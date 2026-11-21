# Feature: Per-App Notification Rules

## What it is
A settings screen where the user labels each installed app with one of three choices:

| Choice | Meaning |
|---|---|
| **Always bait** | Notifications from this app are bait. A quick tap can get the user caught. |
| **Always important** | Notifications from this app are never penalized (bypass). |
| **Let us decide** | The app judges each notification using the user's answers and the strictness setting. |

## Default
**Every app starts as "Let us decide."** The user only changes an app if they want to force it. No app is pre-labeled as bait or important.

## UI
- One row per installed launchable app, with a dropdown (select) holding the three choices.
- Changes save immediately, with no save button.
- The real app list comes from the phone's installed launchable apps, not a hardcoded list.

## How the choice is used
1. **Always important:** the classifier returns `Bypass` and stops.
2. **Always bait:** the notification is treated as bait. The freshness window and the random roll still apply.
3. **Let us decide:** the classifier runs the rules (category, VIP names, keywords, the user's essential and non-essential descriptions). If it is unsure, it returns `NoPenalty` (fail safe).

## Related settings on the same screen
- **Essential:** a text field, "What makes a message essential for you?"
- **Non-essential:** a text field, "What makes a message non-essential for you?"
- **Strictness:** a slider with Relaxed, Balanced, and Strict. Strict marks more messages as bait. Relaxed marks few. Balanced leaves unclear ones alone.
- The two text fields and the slider only apply to apps set to "Let us decide."

## Data shape

```kotlin
enum class AppRule { ALWAYS_BAIT, ALWAYS_IMPORTANT, LET_US_DECIDE }
enum class Strictness { RELAXED, BALANCED, STRICT }

data class RuleSettings(
    val appRules: Map<String, AppRule> = emptyMap(), // package name -> rule
    val essentialCriteria: String = "",
    val nonEssentialCriteria: String = "",
    val strictness: Strictness = Strictness.BALANCED
)

// Any package missing from the map is treated as LET_US_DECIDE
fun RuleSettings.ruleFor(pkg: String) = appRules[pkg] ?: AppRule.LET_US_DECIDE
```

Store only the apps the user changed. A missing app means the default, so newly installed apps work without any migration.

## Acceptance criteria
1. A freshly installed app, and an app the user never touched, behaves as "Let us decide."
2. Setting an app to "Always important" makes its notifications return `Bypass`.
3. Setting an app to "Always bait" makes its notifications go straight to the freshness check and roll.
4. The three choices persist across an app restart.
5. The two text fields and the strictness slider are saved and readable by the classifier.

## Owners
- **Person 3:** the settings screen and storage.
- **Person 2:** reads `RuleSettings` in the classifier.

## Open question
How the "Let us decide" judge uses the two text fields and the slider (rules only, or a Gemini or on-device model for unclear cases) is still undecided. Until it is, "Let us decide" falls back to the category, VIP, and keyword rules, and returns `NoPenalty` when unsure.

## Implementation status
- Done: `AppRule` / `Strictness` and the fields live in `GameConfig` (the team's single saved settings store) instead of a separate `RuleSettings`; saved by `GameStateCodec` v6, which turns old whitelisted apps into "Always important" and old bait apps into "Always bait".
- Done: Settings > Notification rules (`ui/settings/NotificationRulesScreen.kt`) with the installed-app list and dropdowns, the two answers (saved half a second after typing stops), and the strictness slider. Layout follows `notification-settings.html`; visual polish still to come.
- The app list shows only apps allowed to send notifications (Android 13+: `POST_NOTIFICATIONS` granted, which is what the system notification switch controls; older Android can't read this, so every app shows). Apps with notifications off are hidden, even if a choice is saved for them (the choice is kept).
- Tap path: `NotificationClassifier.assess` uses the app's rule. **"Let us decide" currently always returns bait** (rule code `let_us_decide`); the answers and strictness are saved but unused until the judge is built.
- Removed from Settings: the Always-safe apps page, the old per-app rules (channels, app profiles), the VIP card and the important/distracting words page. Their data stays in `GameConfig` and in `NotificationClassifier.assessByRules` for the future judge.

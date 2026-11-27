# Feature: per-app notification rules

## What it is
A settings screen where the user labels each installed app with one of three choices:

| Choice | Meaning |
|---|---|
| **Always bait** | Notifications from this app are classified as bait and an eligible tap is always caught, without an AI check or catch-probability roll. |
| **Always important** | Notifications from this app are never penalized (bypass). |
| **Let us decide** | The AI judge classifies each notification using the user's answers and the strictness setting. |

## Default
**Every app starts as "Let us decide."** The user only changes an app if they want to force it. No app is pre-labeled as bait or important.

## UI
- One entry per installed launchable app, each with a picker for the three choices. The exact layout follows [UI_DESIGN_GUIDE.md](../UI_DESIGN_GUIDE.md).
- Changes save immediately, with no save button.
- The real app list comes from the phone's installed launchable apps, not a hardcoded list. On Android 13+ only apps allowed to post notifications are listed (`rulePicker`).

## How the choice is used
1. **Always important:** the classifier returns Important (`app_always_important`); the tap outcome is `Bypass`.
2. **Always bait:** the notification is treated as bait and an eligible tap is caught without AI or the random catch-probability roll. The freshness window and catch lockout still apply.
3. **Let us decide:** the AI classifies each notification as Important or Bait. Important notifications bypass the catch; Bait notifications use the selected catch probability. If the AI check is unavailable or fails, the result is Unknown and the outcome `NoPenalty` (fail safe). See [how AI classification works](../ai/how-ai-classification-works.md).

## Related settings on the same screen
- **Essential:** a text field, "What makes a message essential for you?"
- **Non-essential:** a text field, "What makes a message non-essential for you?"
- **Strictness:** a slider with Relaxed, Balanced (default) and Strict: the bait-probability cutoff is 0.85, 0.7 or 0.55. Strict marks more notifications as bait, Relaxed fewer.
- The two text fields and the slider only apply to apps set to "Let us decide."

## Data shape

In `logic/GameModels.kt` (`GameConfig`, simplified):

```kotlin
enum class AppRule { AlwaysBait, AlwaysImportant, LetUsDecide }
enum class Strictness { Relaxed, Balanced, Strict }

data class GameConfig(
    val appRules: Map<String, AppRule> = emptyMap(), // package name -> rule, changed apps only
    val essentialCriteria: String = "",
    val nonEssentialCriteria: String = "",
    val strictness: Strictness = Strictness.Balanced,
    // other settings...
) {
    fun ruleFor(pkg: String) = appRules[pkg] ?: AppRule.LetUsDecide
}
```

Store only the apps the user changed. A missing app means the default, so newly installed apps work without any migration.

## Acceptance criteria
1. A freshly installed app, and an app the user never touched, behaves as "Let us decide."
2. Setting an app to "Always important" makes its taps return `Bypass`.
3. Setting an app to "Always bait" makes its notifications skip AI and the probability roll; a fresh eligible tap causes a catch unless the game is paused or the catch lockout is active.
4. The three choices persist across an app restart.
5. The two text fields and the strictness slider are saved and readable by the classifier.

## Where it lives
- Settings screen: `ui/settings/NotificationRulesScreen.kt` (rule pickers in `ui/common/AppRuleRow.kt`, list from `rulePicker`), writing through `GameRepository.setAppRule` / `clearAppRule`, `setCriteria` and `setStrictness`. Onboarding uses the same pickers.
- Storage and classification: `GameConfig.appRules` (`logic/GameModels.kt`), read by `NotificationClassifier.assess`.
- AI judge: `ai/JudgeQueue.kt`, `ai/ClefJudge.kt`, `ai/ClefQuestion.kt`.

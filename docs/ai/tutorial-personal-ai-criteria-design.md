# Tutorial: personalize what the AI calls important

## Purpose

Add an optional tutorial page where a new user can explain what matters to them and what they consider non-essential. Save those answers as the same preferences already used by the AI on **Settings > Notification rules**.

The answers should guide the AI's judgment. They should not create a separate classifier, override an explicit per-app rule, or make the AI treat every user-described item as certain. App rules remain the strongest choice: **Always important**, **Always bait**, or **Let us decide**.

## What already exists

The underlying personalization is implemented:

- `GameConfig.essentialCriteria` and `GameConfig.nonEssentialCriteria` hold the two user answers.
- `GameStateCodec` saves them locally as part of the game configuration.
- `ClefQuestion.criteria(config)` adds them to the two answer descriptions sent to the Clef decision model.
- `JudgePrompt.system(config)` includes them in the chat judge prompt.
- `JudgeQueue` includes both answers in its judging settings. If they change, notifications still being observed are judged again.
- The existing Settings page already lets users edit these answers.

The tutorial change is therefore a new way to collect the same preferences early. It should not create new preference fields or duplicate the AI logic.

## Proposed tutorial flow

Add a new optional step after the introduction and before the safe-app explanation. The setup page for Android notification access remains last.

Suggested order:

1. Welcome / why Taptation exists.
2. **What matters to you?** Enter examples of essential notifications.
3. **What can wait?** Enter examples of non-essential notifications.
4. Explain safe apps and the per-app choices.
5. Explain how catches work.
6. Grant notification access and optionally allow the overlay.

The two questions can share one page with two fields if the page is too long on a small phone. Keep them as two clearly labeled questions, not one combined text box. The user can press **Skip for now** or continue with both fields blank. They can edit answers later in Settings.

Suggested copy:

**Title:** What matters to you?

**Explanation:** These answers help Taptation's AI judge notifications from apps set to “Let us decide.” You can change them later in Settings.

**Field 1 label:** What notifications are important in your life?

**Placeholder:** Family messages, work shifts, delivery updates, appointment changes…

**Field 2 label:** What notifications are usually not important to you?

**Placeholder:** Likes, follows, game rewards, sales, suggested posts…

**Privacy note:** Your answers are saved on this device. When AI judging is configured, Taptation sends these preferences and the notification details to Cloudflare to make a decision. Notification details are not saved by Taptation.

**Actions:** `Back`, `Skip for now`, `Next`.

Use the project's established theme, spacing, text fields and section cards. Keep the page scrollable when the keyboard is open and keep the actions reachable on a small phone. Do not request AI or notification permissions as a condition for entering preferences.

## How the answers affect the AI

The current prompt appends the user's essential examples to the **IMPORTANT** description and non-essential examples to the **BAIT** description. For example, if a user enters “messages from my child’s school” as essential, the AI receives that as part of the IMPORTANT option. If they enter “sports scores” as non-essential, it is part of the BAIT option.

These are personal guidance, not hard-coded rules. The AI still examines the notification content and strictness setting and can return **UNSURE** when evidence is mixed. Do not promise that the model will always classify a match exactly as the user expects.

Only apps with the **Let us decide** rule use these AI preferences. An app set to **Always important** bypasses bait classification; an app set to **Always bait** stays bait. When the AI is unavailable or has no credentials, the current app behavior does not use these answers to classify notifications. The tutorial must not imply that entering answers alone guarantees personalized automatic judging.

## Data and code design

### Reuse the current model and repository

Keep the saved schema as-is:

```kotlin
data class GameConfig(
    val essentialCriteria: String = "",
    val nonEssentialCriteria: String = "",
    // existing settings...
)
```

The onboarding composable should obtain the existing repository, observe the current state, and update these fields through `GameRepository`. Do not write preferences directly from the screen.

Illustrative Compose wiring (adapt to the existing `OnboardingScreen` and repository state APIs):

```kotlin
val repository = rememberGameRepository()
val gameState by repository.state.collectAsStateWithLifecycle()
val config = gameState?.config ?: GameConfig()

CriteriaField(
    label = "What notifications are important in your life?",
    placeholder = "Family messages, work shifts, delivery updates…",
    saved = config.essentialCriteria,
) { value ->
    repository.updateConfig { it.copy(essentialCriteria = value.trim()) }
}

CriteriaField(
    label = "What notifications are usually not important to you?",
    placeholder = "Likes, follows, game rewards, sales…",
    saved = config.nonEssentialCriteria,
) { value ->
    repository.updateConfig { it.copy(nonEssentialCriteria = value.trim()) }
}
```

The current `CriteriaField` is private to `NotificationRulesScreen.kt`. Either extract a small shared criteria-field composable under `ui/common` for both onboarding and Settings, or create an onboarding-specific field that uses the same labels, placeholders, character limit, debounce, and theme. Prefer sharing one component if extraction does not tangle Settings-specific state.

The existing maximum is `GameConfig.MAX_CRITERIA_LENGTH` (1000 characters per field). Enforce it in the UI as well as the model. Trim leading/trailing whitespace before saving; leave ordinary phrases and punctuation intact.

### Save and skip behavior

- Save through `repository.updateConfig { ... }`, which persists through the existing game store.
- Autosave edits (the Settings field already debounces edits) or save before navigating away. Do not keep answers only in `rememberSaveable`, because the AI reads `GameConfig`.
- **Skip for now** leaves empty fields empty and continues. It should not write example placeholder text into the user's preferences.
- If the user navigates Back and returns, show their saved answers.
- If onboarding is reopened after completion, use the existing saved values as the initial content.
- Preserve the existing tutorial completion preference. Do not mark the tutorial complete just because the criteria page was skipped; complete it at the existing final setup action.

### AI request path

No new AI endpoint or model is needed:

```text
Tutorial answers
  -> GameRepository.updateConfig
  -> persisted GameConfig
  -> TaptationApplication observes config
  -> JudgeQueue re-judges active “Let us decide” notifications if criteria changed
  -> ClefQuestion / JudgePrompt includes the answers in future AI decisions
```

The current judge already uses the criteria as prompt context. Keep the existing limits on notification data and do not log notification title/text. The user-facing privacy note should accurately describe that the criteria and notification details are sent to Cloudflare when configured AI judging runs.

## Navigation and page state

`OnboardingScreen` currently uses an integer `page`, displays “Step X of 3”, shows four progress dots because setup is a fourth page, and starts directly at page 3 when opened for setup. Inserting one content page requires updating the page model deliberately:

- Prefer named page constants or an enum rather than continuing to scatter numeric values (`0`, `1`, `2`, `3`).
- Set the displayed step count to the number of tutorial content pages, excluding the permission setup page.
- Keep the setup destination addressable directly (`startAtSetup`) even after inserting the personalization page.
- Update Next/Back labels and progress dots to match the actual flow.
- Preserve the setup-page finish condition: notification access remains required; overlay permission remains optional.

Suggested page model:

```kotlin
private enum class OnboardingPage {
    INTRO, PERSONAL_CRITERIA, SAFE_APPS, CATCHES, SETUP
}
```

The exact copy/order can be tuned with the team, but personalization should come before permissions so users can finish their answers without first navigating Android Settings.

## Testing and demo acceptance

### UI and persistence

1. Clear app data or reset onboarding completion, then open Taptation.
2. Confirm the new personal-criteria page appears before notification-access setup.
3. Enter a phrase in each field, go Next, go Back, and confirm both answers remain.
4. Finish onboarding, open **Settings > Notification rules**, and confirm the two fields contain the exact answers.
5. Edit a value in Settings, restart the app, and confirm it remains saved and appears if onboarding is opened again.
6. Repeat with both fields blank and use **Skip for now**. Confirm onboarding completes and no example text is stored.
7. Check a small emulator, scroll with the keyboard open, large text, and light/dark themes.

### AI wiring

1. With AI credentials configured, use a test notification from an app set to **Let us decide**.
2. Change the personal criteria while the notification remains posted. Confirm `JudgeQueue` requests a new decision using the updated criteria.
3. Confirm **Always important** and **Always bait** app rules still take precedence.
4. Without credentials, confirm the page still saves preferences and Settings remains editable; show the current no-AI explanation so the user understands that the answers cannot personalize automatic decisions in that build.
5. Check logs and persisted game snapshots for accidental notification title/text. User-entered criteria are expected in the game configuration; notification content is not.

### Unit and instrumentation coverage

- Add navigation coverage that the criteria step appears in the correct sequence and can be skipped.
- Add UI coverage verifying both inputs write their corresponding `GameConfig` field, not the other field.
- Existing prompt tests should verify each preference appears only in its matching IMPORTANT or BAIT description and that blank answers add no empty criteria line.
- Add a `JudgeQueue` test that changing either criteria field re-judges an active candidate.
- Keep app-rule precedence and `UNSURE` behavior covered by existing classifier/judge tests; the tutorial must not bypass them.

## Out of scope

- New AI model, endpoint, backend, or database fields.
- Automatically inferring preferences from messages, contacts, or app usage.
- Changing difficulty/strictness or per-app rules during this tutorial step.
- Treating the user's text as instructions to execute. It is preference context for a classification only.
- Claiming private/on-device AI: configured AI requests currently go to Cloudflare.

## Recommendation

Add a short, optional personalization page to onboarding and write answers directly into the existing `GameConfig` criteria fields. This puts the preference setup where new users expect it, avoids duplicate state, and makes the existing AI prompt immediately personalized for future **Let us decide** notifications.

## Implementation status

Implemented in `ui/OnboardingScreen.kt`, `ui/TaptationApp.kt`, and the shared `ui/common/CriteriaField.kt`. The onboarding now runs once on first launch; its new page saves both existing criteria through `GameRepository`, and can be skipped. Settings reuses the same field component. The debug APK builds successfully, and the welcome and criteria pages were inspected on an Android emulator. Automated tests and a full persistence round-trip were not run in this pass.

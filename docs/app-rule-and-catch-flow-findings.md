# App rule and catch-flow findings

## Request being checked

Each app has three rules:

1. **Always important:** protect the app; do not penalize its notifications.
2. **Always bait:** treat every notification from the app as distracting and catch an eligible tap without asking AI or rolling the catch percentage.
3. **Let us decide:** use AI to classify the notification as important or distracting. If it is distracting, use the selected catch percentage to decide whether the tap catches the user.

The third rule is the default. It is the main automatic experience, while the first two let users set direct app-specific behavior.

## What the code did before this change

### App classification

`NotificationClassifier.assess()` already checked the selected app rule first:

```kotlin
when (config.ruleFor(context.packageName)) {
    AppRule.AlwaysImportant -> important("app_always_important")
    AppRule.AlwaysBait -> bait("app_always_bait")
    AppRule.LetUsDecide -> letUsDecide(context, config)
}
```

That meant **Always important** bypassed AI and **Always bait** skipped AI and was classified as Bait. **Let us decide** used the AI verdict. `JudgeQueue.request()` also only queued AI work when the app rule was `LetUsDecide`, so explicit app rules already reduced AI requests.

### Catch outcome

`GameEngine.onTap()` then handled all Bait classifications the same way: unless the game was in its existing catch lockout, it rolled against the selected probability (Easy 20%, Medium 40%, Hard 70%, or a debug override). As a result, an app on **Always bait** was classified as distracting without AI, but a tap could still miss the roll.

That did **not** match the requested meaning of Always bait as an always-catch rule.

## Change made

`GameEngine.onTap()` now recognizes the `app_always_bait` rule and forces the catch instead of rolling the selected probability:

```kotlin
val alwaysBaitRule = next.config.ruleFor(context.packageName) == AppRule.AlwaysBait
// ...
next.isDead || alwaysBaitRule || random() < catchProbability
```

The order of checks still preserves existing game protections:

- Stale notifications are rejected before classification and do not catch.
- A paused game does not catch.
- During the existing catch lockout, repeated taps do not cause another catch.
- Otherwise, **Always bait** causes a catch without an AI request or probability roll.
- **Let us decide** uses the AI result. Important bypasses; Bait uses the selected catch probability; an unavailable/failed AI result has no penalty.
- **Always important** bypasses the catch path.

The notification settings screen now explains that Always bait catches eligible taps without an AI check, and that automatic mode uses AI followed by the catch chance. The page 3 copy options in [onboarding-and-app-copy-options.md](onboarding-and-app-copy-options.md) describe all three rules. The older catch-chance design note now says the percentage is for AI-classified bait; Always bait is the guaranteed-catch exception.

## Tests updated

The unit test `alwaysBaitCatchesWithoutAiOrProbabilityRoll` configures Always bait with a zero catch probability and verifies that:

- the result is Bait with rule `app_always_bait`;
- the tap is caught; and
- the random-number source is not called.

The probability tests now explicitly set an app to `LetUsDecide` with an AI Bait result. That checks that 0%, 100%, and threshold behavior still apply in automatic mode. Existing tests continue to cover safe-app bypass, stale taps, and the catch lockout.

Tests were updated but have not been run as part of this change.

## Relevant code

- `app/src/main/java/com/codejam/taptation/logic/NotificationClassifier.kt` — rule precedence and the Bait/Important/Unknown classification.
- `app/src/main/java/com/codejam/taptation/logic/GameEngine.kt` — stale, paused, lockout, probability, damage, and catch outcome handling.
- `app/src/main/java/com/codejam/taptation/ai/JudgeQueue.kt` — AI work is requested only for `LetUsDecide` apps.
- `app/src/test/java/com/codejam/taptation/logic/GameEngineTest.kt` — forced Always bait catch and automatic-mode probability tests.

## Demo checks

1. Set an app to **Always important** and tap one of its notifications: it should not cause a catch.
2. Set an app to **Always bait**, set the debug catch chance to 0%, and tap a fresh notification outside the lockout: it should still cause a catch. No AI call or random catch roll should be needed.
3. Set an app to **Let us decide**, make sure AI returns Important, and tap: it should bypass the catch chance.
4. Set an app to **Let us decide**, make sure AI returns Bait, and try 0% and 100% catch chances: 0% should miss and 100% should catch.
5. Repeat an Always bait tap during the existing lockout: no second catch should be applied during that lockout.

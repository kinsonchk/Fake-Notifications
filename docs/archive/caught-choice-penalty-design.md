> Historical, kept for reference: the design for the continue penalty. It is implemented, but its "Not now" button, button labels and codec version are out of date. Current rules: [logic/game-rules.md](../logic/game-rules.md).

# Design: Add a Small Extra Penalty for Continuing in the Original App

## Purpose

When a bait notification catches the user, Taptation already applies the normal catch damage and shows two choices:

- **Go to Taptation**
- **Continue in the original app**

This design makes the choice meaningful without making the overlay a forced lockout. The initial catch remains the main consequence. Continuing deliberately adds a smaller extra consequence.

## Current behavior in this project

`GameEngine.onTap()` decides whether a tap is caught and applies the configured catch damage immediately. It stores a `PendingCatch`. The feedback UI then displays the choice. `CatchFeedback.choose()` calls `GameRepository.dismissCatch()` and navigates to the chosen destination; it does not apply more damage.

The current overlay and foreground dialog label the second choice **Go to original app**. For consistency with the proposed consequence, change the user-facing label to **Continue in the original app**. Keep the internal routing behavior the same.

## Recommended rule

1. A catch immediately applies the normal configured damage once. This is the base penalty and is already how the game works.
2. Choosing **Go to Taptation** acknowledges the catch and adds no extra damage.
3. Choosing **Continue in the original app** acknowledges the catch and applies one small extra penalty, for example **5 health points**.
4. Closing the overlay, pressing Back, switching away, or dismissing the fallback notification without choosing does not add the extra penalty. The base catch penalty remains.
5. The catch choice can only be settled once. Repeated taps, activity recreation, service restarts, or delayed callbacks must not apply the extra penalty more than once.
6. If opening the original app fails after the user explicitly chose to continue, keep the user's explicit choice consequence, show a clear message, and offer Go to Taptation. Do not silently apply another penalty on retry.

Example: With a 10-point normal catch penalty, health goes from 100 to 90 as soon as the catch is committed. If the user chooses Taptation, it stays at 90. If the user chooses to continue in the original app, it goes from 90 to 85.

## What happens if the user closes the original app?

Do not add a penalty merely because the user later closes Instagram, YouTube, or another source app. Android does not provide a dependable, privacy-friendly callback that means “the user is done with this app now”; app process death, navigation, screen-off, and switching apps are not equivalent to an intentional close.

The extra penalty is tied only to the explicit **Continue in the original app** button. This makes the rule understandable and testable. Once the user chooses that button, the extra penalty is committed even if they immediately leave the app. Avoid trying to infer intent from foreground-app monitoring or accessibility services.

If product testing finds this too harsh, make the extra amount zero or remove the extra-choice penalty. Do not defer the base damage until a choice: the catch should remain real if the user dismisses feedback, denies overlay permission, or force-closes Taptation.

## Suggested UI copy

Overlay/dialog title: **You got caught**

Message: **Your health dropped by 10. Going back to the distracting app costs 5 more.**

Buttons:

- **Go to Taptation**
- **Continue in the original app · −5 health**

Use the actual values from the committed catch and selected extra penalty; do not hardcode “10” in UI text. Keep the explanatory sentence short. The second button should not imply that Taptation blocks the source app.

Fallback notification text can say: **Your catch is saved. Open Taptation to choose what to do next.** The saved catch already has its base penalty. If the user never opens the notification, no extra choice penalty is added.

## Implementation design

### Keep game rules out of the overlay

The overlay is presentation code. It should report the user's explicit choice to the repository, while the game engine makes and persists any health change. This keeps the behavior testable and prevents Android UI callbacks from writing game state directly.

### Persist the pending choice

A process death can happen while the prompt is visible. Store enough information with `PendingCatch` to settle the choice exactly once. A possible model is:

```kotlin
enum class CatchChoice { GoToTaptation, ContinueOriginalApp }

data class PendingCatch(
    val id: String,
    val packageName: String?,
    val baseDamage: Int,
    val continueDamage: Int = 5,
    val choiceSettled: Boolean = false,
)
```

Prefer an immutable catch record and a separate settled-choice marker/history if the existing storage format makes mutating `PendingCatch` risky. The choice and its health record must be committed atomically through the existing `GameRepository` worker and store.

Expose one repository operation, for example:

```kotlin
fun settleCatchChoice(id: String, choice: CatchChoice): CompletableFuture<ChoiceResult>
```

The engine should:

- verify the pending catch ID matches;
- return `AlreadySettled` for a duplicate request;
- apply `continueDamage` once for `ContinueOriginalApp` (clamped to the health range);
- apply zero extra damage for `GoToTaptation`;
- record the reason distinctly, such as `continue_after_catch`;
- clear/settle the pending choice in the same saved state;
- preserve the existing behavior that selecting a destination never reruns classification or catch probability.

Then `CatchFeedback` calls this API before navigation:

```kotlin
val choice = if (ourApp) CatchChoice.GoToTaptation else CatchChoice.ContinueOriginalApp
repository.settleCatchChoice(pending.id, choice).whenComplete { result, failure ->
    // Navigate only after the choice and any extra health change are saved.
}
```

Use the saved `PendingCatch` as the source of truth for values and IDs. A local `lastChosenId` check is useful for UI debouncing but is not enough to prevent duplicate damage across process death; idempotency belongs in persisted game logic.

### Dismissal and fallback behavior

Closing the visual overlay must not settle the choice as “continue.” It merely removes that presentation. The pending catch remains available so the user can later choose in Taptation. Likewise, swiping away the fallback notification does not mean either choice was made.

Keep the existing destination behavior after a successful choice:

- Go to Taptation opens Home.
- Continue in the original app invokes the original notification action if available, then falls back to launching the source package.
- If no original action/app is available, report that and allow the user to open Taptation. The explicit choice was still recorded once.

## Compatibility and migration

`PendingCatch` is encoded by `GameStateCodec`. Adding required serialized fields needs a versioned codec migration and tests for snapshots from every currently supported version. For old pending catches, assign the new extra penalty a conservative default (recommend 0 for pre-existing saved prompts, so an already-visible prompt does not unexpectedly become harsher after upgrade). Newly created catches can store the current configured value, initially 5.

Do not apply an extra penalty to catches created before this feature or to manual test results unless the product explicitly wants the same rule for them. The base damage remains the existing configured Mild/Medium/Savage damage.

## Tests

### Unit tests for game logic

- Catch applies the existing base damage and creates one pending choice.
- Settling as Go to Taptation adds no damage and clears/settles the pending choice.
- Settling as ContinueOriginalApp adds exactly the configured small extra damage.
- Repeating the same settlement request or submitting a second choice for the same catch never adds damage again.
- A mismatched or missing catch ID changes no state.
- Extra damage clamps at zero health and creates a clear health-history reason.
- A snapshot round-trip preserves the pending catch and its choice-penalty data.
- Old codec versions load with no unexpected extra penalty.

### Android demo

1. Install the current debug APK, open Taptation, and grant Notification access. Allow **Display over other apps** for the overlay test.
2. In Debug, set health to 100, choose a bait source, and set the debug catch chance to 100%.
3. Trigger a real notification tap from the bait source while Taptation is in the background.
4. Confirm the base catch damage appears immediately and the overlay explains both consequences.
5. Repeat from health 100. Tap **Go to Taptation**. Confirm health drops only by the base amount.
6. Reset health and repeat. Tap **Continue in the original app**. Confirm health drops by the base amount plus the small extra amount and the original app opens.
7. Try tapping a choice rapidly twice. Confirm the catch receives no duplicate extra damage.
8. Trigger another catch, close/dismiss the overlay without choosing, then open Taptation. Confirm the base damage remains, no extra damage was added, and the choice is still available.
9. Turn overlay permission off and test the fallback notification. Dismissing that notification must not count as choosing Continue or add extra damage.
10. Repeat using Taptation's own test notification only as a foreground-path check; use a real source-app notification for the background overlay path.

## Implementation status

The first implementation now follows this design:

- A notification catch applies the existing configured base damage immediately.
- New notification catches save a 5-point continuation penalty with the pending choice.
- **Go to Taptation** settles the pending choice with no extra damage.
- **Continue in the original app** applies the saved extra damage once, records it as `continue_after_catch`, then follows the original notification action or package-launch fallback.
- **Not now**, Back, or dismissing the overlay leaves the catch pending and adds no continuation damage. In Taptation, a small reminder remains with a **Choose now** button; the overlay can be shown again when returning from the original app.
- The saved game format is now version 6. Version 5 and earlier pending catches load with a zero continuation penalty so an upgrade does not unexpectedly charge someone for a prompt that was already open.
- The penalty is committed in game logic before navigation. Duplicate settlement requests find no matching pending catch and cannot apply damage again.

The implementation is in `GameEngine`, `GameRepository`, `CatchFeedback`, and `CatchChoiceDialog`; no Android app-closure detection or accessibility monitoring is used.

## Decision summary

Recommended first version: keep the existing immediate catch damage; add a small, clearly disclosed penalty only after an explicit **Continue in the original app** choice; add no choice penalty for Go to Taptation or for closing/dismissing feedback without choosing. Do not detect app closure. Persist and settle the choice exactly once in game logic, and test the Android presentation separately from the unit-tested penalty rule.

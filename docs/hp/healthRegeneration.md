# Health regeneration design

## Goal

Define health recovery over time and a debug control for testing different recovery intervals quickly. Notification dismissals remain statistics-only; they do not add or remove health.

The interval selector and catch-only timer reset in this document are implemented. The debug APK compiles; automated and on-device tests have not been run for this feature.

## Current project behavior

- `GameConfig.recoveryPerHour` still stores the amount (**5** by default); the new `recoveryIntervalMinutes` defaults to **60 minutes**. The compatibility property `recoveryAmount` names the amount clearly in game logic.
- `GameEngine.recovered()` applies complete elapsed hours when game state is refreshed or another game operation runs. It is lazy; it does not need a background timer waking the app every hour.
- A successful catch resets the recovery timer; an uncaught Bait tap preserves it.
- Pausing Taptation stops recovery. Health is capped at 100, and natural recovery does not revive a dead tree.
- `GameRepository.onDismiss()` records notification dismissals in stats and has no health effect.
- `DemoControlsPanel` now offers debug intervals of 1, 5, and 15 minutes, plus the normal 60-minute rate.

Relevant code: `logic/GameModels.kt`, `logic/GameEngine.kt`, `logic/GameRepository.kt`, and `debug/DemoControlsPanel.kt`.

## Recommended behavior

Keep normal recovery at **5 health every 60 clean minutes**. A clean interval is interrupted only when a Bait tap is successfully caught.

- A successful catch resets clean-time progress to zero.
- An uncaught Bait tap preserves progress.
- Important, whitelisted, and Unknown taps preserve progress.
- A notification swipe, clear-all, or Android timeout has no health effect and does not reset clean-time progress. Dismissal stats continue to work as they do today.
- Inactive time does not count. Health at zero stays at zero until the existing reset action.
- Apply complete intervals when game state is next refreshed or changed. Do not add a periodic background worker just to update health.
- Cap health at 100 and advance the recovery anchor even while health is full, so time at full health cannot accumulate into a hidden bonus.

Example: if a user is 45 minutes into a 60-minute interval and taps a Bait notification, then:

- If the catch roll succeeds, the timer resets to zero and another 60 clean minutes are needed for +5.
- If the catch roll misses, the 45 minutes remain, so only 15 more clean minutes are needed for +5.

## Debug interval control

Add a debug-only **Health recovery interval** control to **Debug > Demo controls**. Keep the recovery amount at 5 health and let the debug build choose how often to grant it:

- **1 minute** — quick demo
- **5 minutes** — short test
- **15 minutes** — medium test
- **60 minutes (normal)** — production behavior

Show the active choice, such as `Recovery: +5 every 5 clean minutes`. Add **Use normal rate** to return to 60 minutes. Keep the existing **Fast-forward 1 clean hour** control; it verifies the normal production interval through the same engine logic.

The override must be available only in debuggable builds. Do not show it in regular Settings or let a debug override change release behavior.

## Suggested implementation

### 1. Store an amount and interval

The existing `recoveryPerHour` remains in its saved/API slot for compatibility and acts as the amount per interval. `recoveryAmount` is a clearer read-only alias. Add the normal interval and debug override:

```kotlin
data class GameConfig(
    val recoveryAmount: Int = 5,
    val recoveryIntervalMinutes: Int = 60,
    val debugRecoveryIntervalMinutes: Int? = null,
    // existing fields...
)

fun GameConfig.effectiveRecoveryIntervalMinutes(debugBuild: Boolean): Int =
    if (debugBuild) debugRecoveryIntervalMinutes ?: recoveryIntervalMinutes
    else recoveryIntervalMinutes
```

Validate the amount in `0..100` and intervals in a safe range such as `1..1440` minutes. The debug UI can offer only the four choices above; domain validation protects other callers.

### 2. Calculate completed intervals

Replace the fixed-hour calculation in `GameEngine.recovered()` with interval math:

```kotlin
val intervalMillis = config.effectiveRecoveryIntervalMinutes(allowDebugOverrides) * 60_000L
val intervals = (now - source.recoveryAnchorMillis).coerceAtLeast(0) / intervalMillis
val gained = intervals * config.recoveryAmount
val health = (source.health.toLong() + gained).coerceAtMost(100).toInt()
val nextAnchor = source.recoveryAnchorMillis + intervals * intervalMillis
```

Keep disabled/dead checks. When the normal amount or interval changes, settle elapsed time using the old rate first, then start the new interval at `now`. When the debug interval changes, also settle using the old effective interval first and set the anchor to `now`; this prevents a faster demo rate applying retroactively to earlier time.

### 3. Reset progress only on a successful catch

In `GameEngine.onTap()`, the current code sets `recoveryAnchorMillis = now` before the catch roll for any Bait tap. Move that reset into the branch where the random roll succeeds and a catch is created. Do not move it into the generic Bait branch. A missed roll must preserve the previous anchor. `caught()` already records the catch and applies damage; place the anchor update in that same successful-catch state change.

Do not alter `GameRepository.onDismiss()` to award health. It should continue recording dismissal stats only. User swipe, clear-all, and Android timeout do not grant or remove health.

### 4. Debug API and UI

Extend `DemoControlState` with the effective recovery interval and extend `DemoControls` with a method such as:

```kotlin
fun setRecoveryIntervalForDebug(minutes: Int?)
```

`RepositoryDemoControls` should call a debug-only `GameRepository` method. That method must verify `ApplicationInfo.FLAG_DEBUGGABLE` and update only the debug override. Add a dropdown or segmented control in `DemoControlsPanel`; show the selected interval beside health.

Keep the existing architecture: Compose calls `DemoControls`; the repository serializes the update; the game engine and store remain the source of truth. Do not edit preferences directly from the UI.

### 5. Save compatibility

`GameStateCodec` is currently version **6**. If the model changes to an amount plus interval and debug override, write version **7**.

- Decode v1-v6 without losing health, timestamps, history, or settings.
- Keep old `recoveryPerHour` as the recovery amount; v1-v6 snapshots default to `recoveryIntervalMinutes = 60` and no debug override.
- Encode the new values in v7 and validate them on decode.
- Verify old snapshots with a partial hour retain the same partial progress at the normal 60-minute interval.

If schema simplicity is preferred, keep the persisted `recoveryPerHour` as the normal amount and append interval/debug fields. Old saves must still mean 5 health per 60 minutes.

## Test plan

### Unit tests

- Default recovery grants +5 after 60 clean minutes and no extra health immediately before the interval completes.
- A 1-, 5-, or 15-minute debug interval grants +5 per completed interval; release-mode engine ignores the debug override.
- Changing interval does not apply the new interval to time that elapsed under the old one.
- An uncaught Bait tap preserves elapsed clean time; a successful catch resets it to zero.
- Important/whitelisted/Unknown taps and notification dismissals do not reset the timer.
- Multiple complete intervals catch up correctly; health caps at 100 and does not revive from zero.
- Inactive time does not regenerate health.
- User swipe, clear-all, and Android timeout continue to affect stats as before, but none changes health.
- Codec v1-v6 migration and v7 round-trip preserve the recovery anchor and rate.

### Manual emulator/phone demo

1. Open **Debug > Demo controls** and set health to 50.
2. Select **1 minute** recovery. Wait one clean minute, then open Home or perform a game-state refresh. Health should become 55; no background timer is required for the update.
3. Set health to 50 and select **60 minutes**. Use **Fast-forward 1 clean hour**. Health should become 55.
4. Set health to 50 and fast-forward 45 minutes. Trigger a Bait tap with the debug catch chance set to 0%. The timer should keep its 45 minutes; after 15 more clean minutes, health should increase by 5.
5. Set health to 50 and fast-forward 45 minutes again. Set the debug catch chance to 100% and trigger a Bait tap. The successful catch should reset the timer. Fast-forwarding 15 more minutes should not regenerate health; completing a new clean interval should.
6. Swipe a notification, clear all notifications, and let a test notification time out. Confirm these removals affect stats only and never change health.
7. Disable Taptation, wait, and re-enable it. Confirm disabled time did not regenerate health.
8. Confirm the interval control is absent in a release build and release behavior stays at the normal rate.

## Files likely to change

- `logic/GameModels.kt`: recovery amount alias, normal interval, and debug interval fields.
- `logic/GameEngine.kt`: interval-based recovery and catch-only recovery-anchor reset.
- `logic/GameRepository.kt`: debug-only interval update method.
- `debug/DemoControls.kt`, `debug/RepositoryDemoControls.kt`, and `debug/DemoControlsPanel.kt`: interval selection and display.
- `logic/GameStateCodec.kt`: v7 backward-compatible migration.
- `GameEngineTest.kt`, codec tests, and debug-panel tests: automated coverage.

## Implementation status

Implemented in `GameEngine`, `GameRepository`, `GameStateCodec` v7, and the Debug demo controls. Notification dismissals remain stats-only. `assembleDebug` succeeds; automated tests and device behavior have not been checked in this implementation pass.

## Recommendation

Keep normal recovery at **5 health per 60 clean minutes**. Reset the clean-time timer only when a Bait tap actually results in a catch; preserve progress after a missed roll. Add a debug-only interval selector with 1/5/15/60-minute choices. Keep all notification dismissals stats-only, including user swipes, clear-all, and Android timeouts.

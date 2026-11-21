# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Fake Notifications** (Gradle project `Taptation`, package `com.codejam.taptation`): 1-day hackathon Android app, team of 4. App watches other apps' notifications; tapping a "bait" notification (social, games) has a small random chance (5-10%) to "catch" the user, lowering a health state (tree/pet) and showing a short lockout. Urgent apps (family, work, rides, banking) are whitelisted and never penalized.

Planning docs (read before working on a teammate's area):
- `fake-notifications-features-by-person.md`: core vs priority features per person, shared interfaces, go/no-go checkpoint (tap detection on a real phone by hour 4), cut list.
- `person4_testing.txt`: layered test plan, debug-menu wishlist, end-to-end demo checklist, edge cases.
- `docs/PERSON_2_INTEGRATION.md`: exact game rules, defaults, Person 1 contract, Person 3 API.

Kotlin + Jetpack Compose, single `:app` module, version catalog in `gradle/libs.versions.toml`, minSdk 26, compile/target SDK 37, AGP 9.x.

## Commands

On Windows use `.\gradlew.bat` in PowerShell, or `./gradlew` in Git Bash.

```bash
./gradlew assembleDebug                 # build
./gradlew installDebug                  # build + install on connected phone
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew testDebugUnitTest --tests "com.codejam.taptation.service.TapDetectionTest"   # single test class
./gradlew connectedDebugAndroidTest     # instrumented tests (needs device)
adb logcat -s FakeNotifs                # all app log lines use tag FakeNotifs
adb shell cmd notification allow_listener com.codejam.taptation/com.codejam.taptation.service.TapListenerService   # grant notification access without UI
```

Real verification needs a phone: see README "Verify" steps (post test notification, tap vs swipe, simulate tap).

## Architecture

Tap pipeline:

```
system -> TapListenerService.onNotificationPosted   (cache NotificationContext by key, 500-entry in-memory LRU)
       -> TapListenerService.onNotificationRemoved(sbn, rankingMap, reason)
       -> TapDetection.shouldDispatch(...)          (pure filter)
       -> TapLog.record(pkg)                        (in-memory last 20, StateFlow, debug screen)
       -> TapDispatcher.dispatch(NotificationContext) -> TapDispatcher.handler.onTap(context)
       -> GameRepository (installed by TaptationApplication.onCreate)
       -> single-thread worker -> GameEngine.onTap -> PreferencesGameStore
```

- `logic/TapHandler.kt`: the **shared contract**. `TapHandler.onTap(packageName)` plus `onTap(context: NotificationContext)` (defaults to the package-only one); `TapDispatcher` holds a `@Volatile` handler with both `dispatch` overloads. Package-only calls stay supported.
- `logic/GameModels.kt`: `NotificationContext` (packageName, key, title, text, notification category, appCategory), `GameConfig`, `GameState`, `TapOutcome` (`Bypass` / `NoPenalty` / `Caught`), `CatchLevel`.
- `logic/GameEngine.kt`: pure, single-thread-confined rules (classification, roll, damage, recovery, lockout, death/reset). Clock and random are injected, so it's JVM unit-testable.
- `logic/GameRepository.kt`: the real `TapHandler`. Serializes every operation on one worker thread (no disk I/O on listener callbacks), resolves `ApplicationInfo.category`, exposes `state` / `error` / `lastResult` StateFlows and `CompletableFuture` mutations. Never `.get()`/`.join()` on the UI thread.
- `logic/PreferencesGameStore.kt`, `logic/GameStateCodec.kt`: versioned, atomic local snapshots.
- `TaptationApplication.kt`: creates the single `GameRepository` and sets `TapDispatcher.handler`, so it works even when only the service process starts. UI gets it via `(context.applicationContext as TaptationApplication).game`.
- `service/TapDetection.kt`: pure filter logic, framework constants only, so it runs in plain JVM tests. Keep new decision logic here, not in the service.
- `service/PermissionHelper.kt`: notification-access check/settings intents and battery-optimization helpers; reuse for onboarding UI.
- `debug/`: `DebugScreen` + `LogicDebugPanel` (what `MainActivity` shows now), `TestNotification` posts an auto-cancel notification with a contentIntent so tapping it yields `REASON_CLICK`.

Full rules, defaults and the Person 3 API are in `docs/PERSON_2_INTEGRATION.md`. Key gotchas from it: bait rules and whitelist start **empty**, so nothing is penalized until a bait package/keyword is configured; `Caught` already applied damage, so UI must not call `applyCatch` again; notification title/text are transient and must never be persisted or logged.

## Shared interfaces (agreed between teammates)

- `onTap(context: NotificationContext)` (and legacy `onTap(packageName)`): Person 1 -> Person 2.
- `GameRepository` (`state`, `getHealth()`, `isWhitelisted`, `setWhitelisted`, `updateConfig`, `applyCatch`, `reset`, `refresh`): Person 2 -> Person 3.
- Storage owned by Person 2, read by Person 3 through `GameRepository.state`. Person 3's settings screen writes probability/level via `updateConfig`.

## Tap detection rules (don't regress)

- Count a removal as a tap **only** when `reason == REASON_CLICK`. Swipe (`REASON_CANCEL`), clear-all, app-cancel, group-summary-cancel and every other reason are ignored.
- Skip notifications with `FLAG_GROUP_SUMMARY` or `FLAG_ONGOING_EVENT`.
- Own package counts only in debuggable builds (so the in-app test notification works); ignored in release.
- Override only the **three-argument** `onNotificationRemoved`. The base three-arg version forwards to the one-arg one, so overriding both double-handles.
- Listener callbacks run on the main thread: keep them to filter + log + dispatch. `TapHandler` implementations must move disk/network work off-thread (`GameRepository` does this with its worker).

## Platform gotchas

- `REASON_CLICK` fires only for notifications that auto-cancel on tap; ones that stay after tapping produce no event.
- `adb shell cmd notification post` notifications have no contentIntent, so they can't test taps (`person4_testing.txt` suggests them; fine for testing posting/logic, not tap detection). Use the in-app test notification, a real app, or a helper app with real contentIntents.
- Notification access can't be granted from code (settings toggle or the adb command above).
- Android 13+ sideloaded APKs: access toggle may be greyed out until App info > ⋮ > Allow restricted settings.
- Battery optimization can kill the listener (non-Pixel especially): app should be set to Unrestricted.

## Team ownership

Each person works on their own branch and merges into `main` via PR.

| Area | Owner |
|---|---|
| `service/`, `debug/DebugScreen.kt`, `debug/TestNotification.kt` | Person 1 (notification service) |
| `logic/`, `TaptationApplication.kt`, `debug/LogicDebugPanel.kt`: whitelist, classification, roll, health, storage | Person 2 |
| `ui/`: onboarding, tree/pet visuals, lockout, stats, real navigation in `MainActivity` | Person 3 |
| `debug/` extensions, adb scripts, demo | Person 4 |

Don't edit another person's area; leave a `// TODO(Person X)` instead. Out of scope for the service: cancelling/snoozing other apps' notifications. Don't add dependencies beyond the standard Compose/AndroidX set unless essential.

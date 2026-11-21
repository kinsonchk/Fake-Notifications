# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Fake Notifications** (Gradle project `Taptation`, package `com.codejam.taptation`): 1-day hackathon Android app, team of 4. App watches other apps' notifications; tapping a "bait" notification (social, games) has a small random chance (5-10%) to "catch" the user, lowering a health state (tree/pet) and showing a short lockout. Urgent apps (family, work, rides, banking) are whitelisted and never penalized.

Planning docs live in `docs/`, one folder per person (`docs/person-1` … `docs/person-4`), each with a `README.md` listing that person's docs, status and remaining work. Read the relevant one before working on a teammate's area:
- `docs/features-by-person.md`: core vs priority features per person, shared interfaces, go/no-go checkpoint (tap detection on a real phone by hour 4), cut list.
- `docs/classification-and-settings-brief.md`: classification rules (Section A, Person 2), settings page (Section B, Person 3), open questions (Section C). Conflicts with the current code: see "Brief vs current code" below.
- `docs/person-1/notification-service-brief.md`: Person 1 brief and acceptance criteria.
- `docs/person-2/PERSON_2_INTEGRATION.md`: exact game rules, defaults, Person 1 contract, Person 3 API.
- `docs/person-4/testing-plan.txt`: layered test plan, debug-menu wishlist, end-to-end demo checklist, edge cases.

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
- `logic/NotificationContext.kt`: `NotificationContext` (packageName, key, title, text, notification category, appCategory, isConversation, channelName, ageMillis, action button titles). Transient: never persisted or logged.
- `logic/GameModels.kt`: `GameConfig`, `GameState`, `TapOutcome` (`Bypass` / `NoPenalty` / `Caught`), `CatchLevel`.
- `logic/GameEngine.kt`: pure, single-thread-confined rules (classification, roll, damage, recovery, lockout, death/reset). Clock and random are injected, so it's JVM unit-testable.
- `logic/GameRepository.kt`: the real `TapHandler`. Serializes every operation on one worker thread (no disk I/O on listener callbacks), resolves `ApplicationInfo.category`, exposes `state` / `error` / `lastResult` StateFlows and `CompletableFuture` mutations. Never `.get()`/`.join()` on the UI thread.
- `logic/PreferencesGameStore.kt`, `logic/GameStateCodec.kt`: versioned, atomic local snapshots.
- `TaptationApplication.kt`: creates the single `GameRepository` and sets `TapDispatcher.handler`, so it works even when only the service process starts. UI gets it via `(context.applicationContext as TaptationApplication).game`.
- `service/TapDetection.kt`: pure filter logic, framework constants only, so it runs in plain JVM tests. Keep new decision logic here, not in the service.
- `service/NotificationSignals.kt`: builds the metadata in the `posted`/`removed` Logcat lines (category, channel, importance, conversation flags, auto-cancel, postTime/age). Logging only; never title/text.
- `service/PermissionHelper.kt`: notification-access check/settings intents and battery-optimization helpers; reuse for onboarding UI.
- `debug/`: `DebugScreen` + `LogicDebugPanel` (what `MainActivity` shows now), `TestNotification` posts an auto-cancel notification with a contentIntent so tapping it yields `REASON_CLICK`.

Full rules, defaults and the Person 3 API are in `docs/person-2/PERSON_2_INTEGRATION.md`. Key gotchas from it: bait rules and whitelist start **empty**, so nothing is penalized until a bait package/keyword is configured; `Caught` already applied damage, so UI must not call `applyCatch` again; notification title/text are transient and must never be persisted or logged.

## Shared interfaces (agreed between teammates)

- `onTap(context: NotificationContext)` (and legacy `onTap(packageName)`): Person 1 -> Person 2.
- `GameRepository` (`state`, `getHealth()`, `isWhitelisted`, `setWhitelisted`, `updateConfig`, `applyCatch`, `reset`, `refresh`): Person 2 -> Person 3.
- Storage owned by Person 2, read by Person 3 through `GameRepository.state`. Person 3's settings screen writes probability/level via `updateConfig`.

## Brief vs current code

`docs/classification-and-settings-brief.md` was written after the first logic version and disagrees with it. Until the team decides, the code and `PERSON_2_INTEGRATION.md` are the source of truth; don't silently change one to match the other.

| Topic | Brief | Code on `main` |
|---|---|---|
| Catch probability default | 10% | 7.5% |
| Damage Mild/Medium/Savage | 5 / 10 / 20 | 10 / 20 / 35 |
| Default level | Medium | Mild |
| Bypass rules | whitelist, call/alarm/reminder/event, VIP title, urgent keyword | same, plus important channels and Discord DMs/@mentions (`NotificationClassifier`) |
| Urgent keywords default | urgent, emergency, code, verification | urgent, code, meeting invite, meeting invitation |
| Unmatched notification | bait (A3 rule 6) | bait only for promo/social, msg/email, configured bait, or a configured app profile; anything else `Unknown` -> `NoPenalty`, no roll |
| Missing title or text | not covered | `Unknown` -> `NoPenalty` (can't rule out VIP/urgent) |
| Freshness window | 10 min, no roll if older | not implemented: `ageMillis` is only recorded in `TapRecord` (negative age -> `Unknown`) |
| Health at 0 | patch resets | stays dead until UI calls `reset()` |
| Tap interface | `onTap(packageName, ageMillis)` | `onTap(NotificationContext)` with `ageMillis` set by the service; `ResultTapHandler.onTap(packageName, ageMillis)` also exists |
| Settings storage | new `SettingsRepository` + DataStore | `GameConfig` in `GameRepository` (SharedPreferences), incl. `vipNames`, `importantKeywords`, channels, app profiles |
| compile/target SDK | 36 | 37 |

Brief C2 asks Person 1 to "log" titles: show them on the debug screen from memory only; the never-log/never-persist rule for title/text still applies.

## Tap detection rules (don't regress)

- Count a removal as a tap **only** when `reason == REASON_CLICK`. Swipe (`REASON_CANCEL`), clear-all, app-cancel, group-summary-cancel and every other reason are ignored.
- Skip notifications with `FLAG_GROUP_SUMMARY` or `FLAG_ONGOING_EVENT`.
- Own package counts only in debuggable builds **and only on the test channel** (`TapDetection.OWN_TEST_CHANNEL_ID`, used by the in-app test notification); ignored in release. Any other notification we post (e.g. a "you got caught" alert) must never count, or tapping it would trigger another catch in a loop.
- Override only the **three-argument** `onNotificationRemoved`. The base three-arg version forwards to the one-arg one, so overriding both double-handles.
- Listener callbacks run on the main thread: keep them to filter + log + dispatch. `TapHandler` implementations must move disk/network work off-thread (`GameRepository` does this with its worker).

## Platform gotchas

- `REASON_CLICK` fires only for notifications that auto-cancel on tap; ones that stay after tapping produce no event.
- `adb shell cmd notification post` notifications have no contentIntent, so they can't test taps (`docs/person-4/testing-plan.txt` suggests them; fine for testing posting/logic, not tap detection). Use the in-app test notification, a real app, or a helper app with real contentIntents.
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

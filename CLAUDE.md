# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Fake Notifications** (Gradle project `Taptation`, package `com.codejam.taptation`): 1-day hackathon Android app, team of 4. App watches other apps' notifications; tapping a "bait" notification (social, games) has a small random chance (5-10%) to "catch" the user, lowering a health state (tree/pet) and showing a short lockout. Urgent apps (family, work, rides, banking) are whitelisted and never penalized.

Planning docs (read before working on a teammate's area):
- `fake-notifications-features-by-person.md`: core vs priority features per person, shared interfaces, go/no-go checkpoint (tap detection on a real phone by hour 4), cut list.
- `person4_testing.txt`: layered test plan, debug-menu wishlist, end-to-end demo checklist, edge cases.

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

Tap pipeline (one direction, one contract):

```
system -> TapListenerService.onNotificationRemoved(sbn, rankingMap, reason)
       -> TapDetection.shouldDispatch(...)   (pure filter)
       -> toTappedNotification(sbn)          (copy into plain-Kotlin TappedNotification)
       -> TapLog.record(tapped)              (in-memory last 20, StateFlow)
       -> TapDispatcher.dispatch(tapped.packageName) -> TapDispatcher.handler.onTap(pkg)
          (TODO(Person 2): switch to passing the whole TappedNotification)
```

- `logic/TapHandler.kt`: the **shared contract** between teammates. `TapHandler.onTap(packageName)`, `TapDispatcher` (holds a `@Volatile` handler), `LoggingTapHandler` stub. Real consequence logic plugs in by setting `TapDispatcher.handler = ...` at app start; the service must never change for that. Don't change the interface signature without the team.
- `service/TapListenerService.kt`: `NotificationListenerService`, bound by the system once the user grants Notification access (no foreground service). Exposes `connected: StateFlow<Boolean>`; requests rebind on disconnect unless access was revoked. Handler exceptions are caught so they can't kill the listener.
- `service/TappedNotification.kt`: plain-Kotlin copy of a tapped notification (package, title, text, category, channelId, key, post/tap times), built by the service from the `StatusBarNotification`. Plain types so handlers unit-test on the JVM.
- `service/TapDetection.kt`: pure logic, no framework calls beyond constants, so it runs in plain JVM tests. Keep new decision logic here, not in the service.
- `service/PermissionHelper.kt`: notification-access check/settings intents and battery-optimization helpers; meant for reuse by onboarding UI.
- `debug/`: Compose debug screen (currently what `MainActivity` shows) and `TestNotification` that posts an auto-cancel notification with a contentIntent so tapping it yields `REASON_CLICK`.

## Shared interfaces (agreed between teammates)

- `onTap(packageName)`: Person 1 -> Person 2 (implemented as `TapHandler`). Planned: Person 2 changes it to `onTap(notification: TappedNotification)` and updates the two `TODO(Person 2)` call sites in `TapListenerService` and `DebugScreen`.
- `applyCatch(level)`, `getHealth()`: Person 2 -> Person 3.
- `isWhitelisted(packageName)`: Person 2 and Person 3.
- Storage owned by Person 2, read by Person 3. Settings screen (Person 3) feeds probability and level to Person 2.

Note: the features doc says Person 2's `onTap` handler returns `Bypass` / `NoPenalty` / `Caught`, but the current `TapHandler.onTap` returns `Unit`. Never log notification title/text: it can contain other apps' private messages. Agree with the team before changing the signature.

## Tap detection rules (don't regress)

- Count a removal as a tap **only** when `reason == REASON_CLICK`. Swipe (`REASON_CANCEL`), clear-all, app-cancel, group-summary-cancel and every other reason are ignored.
- Skip notifications with `FLAG_GROUP_SUMMARY` or `FLAG_ONGOING_EVENT`.
- Own package counts only in debuggable builds (so the in-app test notification works); ignored in release.
- Override only the **three-argument** `onNotificationRemoved`. The base three-arg version forwards to the one-arg one, so overriding both double-handles.
- Listener callbacks run on the main thread: keep them to filter + log + dispatch. `TapHandler` implementations must move disk/network work off-thread.

## Platform gotchas

- `REASON_CLICK` fires only for notifications that auto-cancel on tap; ones that stay after tapping produce no event.
- `adb shell cmd notification post` notifications have no contentIntent, so they can't test taps (`person4_testing.txt` suggests them; fine for testing posting/logic, not tap detection). Use the in-app test notification, a real app, or a helper app with real contentIntents.
- Notification access can't be granted from code (settings toggle or the adb command above).
- Android 13+ sideloaded APKs: access toggle may be greyed out until App info > ⋮ > Allow restricted settings.
- Battery optimization can kill the listener (non-Pixel especially): app should be set to Unrestricted.

## Team ownership

Work for the notification service happens on branch `person1/notification-service`.

| Area | Owner |
|---|---|
| `service/`, `debug/TestNotification.kt`, initial `logic/TapHandler.kt` stub | Person 1 (notification service) |
| `logic/`, `data/`: whitelist, bait classification, random roll, health, storage | Person 2 |
| `ui/`: onboarding, tree/pet visuals, lockout, stats, real navigation in `MainActivity` | Person 3 |
| `debug/` extensions, adb scripts, demo | Person 4 |

Don't edit another person's area; leave a `// TODO(Person X)` instead. Out of scope for the service: cancelling/snoozing other apps' notifications. Don't add dependencies beyond the standard Compose/AndroidX set unless essential.

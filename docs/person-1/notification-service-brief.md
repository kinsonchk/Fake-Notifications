# Claude Code Brief: Person 1, Notification Service (Fake Notifications)

> Paste this whole file into Claude Code as the first message (or save it as `CLAUDE.md` in the repo root). Replace the placeholders in **Section 2** before starting.

---

## 1. Project context

We are building **Fake Notifications**, a 1-day hackathon Android app (team of 4).

**Concept:** The app watches notifications from other apps. When the user taps a "bait" notification (social media, games, etc.), there is a small random chance (5-10%) they get "caught," which lowers a visual health state (a tree or pet) and shows a short lockout screen. Urgent apps (family, work, rides, banking) are whitelisted and never penalized.

**Pitch:** "Disabling notifications avoids the problem. We train you to beat it."

**Team split:**
- **Person 1 (me): Notification service.** This brief.
- **Person 2:** Urgency/consequence logic and local storage.
- **Person 3:** UI and stats.
- **Person 4:** Testing and demo.

**My job:** Build the `NotificationListenerService`, the permission flow, and tap detection, then hand each tap to Person 2 through one agreed interface. Everything else is someone else's job.

---

## 2. Placeholders (fill in before starting)

- **Package name / applicationId:** `com.yourteam.fakenotifs` (replace with the real one)
- **Does the Android Studio project already exist in this repo?** `yes / no`
  - If **yes**: do NOT recreate the project or touch Gradle files beyond what is strictly needed. Add files only.
  - If **no**: scaffold a minimal project as described in Section 5.
- **Demo phone:** Pixel, Android version: `___`
- **minSdk:** 26 (target and compile SDK: latest stable, e.g. 34 or 35)

---

## 3. Scope: what Claude Code should build

### In scope (must deliver)
1. **`TapListenerService`**, a `NotificationListenerService` subclass.
2. **Manifest declarations** for the service.
3. **Permission flow:** check whether notification access is granted, and send the user to the system settings screen to grant it.
4. **Tap detection** using `REASON_CLICK` in `onNotificationRemoved`, with swipe-dismiss and every other removal reason ignored.
5. **Hand-off interface** so Person 2 can plug in later: `onTap(packageName)`.
6. **Minimal debug screen (Compose)** to prove everything works on a real phone.
7. **Resilience basics:** rebind on disconnect, battery optimization guidance, graceful behavior if permission is revoked.
8. **A small unit test** for the "is this removal reason a tap?" function.
9. **A short README section** for teammates on how to run and verify it.

### Out of scope (do NOT build)
- Whitelist, bait-vs-urgent classification, random roll, health, consequences, storage (Person 2)
- Onboarding UI, tree/pet visuals, lockout screen, stats screen (Person 3)
- adb scripts, helper notification app, demo script (Person 4)
- Cancelling or snoozing other apps' notifications
- Friend ping, payments, iOS

Do not edit files in other people's folders (see Section 5). If something outside my scope is needed, add a clearly marked `// TODO(Person X)` and move on.

---

## 4. Technical requirements and design decisions

### 4.1 Manifest
- Declare the service with:
  - `android:name=".service.TapListenerService"`
  - `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`
  - `android:exported="true"`
  - An intent filter with action `android.service.notification.NotificationListenerService`
  - A label users will recognize in the "Notification access" list
- Add `POST_NOTIFICATIONS` permission (needed on Android 13+ only for the debug "post test notification" button).
- No foreground service is needed. The system binds a listener service on its own once access is granted.

### 4.2 Tap detection logic
Override the **three-argument** version:

```kotlin
override fun onNotificationRemoved(
    sbn: StatusBarNotification,
    rankingMap: RankingMap,
    reason: Int
)
```

Rules:
- Treat a removal as a tap **only** if `reason == NotificationListenerService.REASON_CLICK`.
- Ignore swipe-dismiss (`REASON_CANCEL`), clear-all (`REASON_CANCEL_ALL`), app-cancelled (`REASON_APP_CANCEL`), group summary removals (`REASON_GROUP_SUMMARY_CANCELED`), and every other reason.
- Ignore notifications where `sbn.notification.flags` includes `FLAG_GROUP_SUMMARY`.
- Ignore ongoing notifications (`FLAG_ONGOING_EVENT`), since users can't tap-dismiss these meaningfully.
- Ignore our own package in release builds. **In debug builds, do not ignore it**, so the in-app test notification can be used to prove detection.
- Extract the pure decision into a small, testable function, for example `fun isTap(reason: Int): Boolean`, and keep Android framework calls out of it.
- Log every callback (package, reason, key) with the tag `FakeNotifs` so Logcat shows exactly what happened.

### 4.3 Hand-off interface to Person 2
The agreed contract is `onTap(packageName)`. Implement it like this:

```kotlin
// logic/TapHandler.kt (stub owned by Person 1, replaced by Person 2)
interface TapHandler {
    fun onTap(packageName: String)
}

object TapDispatcher {
    @Volatile var handler: TapHandler = LoggingTapHandler()
    fun dispatch(packageName: String) = handler.onTap(packageName)
}

class LoggingTapHandler : TapHandler {
    override fun onTap(packageName: String) {
        Log.d("FakeNotifs", "TAP (stub handler): $packageName")
    }
}
```

- The service calls `TapDispatcher.dispatch(packageName)` and nothing else.
- Person 2 will replace `LoggingTapHandler` with the real implementation (or set `TapDispatcher.handler`) without touching the service.
- Also keep an in-memory ring buffer of the last 20 taps (package name and timestamp) so the debug screen can display them.

### 4.4 Permission flow
- `fun isNotificationAccessGranted(context: Context): Boolean`, using `NotificationManagerCompat.getEnabledListenerPackages(context)` and checking it contains `context.packageName`.
- `fun openNotificationAccessSettings(context: Context)`, launching `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- Re-check the permission state in the debug screen on every resume (lifecycle `ON_RESUME`), because the user returns from system settings.
- Expose these helpers in a small `PermissionHelper.kt` so Person 3's onboarding screen can reuse them.

### 4.5 Resilience
- In `onListenerDisconnected()`, call `NotificationListenerService.requestRebind(ComponentName(this, TapListenerService::class.java))`.
- Log `onListenerConnected()` and `onListenerDisconnected()`, and expose a "service connected: yes/no" flag (a simple `@Volatile` boolean or StateFlow) for the debug screen.
- Battery guidance: provide a helper that opens `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, and a debug-screen note telling the tester to set the app to **Unrestricted** under App battery usage.
- Do not crash if the permission is revoked mid-session. The service just stops receiving events.

### 4.6 Debug screen (Jetpack Compose, single screen)
Show:
1. **Notification access:** granted / not granted, with a button to open settings.
2. **Service connected:** yes / no.
3. **Last 20 taps:** package name and time.
4. **Button: "Post test notification."** Posts a notification from our own app with `setAutoCancel(true)` and a real `contentIntent` (e.g. opens the app), so tapping it fires `REASON_CLICK`. Create a notification channel first. On Android 13+, request `POST_NOTIFICATIONS` at runtime before posting.
5. **Button: "Simulate tap."** Calls `TapDispatcher.dispatch("com.test.fake")` directly, to test the hand-off without a real notification.
6. **Button: "Open battery settings."**

Keep it plain and functional. Person 3 owns the real UI.

---

## 5. Repo and file layout

If the project already exists, add only the files below. If it does not, scaffold a minimal Kotlin + Jetpack Compose project (Gradle Kotlin DSL, version catalog, Compose BOM, `minSdk 26`) with an `.gitignore` that excludes `build/`, `.gradle/`, `local.properties`, `.idea/`, and `*.keystore`.

```
app/src/main/
  AndroidManifest.xml                         (add service + permissions)
  java/<package path>/
    service/
      TapListenerService.kt                   (Person 1)
      TapDetection.kt                         (Person 1: isTap(reason) + filters)
      PermissionHelper.kt                     (Person 1: access check + settings intents)
      TapLog.kt                               (Person 1: in-memory last-20 buffer)
    logic/
      TapHandler.kt                           (stub interface + dispatcher, Person 1 creates, Person 2 owns afterward)
    debug/
      DebugScreen.kt                          (Person 1 starter, Person 4 may extend)
      TestNotification.kt                     (channel + post test notification)
app/src/test/java/<package path>/service/
  TapDetectionTest.kt                         (unit tests)
README.md                                     (add "Notification service" section)
```

**Folder ownership (do not edit outside `service/`, `debug/`, and the stub in `logic/`):**
- `logic/` and `data/`: Person 2
- `ui/`: Person 3
- `debug/`: Person 4 extends it later

**Git:**
- Work on a branch named `person1/notification-service`.
- Make small commits with clear messages.
- Do not commit a broken build.
- Commit the shared interface (`TapHandler.kt`) **first** so teammates can code against it immediately.

---

## 6. Gotchas Claude Code must account for

1. **`REASON_CLICK` only fires when a notification auto-cancels on tap.** Notifications that stay after being tapped will not produce a tap event. This is a known limitation. Note it in the README.
2. **adb-posted notifications won't register as taps.** `adb shell cmd notification post ...` posts from the shell package with no `contentIntent`, so tapping them may do nothing. Use the in-app test notification or a real app for tap verification.
3. **Notification access cannot be granted programmatically.** The user must enable it in system settings.
4. **Android 13+ restricted settings:** if the APK is sideloaded (not installed from Android Studio), the notification access toggle may be greyed out. Fix: App info page, three-dot menu, "Allow restricted settings". Mention in the README.
5. **Both overloads exist:** the base class calls the one-argument `onNotificationRemoved` from the three-argument one. Override only the three-argument version to avoid double handling.
6. **Do not do heavy work on the callback thread.** Keep the callback to a filter, a log, and a dispatch.
7. **Group notifications:** tapping a child in a group can trigger a summary removal with a different reason. The filters in 4.2 handle this.
8. **Battery optimization** can kill the service on non-Pixel phones. Document the Unrestricted setting.

---

## 7. Acceptance criteria (definition of done)

On a real phone (Pixel preferred), all of these must pass:

1. App installs and launches to the debug screen.
2. Debug screen shows "not granted" initially. The settings button opens the Notification access screen. After enabling access and returning, it shows "granted" and "service connected: yes."
3. Tapping **Post test notification**, then tapping the notification, adds an entry to the last-20 list and logs `TAP (stub handler): <package>` in Logcat under the tag `FakeNotifs`.
4. **Swipe-dismissing** the test notification adds **nothing** to the list.
5. Tapping a notification from a real app (messages, email) that auto-cancels adds an entry with that app's package name.
6. **Simulate tap** button triggers the stub handler.
7. Toggling notification access off and on again does not crash the app, and the service reconnects.
8. Unit tests for `isTap(reason)` pass: `REASON_CLICK` returns true, and `REASON_CANCEL`, `REASON_CANCEL_ALL`, `REASON_APP_CANCEL`, `REASON_GROUP_SUMMARY_CANCELED` return false.
9. The project builds with `./gradlew assembleDebug` and unit tests run with `./gradlew testDebugUnitTest`.
10. README has a "Notification service" section: how to run, how to grant access, how to verify, and the known limitations.

---

## 8. How to verify (commands for me to run)

```bash
# Build and install on the connected phone
./gradlew installDebug

# Watch only our log lines
adb logcat -s FakeNotifs

# Optional shortcut: grant notification access over adb (if supported on this device)
adb shell cmd notification allow_listener <package>/<package>.service.TapListenerService

# Unit tests
./gradlew testDebugUnitTest
```

---

## 9. Working agreement for Claude Code

1. **Start by listing what already exists in the repo** and confirming whether the project is scaffolded. Do not overwrite existing files.
2. **Plan first:** give a short file-by-file plan, then implement.
3. **Simplest working version first**, then add resilience and the debug screen.
4. Keep the code small, readable, and commented where behavior is non-obvious (especially removal reasons).
5. Do not add dependencies beyond the standard Compose/AndroidX set unless essential.
6. When finished, give me a summary: files created or changed, how to run it, what I must do manually on the phone, and any known limitations.
7. Do not implement anything listed under "Out of scope."

---

## 10. After this is done (hand-off checklist for me)

- [ ] Push the branch and merge `TapHandler.kt` into `main` early so Person 2 can build against it
- [ ] Tell Person 2: call `TapDispatcher.handler = <their implementation>` at app start
- [ ] Tell Person 3: reuse `PermissionHelper.kt` for the onboarding permission screen
- [ ] Tell Person 4: the debug screen and the in-app test notification are ready for their test plan
- [ ] Test battery behavior: leave the phone idle for 10+ minutes, then tap a notification again

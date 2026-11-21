# Fake Notifications

It creates fake notifications
and does other stuff as well

## Notification service

Watches other apps' notifications and reports every **tap** to the consequence logic through one call:
`TapDispatcher.dispatch(packageName)` → `TapDispatcher.handler.onTap(packageName)`.

| File | What it does |
|---|---|
| `logic/TapHandler.kt` | `TapHandler` interface, `TapDispatcher`, logging stub. Install the real handler at app start with `TapDispatcher.handler = ...` |
| `service/TapListenerService.kt` | `NotificationListenerService`. Dispatches only `REASON_CLICK` removals |
| `service/TapDetection.kt` | Pure `isTap(reason)` and `shouldDispatch(...)` filters (unit tested) |
| `service/PermissionHelper.kt` | Notification access check + settings intents, battery settings. Reuse for onboarding |
| `service/TapLog.kt` | In-memory list of the last 20 taps |
| `debug/DebugScreen.kt`, `debug/TestNotification.kt` | Debug screen shown by `MainActivity` |

### Run

```bash
./gradlew installDebug          # build + install on the connected phone
adb logcat -s FakeNotifs        # watch only our log lines
./gradlew testDebugUnitTest     # unit tests
```

### Grant notification access

1. Open the app and tap **Open notification access settings**.
2. Turn on **Taptation**, confirm, and go back. The screen should show `granted` and `Service connected: yes`.
3. Tap **Open battery settings** and set the app to **Unrestricted** (App info > App battery usage), so the system doesn't kill the listener.

Shortcut over adb (works on most devices):

```bash
adb shell cmd notification allow_listener com.codejam.taptation/com.codejam.taptation.service.TapListenerService
```

### Verify

1. **Post test notification**, then tap the notification: a new row shows up in *Last 20 taps* and Logcat shows `TAP (stub handler): com.codejam.taptation`.
2. Post another one and **swipe it away**: no new row (Logcat shows `reason=CANCEL`).
3. Tap a notification from a real app (Messages, Gmail): a row shows up with that app's package name.
4. **Simulate tap**: a `com.test.fake` row shows up and the stub handler logs it.
5. Turn notification access off and on again: no crash, and *Service connected* goes back to `yes`.

### Known limitations

- **Only auto-cancelling notifications count.** `REASON_CLICK` fires only when the notification disappears on tap. Notifications that stay after being tapped produce no event.
- **adb-posted notifications can't be used to test taps.** `adb shell cmd notification post` posts from the shell package with no tap action. Use the in-app test notification or a real app.
- **Access can't be granted from code.** The user has to turn it on in system settings (or use the adb shortcut above).
- **Android 13+ restricted settings.** If the APK was sideloaded rather than installed from Android Studio, the toggle may be greyed out. Fix: App info > ⋮ menu > **Allow restricted settings**, then try again.
- **Battery optimization** can kill the listener, mostly on non-Pixel phones. Set the app to Unrestricted.
- Our own notifications count as taps in **debug builds only**, so the test notification works. Release builds ignore them.
- The tap list lives in memory and is cleared when the app process dies.

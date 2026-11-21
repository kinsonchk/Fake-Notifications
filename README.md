# Fake Notifications

Android hackathon app (launcher name **Taptation**) that tracks notification taps and
applies configurable, probabilistic consequences to notifications classified as bait.
The notification listener and Person 2's logic are connected; the current activity is
an integration/debug screen. Person 3's final product UI is still to be connected.

## Consequence logic (Person 2)

Implemented: app whitelist, package/keyword/app-category bait classification, configurable
catch chance, Mild/Medium/Savage damage, recovery, death/reset, persistent lockout deadlines,
tap counts per app, catch totals, and bounded tap/health history. Raw notification text
is used transiently for classification and is never written to the game store.

Defaults: 7.5% catch probability, 100 maximum health, damage of 10/20/35, +5 health per
completed clean hour, and a 30-second lockout deadline. Bait rules and the whitelist start
empty: **configure a bait package or keyword before expecting normal taps to cause catches**.

See [Person 2 integration and demo guide](docs/PERSON_2_INTEGRATION.md) for the exact rules,
Person 3 APIs, storage behavior, testing commands, and remaining UI work.

## Notification service

Watches other apps' notifications and reports every **tap** to the consequence logic through one call:
`TapDispatcher.dispatch(context)` → `TapDispatcher.handler.onTap(context)`.
The original `dispatch(packageName)` / `onTap(packageName)` entry points remain supported.

| File | What it does |
|---|---|
| `logic/TapHandler.kt` | Backward-compatible `TapHandler` interface and `TapDispatcher` |
| `logic/GameEngine.kt` | Pure classification, catch, health and recovery rules |
| `logic/GameRepository.kt` | Serialized background operations and observable state |
| `logic/PreferencesGameStore.kt`, `logic/GameStateCodec.kt` | Versioned, atomic local snapshots |
| `TaptationApplication.kt` | Installs the real handler even when the activity is not open |
| `service/TapListenerService.kt` | `NotificationListenerService`. Dispatches only `REASON_CLICK` removals |
| `service/TapDetection.kt` | Pure `isTap(reason)` and `shouldDispatch(...)` filters (unit tested) |
| `service/PermissionHelper.kt` | Notification access check + settings intents, battery settings. Reuse for onboarding |
| `service/TapLog.kt` | In-memory list of the last 20 taps |
| `debug/DebugScreen.kt`, `debug/LogicDebugPanel.kt`, `debug/TestNotification.kt` | Listener and logic integration screen shown by `MainActivity` |

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

1. **Post test notification**, then tap the notification: a new row shows up in *Last 20 taps* and Logcat shows `tap pkg=com.codejam.taptation outcome=NoPenalty reason=unknown health=100` on a fresh install.
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

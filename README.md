# Fake Notifications

Team docs, one folder per person: [docs/](docs/README.md).

Android hackathon app (launcher name **Taptation**) that tracks notification taps and
applies configurable, probabilistic consequences to notifications classified as bait.
The notification listener and Person 2's logic are connected; the current activity is
an integration/debug screen. Person 3's final product UI is still to be connected.

## Consequence logic (Person 2)

Implemented: ordered whitelist/category/VIP/urgency bypass rules, notification classification,
observed app/channel rules, configurable
catch chance, Mild/Medium/Savage damage, recovery, death/reset, persistent lockout deadlines,
tap counts per app, catch totals, and bounded tap/health history. Raw notification text
is used transiently for classification and is never written to the game store.

Defaults: 7.5% catch probability, 100 maximum health, damage of 10/20/35, +5 health per
completed clean hour, and a 30-second lockout deadline. Complete, recognized social/promo
and ordinary email/message notifications default to bait after all importance checks.
Incomplete or uncertain signals never receive penalties. App profiles and channel overrides
start empty: configure them using observed data rather than guessed package/channel names.

See [Person 2 integration and demo guide](docs/person-2/PERSON_2_INTEGRATION.md) for the exact rules,
Person 3 APIs, storage behavior, testing commands, and remaining UI work.

## Notification service

Watches other apps' notifications and reports every **tap** to the consequence logic through one call:
`TapDispatcher.dispatch(context)` → `TapDispatcher.handler.onTap(context)`.
The original `dispatch(packageName)` / `onTap(packageName)` entry points remain supported.

| File | What it does |
|---|---|
| `logic/TapHandler.kt` | Backward-compatible `TapHandler` interface and `TapDispatcher` |
| `logic/GameEngine.kt` | Pure classification, catch, health and recovery rules |
| `logic/NotificationClassifier.kt` | Ordered importance/bait rules and conservative fallback |
| `logic/GameRepository.kt` | Serialized background operations and observable state |
| `logic/PreferencesGameStore.kt`, `logic/GameStateCodec.kt` | Versioned, atomic local snapshots |
| `TaptationApplication.kt` | Installs the real handler even when the activity is not open |
| `service/TapListenerService.kt` | `NotificationListenerService`. Dispatches only `REASON_CLICK` removals |
| `service/TapDetection.kt` | Pure `isTap(reason)` and `shouldDispatch(...)` filters (unit tested) |
| `service/PermissionHelper.kt` | Notification access check + settings intents, battery settings. Reuse for onboarding |
| `service/TapLog.kt` | In-memory list of the last 20 taps |
| `service/NotificationSignals.kt` | Category, channel, importance, conversation flags and timing for the `posted`/`removed` Logcat lines (never title/text) |
| `ui/TaptationApp.kt` | Bottom tabs shown by `MainActivity` (see *App layout* below) |
| `ui/home`, `ui/stats`, `ui/settings`, `ui/common` | Home status card, stats, settings, shared layout and tap-reason text |
| `debug/DebugScreen.kt`, `debug/NotificationsScreen.kt`, `debug/TestNotification.kt` | Debug-only tabs: system status + demo controls; notification composer, saved presets, tap reasons, observed notifications |

### App layout

Bottom tabs, each answering one question. Debug and Notifications exist only in debug builds.

| Tab | Shows |
|---|---|
| **Home** | Tree status: health, catches, taps, lockout countdown, last tap and why. Warns if notification access is off. Person 3 replaces it with the tree. |
| **Stats** | Totals, taps per app, recent taps with their reason, health history. |
| **Notifications** | Post test notification; custom notification form (package, title, text, category, channel, conversation, actions, image) with a live "would be" preview; saved presets; last 20 taps with classification, reason and outcome; observed notifications with the classification they would get. |
| **Settings** | Catch chance and level, whitelisted apps, VIP names, important and bait keywords, per-app rules (bait app, profile, channels). |
| **Debug** | Access/service/battery status, demo controls (0/100 %, set health, force catch, fast-forward, reset), simulate bait tap. |

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

1. **Notifications** tab > **Post test notification**, then tap the notification: a new row shows up in *Last 20 taps* with its classification and reason (a fresh install shows *Unsure*, because the test notification has no category). Mark its package as bait in **Settings** > *Per-app rules* to enable catch rolls for it.
2. Post another one and **swipe it away**: no new row (Logcat shows `reason=CANCEL`).
3. Tap a notification from a real app (Messages, Gmail): a row shows up with that app's package name.
4. **Simulate tap (no notification)** on the custom notification form: a row with the typed package shows up without using the shade.
5. Turn notification access off and on again: no crash, and *Service connected* goes back to `yes`.

### Reading the Logcat lines

Every posted and removed notification logs its signals (filter `tag:FakeNotifs`):

```
posted pkg=com.discord update=false cat=msg channelId=... channel="Direct Messages" importance=HIGH conversation=true groupConversation=false style=MessagingStyle autoCancel=true ongoing=false summary=false group=true contentIntent=true postTime=... age=3ms key=...
removed pkg=com.discord reason=CLICK(1) cat=msg channelId=... ... age=8123ms key=...
```

- `autoCancel=false`: tapping won't remove it, so the tap is never detected.
- `update=true`: the app replaced an active notification (same key). A small `age` on an update means `postTime` was reset.
- `?` means Android didn't provide the value: `conversation` needs Android 12+, `groupConversation` is only set by MessagingStyle, and `channel`/`importance` are often missing on `removed` lines (use the `posted` line).
- Title and text are never logged.

### Known limitations

- **Only auto-cancelling notifications count.** `REASON_CLICK` fires only when the notification disappears on tap. Notifications that stay after being tapped produce no event.
- **adb-posted notifications can't be used to test taps.** `adb shell cmd notification post` posts from the shell package with no tap action. Use the in-app test notification or a real app.
- **Access can't be granted from code.** The user has to turn it on in system settings (or use the adb shortcut above).
- **Android 13+ restricted settings.** If the APK was sideloaded rather than installed from Android Studio, the toggle may be greyed out. Fix: App info > ⋮ menu > **Allow restricted settings**, then try again.
- **Battery optimization** can kill the listener, mostly on non-Pixel phones. Set the app to Unrestricted.
- Our own notifications count as taps in **debug builds only, and only the test notification** (channel `debug_test`). Release builds ignore them. Other notifications we post never count, so a future "you got caught" alert can't cause another catch.
- The tap list lives in memory and is cleared when the app process dies.

## Person 4 debug controls

The **Debug** tab has the Person 4 demo controls for probability, health, force catch, fast-forward, and reset. They go through `RepositoryDemoControls`, which calls the real `GameRepository`, so they change the same saved game as normal taps. Release builds hide the Debug and Notifications tabs.

The in-app test notification is the repeatable tap test because it has a content action and auto-cancels. A shell-posted ADB notification can check appearance, but should not be used to verify tap detection. Use db logcat -s FakeNotifs to inspect listener messages.


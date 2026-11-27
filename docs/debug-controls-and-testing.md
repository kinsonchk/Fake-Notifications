# Debug controls and testing

The Debug tab exists only in debug builds. It helps prove the app works and makes a demo easy to
repeat. It holds no game rules: those live in `logic/` (see [logic/game-rules.md](logic/game-rules.md)).
Build, install, notification access and the basic tap/swipe check are in the
[developer guide](developer-guide.md).

## Set up Java and the Android SDK (Windows)

Gradle needs a full JDK (17 or newer; AGP 9.x uses JDK 17 by default and Gradle 9 also runs on newer
JDKs). A Java runtime without `jlink.exe`, such as the one bundled with some editors, fails the
build. If Android Studio is not set up to pick the JDK, set these in each new PowerShell window
(adjust the paths to your machine; don't commit machine-specific paths):

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Java\<your JDK>"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --stop     # restart the Gradle daemon so it picks up the new JDK
```

These variables last only for the current window. To avoid retyping them, set them as Windows user
variables or choose the Gradle JDK in Android Studio. A `.env` file is not read by PowerShell or Gradle.

## What the Debug tab has

- **Notification tools**: a quick test notification, the custom notification composer, saved
  notifications, the last 20 taps with their classification and reason, and the last observed
  notifications with a live classifier preview (memory only).
- **System**: notification access, listener connection and battery status, with buttons to the
  matching Android settings.
- **Demo controls** (`DemoControlsPanel`, backed by `RepositoryDemoControls`, which calls the real
  `GameRepository`):
  - **Set 0% / Set 100%**: debug catch probability.
  - **Set health**: `setHealthForDebug` (0-100).
  - **Set damage** (1-100) and **Default damage**: damage per catch for the selected level.
  - **Set lockout** (1-3600 s) and **Default lockout** (15 s): applies from the next catch.
  - **Debug recovery interval**: +5 health every 1, 5 or 15 clean minutes, or **60 min · normal**.
  - **Force catch**: `applyCatch` at the current level, with the same damage, lockout and pending
    choice as a tap catch. Ignored while dead or locked out.
  - **Fast-forward 1 clean hour**: `advanceTimeForDebug`. Recovery and the lockout deadline move forward.
  - **Reset demo data**: health 100, no lockout, empty tap and health history and counters.
    Settings are kept. SQLite stats are cleared separately (Stats harness > **Clear stats**).
- **Visual previews**: **Open meadow preview** and **Preview caught animations**. They use local
  fixtures and never write game, meadow or appearance state.
- **Debug probability override**: a 0-100% slider; **Use difficulty value** clears it. Choosing a
  difficulty in Settings also clears it. Release builds ignore it.
- **Demo time limit**: freshness windows of 30 s, 1 min or 2 min for stale-tap demos.
- **Stats harness**: week/month summary, include important, generate 30 days of fake history, clear stats.
- **Shortcuts**: **Simulate bait tap** sets `com.test.fake` to Always bait and sends a tap without the
  shade; **Simulate stale bait** sends one older than the freshness window.

Every debug-only repository call checks the build is debuggable, and `GameRepository.updateConfig`
strips debug-only config fields in release.

## Custom notifications (stand in for another app)

**Debug** > **Notification tools** has a custom notification form: package, title, text, category,
channel name, app category, conversation flag, chat sender, group chat, attachment kind, action
buttons and an optional image. Then:

- **Post custom notification** posts a real notification from Taptation on the debug test channel.
  Tap it in the shade to run the full pipeline. In debug builds the listener replaces the package,
  channel name, conversation flag and app category with the typed values, so the logic sees, for
  example, `com.instagram.android`. Title, text, category, sender, attachment and action buttons
  are set on the notification itself.
- **Simulate tap (no notification)** skips the shade and sends the same context straight to the logic.
- **Save as** keeps the form as a named preset under **Saved notifications** (kept across restarts)
  with Post / Edit / Delete.
- **Would be** shows the classification and reason the current settings would give, before posting.

Only the package is simulated, not the app, so use a real app's notification for the final check.

## Automated tests

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat testDebugUnitTest --tests "com.codejam.taptation.logic.GameEngineTest"   # one class
```

JVM tests need no device. They cover tap detection (`TapDetectionTest`), the classifier and per-app
rules (`NotificationClassifierTest`), game rules and snapshot round trips (`GameEngineTest`, for example
`alwaysBaitCatchesWithoutAiOrProbabilityRoll` and `configAndAllHistoryRoundTrip`), the revised flow
(`RevisedFlowTest`), the AI question and queue (`ClefQuestionTest`, `JudgeQueueTest`), stats
(`StatsCalculatorTest`, `StatsRepositoryTest`, `RevisedStatsTest`) and the meadow.

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain   # needs a phone or emulator
.\gradlew.bat assembleDebugAndroidTest --console=plain    # only compiles the instrumented tests
```

Instrumented tests include `DemoControlsPanelTest` (button wiring against a fake `DemoControls`),
`CaughtOverlayTest` and `ZeroHealthCatchTest` (the real overlay), `SqliteStatsStoreTest`, UI and meadow
tests, and the live AI judge tests in `ai/` (they need the Cloudflare keys; see the root
[CLAUDE.md](../CLAUDE.md) for the command). Before running them, wake the emulator
(`adb shell input keyevent KEYCODE_WAKEUP`, `adb shell svc power stayon true`); with a phone also
plugged in, Gradle targets every device.

### Connect a device

- **Phone**: enable Developer options (tap **Build number** seven times), turn on **USB debugging**,
  connect with a data cable and accept the **Allow USB debugging?** prompt. See Android's
  [developer options](https://developer.android.com/studio/debug/dev-options) and
  [run on a device](https://developer.android.com/studio/run/device.html) pages.
- **Emulator**: Android Studio > **Tools** > **Device Manager**, create or start a virtual phone
  (API 37 matches `compileSdk`). See [managing AVDs](https://developer.android.com/studio/run/managing-avds).

`adb devices -l` should then list the device with the status `device` (`unauthorized` means the
prompt on the phone was not accepted). On an emulator, open the shade by dragging down from the top
edge of the screen with the mouse.

## Checks on a phone

Start with the tap/swipe check in the [developer guide](developer-guide.md#verify).

### Per-app rules

Without AI keys, Let us decide taps are Unknown (no penalty), so use the app rules to see a catch.

1. Pick an installed app that can post an auto-cancelling notification on demand (or use a custom
   notification with its package).
2. In **Debug**, set the catch probability to 100% and health to 100.
3. In **Settings** > **Notification rules**, set the app to **Always important**. Tap one of its
   notifications: the tap row says Important, health and catches are unchanged, and the app opens normally.
4. Set the app to **Always bait** and set the debug catch chance to 0%. Tap a fresh notification
   outside the lockout: it is still caught (no AI call, no roll).
5. Tap another one during the 15 s lockout: no second catch.
6. Swipe one away: no tap row and no catch; it is recorded as a dismissal for stats only.
7. Close and reopen Taptation: the rule is still saved.

### Caught choices

1. Enable notification access. In **Settings** > **Catch reminders**, allow showing reminders over
   other apps and allow reminder notifications (Android 13+ asks for notification permission).
2. Set health to 100 and set a real app to **Always bait**. With Taptation in the background, tap
   that app's notification. Android opens the app, then the caught overlay appears. The catch
   damage is already applied before the choices show.
3. **Go to Taptation** opens Taptation; health reflects only the catch damage.
4. **Continue to <app> · −5 health** removes 5 more health once and runs the notification's
   original action when available, else opens the app, else reports that it is no longer available.
   Tapping it twice quickly still costs 5 only once.
5. A notification posted by Taptation itself opens Taptation, so it shows the in-app dialog (the
   foreground path), not the overlay. Dismissing that dialog (Back or outside) postpones the choice;
   **Choose now** in Taptation brings it back. Neither adds the extra damage.
6. Turn overlay access off and trigger another catch: a **Caught you** notification opens the same
   two choices. Dismissing it chooses nothing and adds no damage.
7. Set the app to **Always important** and repeat: no catch and no choice.

### Release build

```powershell
.\gradlew.bat assembleRelease --console=plain
```

The release build has no Debug tab and ignores every debug override.

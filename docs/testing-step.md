# Person 4: What Was Done and How to Test It

## Set up Java and Android for Gradle

Open PowerShell in the project folder. In each new PowerShell window, run these setup lines before running Gradle:

```powershell
$env:ANDROID_HOME = "C:\Users\OfflineUser\AppData\Local\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --stop
```
Rerun this if want to reupdate your app to the new change. installDebug builds the latest debug app and installs it.
```powershell
$env:ANDROID_HOME = "C:\Users\OfflineUser\AppData\Local\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:JAVA_HOME\bin;$env:Path"

& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
.\gradlew.bat installDebug --console=plain
```

These are environment variables for the current PowerShell window. PowerShell passes them to Gradle when it starts:

- `ANDROID_HOME` tells Android/Gradle where the Android SDK is installed.
- `JAVA_HOME` tells Gradle which Java installation to use.
- Adding `JAVA_HOME\bin` to `Path` lets Windows find Java tools, including `jlink.exe`.
- `gradlew.bat --stop` shuts down old Gradle background processes. Gradle keeps a background process running, so stopping it helps ensure the next build uses the newly selected Java installation.

The earlier error named a VS Code Java **runtime** that did not contain `jlink.exe`. The full JDK at `C:\Program Files\Java\jdk-25` does contain it. Setting `JAVA_HOME` to that full JDK fixed the error; the unit tests then completed successfully. AGP 9.5 requires at least JDK 17 and uses JDK 17 by default. Gradle 9.6 can run with JDK 25. See [Android's AGP 9.5 compatibility notes](https://developer.android.com/build/releases/past-releases/agp-9-5-0-release-notes) and [Gradle's Java compatibility table](https://docs.gradle.org/current/userguide/compatibility.html).

You do not need a `.env` file for this. PowerShell and Gradle do not load a `.env` file automatically; it is commonly used by web projects or tools that explicitly support it. Here, setting `$env:...` is simple and applies only to this window. When the window closes, these temporary settings go away. You could set them as permanent Windows user variables or choose the Gradle JDK in Android Studio if you want to avoid entering them each time. Keep the Java path local to your machine rather than adding a machine-specific path to shared project files.
## In plain words

Person 4's job is to help the team prove the app works and to make the demo easier to repeat. Person 4 does not build the main catch/health rules. Those belong to Person 2.

The work in this project gives you a debug screen, a test notification, and checks that can tell whether the app handled a notification tap correctly.

## What Person 4 added

### 1. A panel for setting up a demo

The **Debug** tab has a **Demo controls** panel. It sets catch chance to 0% or 100%, sets health, forces a catch, fast-forwards one clean hour, and resets demo data.

The buttons call a shared Kotlin interface named `DemoControls`. This is like a set of agreed buttons that Person 2's real health and catch code can connect to. The screen does not change the database by itself.

**Right now, those buttons are not connected to real health/catch logic.** The app shows “Not connected yet” because Person 2's logic and shared store are not in this project yet. This is expected. It prevents the demo from pretending a catch happened when it did not.

### 2. A better repeatable test notification

The app already had a **Post test notification** button. Person 4's changes keep that as the real tap test and give each notification its own action ID. The notification opens the app and disappears after a tap.

A real tap and a swipe are different:

- Tapping should add one row to **Last 20 taps** on the **Notifications** tab, with its classification and the reason.
- Swiping it away should not add a row.

The **Simulate tap (no notification)** button on the custom notification form only tests the app's internal handoff. It does not test tapping a notification in Android's notification shade.

### 3. A check so debug tools are hidden in the release build

The app shows bottom tabs: Home, Stats, Notifications, Settings, Debug. The Notifications and Debug tabs exist only in the debug build; the release build shows Home, Stats and Settings.

### 4. A test for the new panel

`DemoControlsPanelTest` presses the panel buttons using a fake `DemoControls`. It checks that the interface receives the expected requests. This test checks that the buttons are connected to the interface; it does not test Person 2's real catch or health rules.

## How to test the work

### A. Run the unit tests

Open PowerShell in the project folder and run:

if not done (optional)
```powershell
$env:ANDROID_HOME = "C:\Users\OfflineUser\AppData\Local\Android\Sdk"
```
Do this:
```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

**What this checks:** notification-click filtering. Click is accepted; swipes and other notification removals are ignored.

**What success looks like:** the command ends with `BUILD SUCCESSFUL`.


The JVM unit suite also covers the whitelist without a phone:

- `whitelistWinsOverAllBaitRules` proves a whitelisted package bypasses bait and keyword rules, causes no random roll or health loss, and is still recorded.
- `removingWhitelistRestoresBaitRuleAndPreservesOtherWhitelistEntries` proves disabling the whitelist restores the bait consequence while preserving other selected apps.
- `configAndAllHistoryRoundTrip` proves the whitelist configuration survives save/load.
### B. Connect a phone or start an emulator, then run the demo-panel test

The command you tried started ADB successfully. That part is normal. Because nothing appeared below **List of devices attached**, ADB did not find a phone or a running emulator yet. The test command needs one before it can run.

The command should be typed without a final backslash:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
```

#### Option 1: connect an Android phone (recommended for the final demo)

1. On the phone, open **Settings > About phone**. Tap **Build number** seven times. Some phones put Build number under **About phone > Software information**. Enter the phone PIN if asked. Android should say developer options are enabled.
2. Go back to Settings and open **System > Developer options** (the location can differ by phone). Turn on **USB debugging**.
3. Connect the unlocked phone to the computer with a USB cable that supports data. If the phone asks what the USB connection is for, choose **File transfer**.
4. When the phone asks **Allow USB debugging?**, approve it. You can select **Always allow from this computer** on your own trusted computer.
5. In PowerShell, run the ADB command above again.

You should see a row with the phone serial number and the word `device`, for example:

```text
List of devices attached
ABC1234567    device product:... model:...
```

If the list is still empty, unlock the phone, check USB debugging is on, try another USB data cable/port, and check whether Windows needs the phone maker's USB driver. If the row says `unauthorized`, look at the phone and approve the debugging prompt. Android's official instructions are [here](https://developer.android.com/studio/debug/dev-options) and [here](https://developer.android.com/studio/run/device.html).

#### Option 2: start an Android emulator

1. __Open the project__ in Android Studio.
2. Open **Tools > Device Manager**. (In some Android Studio versions, use the Device Manager icon on the right toolbar.)
3. There is a phone in default. 

If want to created new device: \
3b. Click on +, then Choose **Create Virtual Device**.
1. Choose a phone profile, such as a Pixel, and click **Next**.
2. Choose an Android system image. API 37 matches this project's `compileSdk`; if it is not installed, click its download icon and wait for it to finish.
3. Keep default android system image: Google Play Intel x86_64 Atom System Image. Click **Finish**.

next step: \
4. In Device Manager, click the triangle **Run/Launch** button beside the new virtual phone. Wait until Android finishes starting and the home screen appears.
5.  In PowerShell, run the ADB command above:
```bash
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
```
. The emulator should appear with status `device`, often with a name like `emulator-5554`.

Android's official steps for creating and starting a virtual device are [here](https://developer.android.com/studio/run/managing-avds).

#### Run the test after the device appears

With either the phone or emulator listed as `device`, run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

**What this checks:** the instrumentation test opens the Demo controls panel, presses its buttons, and confirms a fake implementation received requests for 0%, 100%, health 1, force catch, one-hour fast-forward, and reset. This tests button wiring only; use the app itself for real whitelist and game behavior.

**What success looks like:** Gradle ends with `BUILD SUCCESSFUL`, and the report lists `DemoControlsPanelTest` as passing. These tests do not cover the whitelist picker.

If you have no phone/emulator, you cannot run an Android instrumentation test. You can only compile its APK:

```powershell
.\gradlew.bat assembleDebugAndroidTest --console=plain
```

`BUILD SUCCESSFUL` here means the instrumentation-test code compiles. It does not mean the test ran. An emulator is enough for the panel test; use the actual presentation phone for final checks of notification taps, swipes, permission settings, and battery behavior.

### C. Check a real notification tap and swipe

This check needs a real Android phone or an emulator with the app installed. A real phone is best for the final demo.

1. Install the debug app:

   ```powershell
   .\gradlew.bat installDebug
   ```

2. Open Taptation. \
if didn't find: \
In the emulator, swipe up from the bottom of the home screen to open the app drawer. Tap the search box at the top. Type Taptation.
3.  Tap **Open notification access settings**, enable Taptation, and return. 
4. Confirm the screen says notification access is granted and the service is connected.
5. Tap **Post test notification**.
6. Open Android's notification shade: \
If don't know where to find:\
Move the mouse to the very top of the emulator screen.
Hold the mouse button and drag down.
Find the Taptation test notification and tap it.
7. and tap the Taptation test notification.
8. Return to Taptation's **Notifications** tab. There should be one new row in **Last 20 taps**.
9.  Post another test notification, open the shade, and swipe it away.
10. Return to Taptation. The swipe should not add a row.

To see diagnostic messages, open another PowerShell window and run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -s FakeNotifs
```

A tapped notification should log `reason=CLICK`. A swipe should log a cancel reason and should not be dispatched as a tap.

### C.1 Test the whitelist on your Android phone

This feature is implemented in the debug build. Use a notification posted by the actual app you select: Android identifies each notification by the package that posted it. The Taptation test notification always belongs to Taptation, so it cannot stand in for Instagram, YouTube, or another app. The **Custom notification** card (see "Custom notifications" below) can simulate another package in debug builds for quick checks; use the real app for the final demo check.

#### Prepare the phone and app

1. Pick an installed app that can post a notification you can trigger during the demo. It must appear in **Whitelisted apps** (the picker lists apps with launcher icons).
2. Find the app's exact package name. For example, to check whether Instagram or YouTube is installed:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" shell pm list packages | findstr /i "instagram youtube"
   ```

   Use the full value after `package:`. You can use any installed app that can send a testable notification, not only those examples.

#### Prove the whitelist bypasses a catch

1. In Taptation's **Settings** tab, set **Catch chance** to 100% by moving its slider all the way right.
2. In **Per-app rules**, enter the selected app's package name and tap **Mark as bait app**. The initial bait list is empty, so this step is needed for a clear comparison.
3. In **Whitelisted apps**, search for that app and turn its switch on. Wait for the switch to reflect the saved setting.
4. Note the current **Health** and **Catches** values.
5. Cause that app to post a notification (for example, send yourself a test message or trigger an app alert). Open Android's notification shade by dragging down from the top of the phone screen, then tap the app's notification. Do not swipe it away.
6. Return to Taptation. Home's **Last tap** should show `Important: App is whitelisted`. Health and catch count should be unchanged. The notification should still have opened its own app normally.

#### Prove turning the whitelist off restores the bait rule

1. Return to Taptation. Turn off the same app's whitelist switch. Keep it marked as bait and keep catch chance at 100%.
2. Cause the same app to post another auto-cancel notification, then tap it in the notification shade.
3. Return to Taptation. The tap should be caught and health should decrease. The catch count should increase. If the app is already in lockout or the tree is dead, set health to 100 in **Debug** > Demo controls before repeating.
4. Turn the whitelist switch back on, close and reopen Taptation, and confirm that it remains selected. This checks saved settings.
5. Finally, swipe away one more notification from that app. It should not create a new tap row or cause a catch.

If no suitable app can post a notification during the demo, the unit tests below still verify the game rule. Taptation's **Post test notification** button checks notification tap detection, but it is not a substitute for this app-specific whitelist check.

For a quick check without the real app, post a **Custom notification** with the selected package (see "Custom notifications" below).
### C.2 Test caught feedback choices

This check needs a phone or emulator with the latest debug app. Enable **Notification access** and open **Settings > Caught feedback**. Tap **Allow caught overlay** and grant Android's **Display over other apps** access. Also enable **notification fallback** (Android 13+ asks for notification permission).

Set the debug catch probability to 100% and configure an app/package as bait. For a real background-overlay check, use a notification from that actual app while Taptation is not in the foreground. Tap it; Android opens the source app, then Taptation should show the two-choice overlay:

- **Go to Taptation** acknowledges the saved catch and brings Taptation to Home.
- **Go to original app** acknowledges the catch and invokes the notification's original action when it is still available. If it is unavailable, Taptation falls back to opening the source app or reports that it is no longer available.

For a custom notification posted by Taptation, expect the two-choice Compose dialog in Taptation, because tapping the test notification opens Taptation itself. This checks the foreground path, not the background overlay.

To check the fallback, turn off overlay access in Android Settings, trigger another catch, and tap the **A moment to choose** notification. The same choices should appear in Taptation. If Android suppresses an overlay or notification, the catch remains saved and should be presented when Taptation is opened.

Repeat with the package whitelisted: no pending catch choice should appear. Swipe a bait notification away: it should not create a catch choice. A catch is applied once before presentation; neither button should reduce health a second time.
### D. Check the release build

Run:

```powershell
.\gradlew.bat assembleRelease --console=plain
```

**What this checks:** the release version builds with the Notifications and Debug tabs hidden.

## Results already obtained in this project

These commands succeeded after the Person 4 changes:

- `testDebugUnitTest` — unit tests passed. After the `jlink.exe` error, setting `JAVA_HOME` to the full JDK at `C:\Program Files\Java\jdk-25` fixed the build; rerunning the unit test command ended with `BUILD SUCCESSFUL`.
- `connectedDebugAndroidTest` — all 3 instrumentation tests passed on `Medium_Phone_API_37.0(AVD)`: the app context test and both `DemoControlsPanelTest` cases. The first run failed because Espresso 3.5.1 used a removed reflective API; upgrading to Espresso 3.7.0 fixed it.
- `assembleRelease` — the release build compiled.

The instrumentation tests have now run successfully on the API 37 emulator. The real notification-shade checks (tap, swipe, access settings, and battery behavior) still need to be done on the presentation phone.

## Demo controls are now connected

`RepositoryDemoControls` implements `DemoControls` with Person 2's `GameRepository`, and the Debug tab uses it. The buttons now change real game state through the same engine and store as normal taps:

- **Set 0% / Set 100%**: catch chance (`updateConfig`).
- **Set health**: `setHealthForDebug` (values outside 0-100 are clamped).
- **Force catch**: `applyCatch` at the current level. Ignored while dead or locked out, like a real catch.
- **Fast-forward 1 clean hour**: `advanceTimeForDebug`. Recovery and the lockout deadline move forward one hour.
- **Reset demo data**: `resetDemoDataForDebug`. Health 100, no lockout, empty history and stats. Settings (whitelist, chance, level, VIPs) are kept.

All of these refuse to run in a release build.

## Custom notifications (stand in for another app)

The **Notifications** tab has a **Custom notification** form. Fill in package, title, text, category, channel name, app category, conversation flag and action buttons, optionally choose an image (shown as the notification's picture), then:

- **Post custom notification** posts a real notification from Taptation on the debug test channel. Tap it in the shade to run the full pipeline. In debug builds the listener replaces the package, channel name, conversation flag and app category with the values you typed, so the logic sees, for example, `com.instagram.android`. Title, text, category and action buttons are set on the notification itself.
- **Simulate tap (no notification)** skips the shade and sends the same context straight to the logic.
- **Save as** keeps the form as a named preset under **Saved notifications** (kept across restarts) with Post / Edit / Delete.
- **Would be** shows the classification and reason the current settings would give, before posting.

This makes the whitelist test in C.1 possible without the real app: whitelist a package, post a custom notification with that package, tap it, and check there is no catch. Use a real app's notification for the final demo check, because only the package override is simulated, not the app.

## Remaining phone-only check

The whitelist picker, bypass logic, and caught overlay are implemented. The remaining manual verification is to exercise a real source-app notification and overlay permissions on the presentation phone; automated JVM tests cannot exercise Android's notification shade, overlay window behavior, or device-specific permission screens.


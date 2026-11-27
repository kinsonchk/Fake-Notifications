> Historical, kept for reference: the app whitelist was replaced by per-app rules (Always important / Always bait / Let us decide); see [ui-reference/feature-per-app-rules.md](../ui-reference/feature-per-app-rules.md).

# App Whitelist Implementation Design

## Goal

Let the user choose apps such as Instagram or YouTube whose notification taps must never trigger a catch. Keep this feature small: the game rules and persistence already support an app whitelist. The installed-app picker is now implemented in the debug/settings surface and reuses the existing whitelist logic and storage.

## Is an app whitelist a good idea?

Yes. Users need a simple way to protect apps that may carry important messages, such as family, work, banking, maps, ride-share, or health apps. A whitelist is predictable: the user chooses an app, and the rule is applied every time.

The trade-off is that this is an **app-wide** exception. Whitelisting Instagram makes every Instagram notification safe, including distracting likes and recommendations as well as useful direct messages. For the first version, that is a reasonable and understandable trade-off. Explain it in the UI. Per-channel exceptions can be a later feature if app-wide rules prove too broad.

## What “whitelist” means here

A whitelist does not stop Instagram/YouTube notifications from appearing on Android, and it cannot stop Android's notification listener from seeing a tap after the user grants notification access. The listener needs to receive the package name to decide what to do.

The recommended MVP meaning is: **Taptation may see and count the tap, but it will never penalize it.** The current game code records whitelisted taps as `Bypass`, increments the app's tap count, makes no random roll, applies no damage, and does not reset the recovery timer. Notification title and body are not persisted.

If “don't get into this app” means that the tap should not appear in Taptation's statistics either, that is a different rule from whitelist. It would require an explicit **Don't track this app** option and different history behavior. Don't silently change the existing stats behavior under the word “whitelist.”

## What already exists

- `GameConfig.whitelistedPackages` holds a `Set<String>` of package names and starts empty.
- `GameRepository.setWhitelisted(packageName, enabled)` queues changes through the existing worker and persists them with the game snapshot.
- `GameRepository.isWhitelisted(packageName)` exposes the current setting.
- `GameEngine.onTap(...)` returns `TapOutcome.Bypass` for a whitelisted package before the random roll or damage. The tap remains recorded as a Bypass.
- `GameEngineTest.whitelistWinsOverAllBaitRules` already checks whitelist precedence, unchanged health, and zero random rolls.
- The game configuration/history round-trip test includes the whitelist set.
- `LogicDebugPanel` already has developer controls to type a package name and add/remove it from the whitelist. This is useful for testing but is not a friendly app picker.

So the core rule and storage do not need a second implementation. The missing user-facing feature is a simple way to see installed apps and toggle their existing package names.

## Smallest useful UI

Add one **Whitelisted apps** section to the existing settings/debug surface. Do not build a new navigation system, a separate settings database, or a large settings redesign for this feature.

The section needs:

1. A short explanation: “Every notification tap from a whitelisted app is safe from catches.”
2. A list of installed launcher apps, with icon, app name, and a switch.
3. Optional search by app name or package name if the list is long. Search is a local filter; it does not alter whitelist data.
4. A small **Refresh apps** action, or refresh the list when the section becomes visible again.
5. A loading/error state while game state or the app list is unavailable.

The switch reads from `GameRepository.state.config.whitelistedPackages` and writes through `GameRepository.setWhitelisted`. Do not create a second `SettingsRepository` or DataStore. The game repository is already the source of truth; the UI should reuse it.

A simple, separately testable Compose list can receive the current set and a callback:

```kotlin
@Composable
fun WhitelistedAppsList(
    apps: List<InstalledAppOption>,
    selectedPackages: Set<String>,
    enabled: Boolean,
    onChange: (packageName: String, enabled: Boolean) -> Unit,
) {
    LazyColumn {
        items(apps, key = { it.packageName }) { app ->
            val selected = app.packageName in selectedPackages
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(app.label)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = selected,
                    enabled = enabled,
                    onCheckedChange = { checked -> onChange(app.packageName, checked) },
                )
            }
        }
    }
}
```

Use package name as the stable row key and saved value. Labels can be translated or duplicated. Show the package name in smaller text when labels collide or for debugging.

The screen owner should call the existing asynchronous API and let repository `StateFlow` update the switch. Never call `.get()` or `.join()` on a future on the UI thread:

```kotlin
fun changeWhitelist(packageName: String, enabled: Boolean) {
    repository.setWhitelisted(packageName, enabled).whenComplete { _, failure ->
        if (failure != null) {
            // Publish a short error on the main thread; the repository's state remains authoritative.
        }
    }
}
```

Disable the affected switch while its write is pending to avoid two quick taps racing. If the save fails, show an error and continue displaying the value from the repository state rather than leaving an optimistic switch value behind.

## Finding installed apps safely

The app list needs `PackageManager`, but Android 11+ limits which installed apps a package can query. This app only needs apps with a launcher icon, so declare visibility for the launcher intent. Do not request broad `QUERY_ALL_PACKAGES` access. Android recommends requesting the smallest package visibility needed for the app's use case. See [Android package visibility guidance](https://developer.android.com/training/package-visibility/declaring) and the [`PackageManager.queryIntentActivities` API](https://developer.android.com/reference/android/content/pm/PackageManager#queryIntentActivities(android.content.Intent,android.content.pm.PackageManager.ResolveInfoFlags)).

Add this directly under `<manifest>` and before `<application>` in `app/src/main/AndroidManifest.xml`:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

Then obtain app labels, icons and package names from the matching launcher activities. Deduplicate by package because an app can have more than one launcher activity. Exclude Taptation itself from the regular user list; its own test notifications are only used by the debug build.

Example query helper for the project's minSdk 26, including the API 33 typed-flags overload:

```kotlin
private fun queryLauncherActivities(
    packageManager: PackageManager,
    launcherIntent: Intent,
): List<ResolveInfo> {
    @Suppress("DEPRECATION")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0L),
        )
    } else {
        packageManager.queryIntentActivities(launcherIntent, 0)
    }
}
```

Run discovery and icon loading away from the UI thread. Keep the resulting app list in memory; persist only selected package names. A saved package whose app is uninstalled has no notification source, so it cannot trigger a catch. For this small version, either retain the package so a reinstall retains the user's choice, or show it in a simple “No longer installed” section with a remove action. Do not silently delete saved preferences during an app-list refresh.

## Existing catch behavior to preserve

The flow should remain:

```text
Android tap
    → TapListenerService accepts REASON_CLICK only
    → TapDispatcher sends NotificationContext to GameRepository
    → GameEngine checks the package whitelist
        → listed: Bypass; record tap; no roll, damage, or recovery reset
        → not listed: existing classification and consequence rules continue
```

Do not put whitelist checks in `TapDetection`. That class decides whether a notification removal was a tap; it should not decide game policy. Do not cancel, hide, snooze, or block notifications from another app. The notification remains in Android and opens its source app normally.

## How to test the whitelist logic and small UI

### Existing logic test

Keep `GameEngineTest.whitelistWinsOverAllBaitRules`. It configures one package as both bait and whitelisted and checks:

- the outcome is `Bypass`;
- health does not change;
- the random-roll counter remains zero;
- the tap is still recorded.

This proves the whitelist overrides the bait rule. It does not depend on Instagram or YouTube being installed.

### Add small repository/UI tests

- `setWhitelisted(pkg, true)` adds exactly that package to the saved set.
- `setWhitelisted(pkg, false)` removes it without changing other selected packages.
- Saving and recreating the game store retains the selected set.
- A list row uses the package name as its key and reflects the saved set.
- Switching a row on/off calls the callback with the same package and requested value.
- App discovery sorts labels, removes duplicate launcher activities, and excludes Taptation.
- Search matches either app label or package name.

Inject a fake app-list source for JVM/Compose tests. Do not make tests depend on whichever apps happen to be installed on a developer's machine.

### Manual emulator/phone check

To prove the whitelist has an effect, use a package that is explicitly configured as bait. The game's initial bait configuration is empty, so an unconfigured package may already get `NoPenalty` and cannot prove the whitelist works.

1. In the debug logic panel, set catch chance to 100% and add a chosen app package to **Bait apps**.
2. Turn that app on in the Whitelisted apps section.
3. Make the app post a notification and tap it.
4. Confirm the tap outcome is `Bypass`, health and catch count do not change, and no random roll is used.
5. Turn that same app off in the whitelist, leaving bait and probability unchanged.
6. Tap another auto-cancel notification from the same app. Confirm normal catch behavior now occurs.
7. Turn the app on again, restart Taptation, and confirm the switch remains on.
8. Swipe another notification away and confirm there is no tap event in either whitelist state.

Use a real notification posted by the selected app for the package-level check. Taptation's own test notification always belongs to `com.codejam.taptation` and cannot impersonate Instagram or YouTube.

## Done when

- The user can see and toggle installed launcher apps in one small section.
- The UI uses the existing `GameRepository` state and `setWhitelisted` API; there is no second settings store.
- The chosen app survives restart and always bypasses penalties when tapped.
- Removing the exemption restores the configured bait behavior for that package.
- Tests verify precedence, persistence, and simple switch wiring.
- The UI tells users that the exemption applies to every notification from the selected app.

## Implementation status (November 2026)

Implemented in the current app:

- `debug/WhitelistAppsPanel.kt` lists launcher apps, supports search and refresh, and toggles each package through `GameRepository.setWhitelisted`.
- `AndroidManifest.xml` declares visibility only for launcher apps; it does not request broad installed-app access.
- `debug/LogicDebugPanel.kt` contains the picker and keeps the manual package field only for the bait demo control.
- Saved whitelist entries for apps that are not installed remain visible with a remove action.
- `docs/app-whitelist-demo-explanation.md` contains a plain-language explanation and demo script.

The game-engine whitelist rule and persistence were already present before this UI change. The implementation relies on those existing rules, including bypass-before-bait behavior. Build/test status should be recorded separately after running the relevant Gradle commands.

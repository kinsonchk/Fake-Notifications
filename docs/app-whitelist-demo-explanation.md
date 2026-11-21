# App whitelist: implementation and demo guide

## What the feature does

A user can choose installed apps in **Whitelisted apps**. When a notification from one of those apps is tapped, Taptation records the tap as a bypass, but it does not roll for a catch, reduce health, or start a penalty. The Android notification still appears and opens its original app normally. The setting applies to every notification from that app.

## How we built it

1. **Reuse the saved game settings.** The selected app is stored by its Android package name in `GameConfig.whitelistedPackages`. The picker calls `GameRepository.setWhitelisted`; it does not make a second settings database. This keeps one reliable source of truth, and the choice survives app restarts.
2. **Show apps Android can launch.** The picker asks `PackageManager` for apps with a launcher icon, then displays their readable names and package names. Android 11 and later restrict app discovery, so the manifest declares only the launcher-app visibility it needs. It does not request access to every installed package.
3. **Apply the existing game rule.** `GameEngine` already checks the whitelist before bait matching and random chance. A selected package returns `Bypass (whitelisted)`, even if it is also marked as bait.
4. **Keep the demo control separate.** The debug panel still has a package-name field for marking a package as bait. That is useful for demonstrating that whitelist takes priority; the normal whitelist choice is the installed-app list.

The app picker is a small card in the existing debug/settings surface. It has search, refresh, save-pending behavior, and a remove option for a saved package that is no longer installed. No new library or network service is needed.

## Demo script

### Prepare

1. Install and open the debug build of Taptation on an emulator or phone.
2. Grant Taptation **Notification access** in Android Settings. The notification tap detector needs this permission.
3. In Taptation, choose an app that can post a notification and that you can make post one during the demo. Taptation's own test notification can verify tap detection, but its package is Taptation, so it does not imitate Instagram or YouTube.
4. In the debug logic controls, set **Catch chance** to 100%.
5. Put the chosen app's exact package name into **Bait app package name (demo control)** and tap **Mark bait**.

### Show that whitelist wins

1. Search for the app under **Whitelisted apps** and turn its switch on.
2. Make the chosen app post a notification, then tap that notification (do not swipe it away).
3. Return to Taptation. Show **Last tap: Bypass (whitelisted)**. Health and catch count should be unchanged.
4. Explain: “This notification still works normally. Taptation simply treats this app's tap as safe.”

### Show the effect of turning it off

1. Turn off the same app's whitelist switch. Leave it marked as bait and keep catch chance at 100%.
2. Make the app post another auto-cancel notification and tap it.
3. The normal bait rule should now apply: the result should be a catch (unless the tree is dead or the game is in lockout; reset health/state first if needed).
4. Turn whitelist back on, close and reopen Taptation, and show that the selection remains saved.

## What to say to a judge

“We added a simple app whitelist so people can protect important or distracting apps from the penalty mechanic. We show launchable apps by their real Android package names and save the choice in the same game settings already used by the rules. When a selected app's notification is tapped, the rule bypasses the random catch check before bait matching can cause damage. Notifications still work in Android as usual. The setting is app-wide, so every notification from that app is protected.”

## Expected result and limits

- Whitelisted tap: `Bypass (whitelisted)`; no random roll or health loss.
- Non-whitelisted bait tap at 100% chance: catch under the normal game rules.
- Swiping a notification away is not a tap and should not appear as a tap event.
- A package for an uninstalled app is retained until removed; it cannot generate taps while uninstalled.
- The picker lists apps that have launcher activities. An app without a launcher icon may not appear in this first version.
- This verifies the app-wide feature, not a per-notification or per-channel exception.

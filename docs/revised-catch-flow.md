# Initial implementation of the revised catch flow

This records the latest agreed product behavior and supersedes conflicting probability,
settings and lockout instructions in the older briefs.

## User-facing behavior

- Settings offers **Easy 20%, Medium 40%, Hard 70%**. No probability slider is shown there.
  Difficulty changes catch probability only. Catch damage is unchanged. Health regenerates at
  +5 per 60 clean minutes; only a successful Bait catch resets the timer. A missed Bait roll
  preserves partial progress. The debug panel can use 1/5/15-minute intervals; release uses 60.
- A caught notification creates one persistent pending choice: **Go to Taptation** or
  **Go to original app**. The choice acknowledges an already-applied catch and never applies
  damage again. The internal 30-second catch cooldown prevents repeated penalties; it is not
  a compulsory pause screen or a device-wide lock.
- With display-over-other-apps permission, background catches show an Android application
  overlay. In the foreground, the same two choices appear as a Compose dialog. Without
  overlay permission (or when adding the overlay fails), a notification opens the choice.
  Allow overlay access and, on Android 13+, notification permission in Settings > Caught feedback.
  Some apps/system surfaces may suppress overlays; no universal device-wide interception is promised.
- Original-app navigation uses the original notification action when it is still available.
  After a process restart, it falls back to opening the source app. If the app/action no longer
  exists, the choice is safely dismissed and the user is informed. A manual force-catch has
  no original notification action: choosing original simply dismisses the prompt.
- Disabling requires a **10-second monotonic wait plus explicit confirmation**. Choose 15
  minutes, 1 hour, or until enabled manually. Enabling is immediate. The repository enforces
  the wait and consumes the confirmation once; a UI countdown alone cannot bypass it.
- While disabled, listener metadata capture, new tap/dismiss recording, catch rolls, damage
  and clean-hour recovery stop. Existing history/settings remain. The OS notification-access
  grant is not revoked; the service remains bound so timed/manual activation can work.
- Freshness defaults to **10 minutes**. The integrated Settings control includes off, short
  demo windows and minute presets. A known age
  strictly greater than the window skips classification and the catch roll. Equality remains
  eligible. Stale actions can be recorded as unsure but do not reset the clean-hour timer.
  Legacy package-only calls retain the conservative metadata classifier behavior.
  Person 1's first-seen tracker is preserved: choose latest-update age (default), or age
  since the listener first observed that notification key. If first-seen time is unavailable,
  the rule falls back to update age.

## Statistics

The normal Stats screen now uses StatsRepository/SQLite, the same source used by the debug
harness. Seven-/30-day views cover today and the preceding six/29 local calendar days.

- Tap-to-dismiss percentage = taps / dismissals * 100. This ratio can exceed 100%.
- Catches per tap = catches / taps * 100.
- Average catches per day = catches / calendar days in the selected period, including zeros.
- Per-category tap/dismiss bars, counts, ratios and catches per tap are shown.
- Daily catch bars show the selected period. No taps/dismissals produces an empty state;
  a zero denominator is shown as a dash, not an invented percentage.
- Defaults remain bait-only; the include-important/unsure control broadens the history.
  Categories refer to notification categories, not the Important/Bait classifier label.
  Old records lacking category metadata stay in an unknown bucket instead of being guessed.
- Manual force-catch is a health/debug operation, not a notification tap, so it does not
  fabricate a tap in SQLite. Use 100% plus a simulated or real bait tap for end-to-end stats.

## Mockup styling

The native Compose screens now follow the revised mockup: a fixed cream/forest/sage palette,
serif headings, rounded white cards, compact bottom navigation, difficulty tiles and a tree
illustration. Dark mode uses the same green palette. Android wallpaper colors no longer override
the design. Home, Stats, Settings and both caught presentations use this styling; settings
sections open at the top and support Android Back. Debug controls remain accessible in debug builds.

Home now shows a seamless grass meadow whose height and density represent health. Brushing bends
the grass without changing game state. Health and actual activity remain below it. Stats continue to display real SQLite history and honest empty states.
`MockupScreensTest` exercises navigation and exports light/dark device screenshots into the test
app's external-files `ui-previews` directory (collect before the test APK is uninstalled).

## Debug and compatibility

Notifications and Debug tabs remain debug-only. The notification composer, presets, system
signals, force catch, health editing, fast-forward and history harness remain available.
The debug tab adds a 0–100% probability override, a clear-override action, and a stale-tap
scenario. Release GameEngine ignores debug probability overrides, even if one remains in
local storage after installing a different build. Selecting difficulty clears the override.

Game snapshot format v7 reads v1–v6, preserves health/history/rules/freshness, and maps old arbitrary
probabilities to the closest fixed difficulty. New activation, freshness, debug overrides,
pending-choice fields and recovery intervals are saved atomically with game state. Existing
v6 fields remain in their original order; v7 appends recovery interval and debug override fields. Notification actions/title/text
remain transient. SQLite v2 adds the category column with ALTER TABLE and preserves old rows.

## Verification

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest assembleRelease lintDebug lintRelease
```

Run device tests against a test phone/emulator. New device tests exercise the actual Android
overlay with temporarily granted overlay access, both foreground choices, confirmation UI,
and SQLite v1 migration. The overlay test uses isolated preferences, restores the overlay
app-op and verifies the original callback fires without a second health penalty.

On a demo phone: enable notification access; allow caught overlays; allow notification
fallback; set debug override to 100%; tap a posted social/promo test notification. Verify both
destinations, inactive behavior, stale-tap skip, and stats. Real-app notification metadata and
OEM/background behavior still require the existing phone checklist.

User-authorized cross-area work includes logic, service feedback, UI, debug support and tests.

## Meadow and notification-rule layout revision

The frontend uses functional Home / Health / Caught / Stats headings without eyebrow labels.
App rules follow the supplied notification-settings HTML layout: real launchable apps with a
three-choice selector on each row, followed by essential/non-essential fields and strictness.
Narrow layouts stack each selector below its app name; names and icons replace package IDs.
Changes use the existing GameRepository API and save automatically. Existing AI behavior is retained;
the attached feature brief's older judge fallback does not replace the backend.

The Home meadow uses stable blade positions, gentle sway and non-consuming brush gestures.
Height and density transition with health; animation pauses offscreen/in background and respects
Android's animation setting. Caught health uses a shared 1.15-second green/red/green event flash,
settling at recorded reduced health. Both Compose and native overlays retain the continuing penalty,
correction action and Not now. Deferred choices remain available without blocking navigation.
Statistics add a two-color opened/swiped split and inspectable daily counts, including zero days.

No engine, classifier, AI judge, persistence or statistics-calculation changes are part of this
frontend revision. Older branch controls for retired channel/keyword rules are no longer exposed.

Notification rules restores main's notification-permission filter on Android 13+: only permitted apps are shown. Disabled apps remain in the loaded inventory so saved rules are preserved and aren't mistaken for uninstalled apps. Older Android retains the existing all-apps fallback. The picker reloads on resume; the backend is unchanged.

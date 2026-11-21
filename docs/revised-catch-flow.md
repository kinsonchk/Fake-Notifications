# Initial implementation of the revised catch flow

This records the latest agreed product behavior and supersedes conflicting probability,
settings and lockout instructions in the older briefs.

## User-facing behavior

- Settings offers **Easy 20%, Medium 40%, Hard 70%**. No probability slider is shown there.
  Difficulty changes catch probability only. Existing health/damage rules remain unchanged;
  a graphic driven by time since catch, taps or dismissals has not been selected.
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
- Freshness defaults to **10 minutes**, configurable to 5/10/30/60 minutes. A known age
  strictly greater than the window skips classification and the catch roll. Equality remains
  eligible. Stale actions can be recorded as unsure but do not reset the clean-hour timer.
  Legacy package-only calls retain the conservative metadata classifier behavior.

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

## Debug and compatibility

Notifications and Debug tabs remain debug-only. The notification composer, presets, system
signals, force catch, health editing, fast-forward and history harness remain available.
The debug tab adds a 0–100% probability override, a clear-override action, and a stale-tap
scenario. Release GameEngine ignores debug probability overrides, even if one remains in
local storage after installing a different build. Selecting difficulty clears the override.

Game snapshot format v4 reads v1–v3, preserves health/history/rules, and maps old arbitrary
probabilities to the closest fixed difficulty. New activation, freshness, debug override and
pending-choice fields are saved atomically with game state. Notification actions/title/text
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

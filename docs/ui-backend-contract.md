# UI ↔ backend contract

**Rule:** screens render backend state and send user intents. Every decision lives in the backend
(`logic/`, `service/`, `stats/`, `ai/`); the backend never imports `ui/`. `TaptationApplication` is
the composition root and the only file that sees both sides. The short version is in
[CLAUDE.md](../CLAUDE.md#ui--backend-contract-read-before-touching-ui-or-the-backend).

All objects below come from `(context.applicationContext as TaptationApplication)`.

## What screens read

| Need | API | Notes |
|---|---|---|
| Game state, errors, last tap | `game.state`, `game.error`, `game.lastResult` | Health recovers on its own (the repository schedules its refresh); screens don't poll. |
| Caught dialog / overlay content | `feedback.prompt: StateFlow<CatchPrompt?>` | App label, `details` (health before/after, damage, rule, channel), `continueExtraDamage`, `postponed`, `animate` (first showing anywhere), `canCorrect`. Recorded on `PendingCatch` at catch time. |
| "This should be important" sheet | `feedback.corrections: StateFlow<CorrectionRequest?>` | Package, app label, channel. |
| Notification rules list | `rulePicker: StateFlow<RulePicker>` | Eligible apps (launchable, not Taptation, allowed to notify on Android 13+) with their rule, `missingRules` (uninstalled), `notificationFilterSupported`, `search(query)`. |
| App names and icons | `apps.label(pkg)`, `apps.icon(pkg)` (via `AppLabels` / `AppIcon` in `ui/common`) | `apps.apps` is the raw launchable list; `apps.reload()` on demand (also done on every resume). |
| Permissions | `system.permissions: StateFlow<PermissionStatus>` | Notification access, overlay, post-notifications, battery, `notificationPromptAvailable`. |
| Home status | `system.monitoring: StateFlow<MonitoringStatus?>` | Setup needed / paused / connection interrupted / monitoring. |
| AI judge available | `judges.isConfigured` | Never read `BuildConfig.CF_*` in screens. |
| Stats | `stats.weekly` / `stats.monthly`, `stats.changes` | |
| Meadow | `meadow.state`, `meadow.error` | Separate saved progression; health history and monitoring determine earned growth. See [living meadow](living-meadow.md). |
| Appearance | `appearance.mode`, `appearance.error` | Dark by default; separately saved Dark/Light choice. MainActivity and the native overlay use the same preference. |
| Allowed values | `GameConfig.FRESHNESS_CHOICES_MINUTES`, `GameConfig.DEFAULT_FRESHNESS_SECONDS`, `DisableGate.DURATION_CHOICES_MINUTES`, `DisableGate.DEFAULT_DURATION_MINUTES`, `DisableGate.WAIT_MILLIS` | Screens only word them. |
| Derived values | `game.recoveryIntervalMinutes(config)`, `config.currentDamage`, `config.countOf(rule)`, `config.freshnessEnabled`, `game.disableWaitRemainingMillis(challenge)` | |

## What screens call

| User intent | API |
|---|---|
| Catch choice | `feedback.actionsFor(catchId)`: `choose(ourApp)`, `postpone()` ("Not now"), `correct()`; `feedback.resume(catchId)` ("Choose now") |
| Correction confirmed | `game.markAppImportant(pkg)` |
| Difficulty | `game.setDifficulty(d)` (also drops the debug catch chance) |
| Ignore older notifications | `game.setFreshnessEnabled(b)` (restores the default), `setFreshnessMinutes(m)` (checked against the choices), `setFreshnessFromFirstSeen(b)` |
| Let us decide answers | `game.setCriteria(essential, nonEssential)` (cut to the max length), `game.setStrictness(s)` |
| App rule | `game.setAppRule(pkg, rule)`, `game.clearAppRule(pkg)` |
| Meadow discovery | `meadow.discover(discovery)` | Wind, rain and creature reactions are temporary UI effects; they never award health or growth. |
| Retry meadow load/save | `meadow.retry()` | A failed save retains the last successfully saved state. |
| App appearance | `appearance.setAppearance(mode)` | Screens never read/write appearance preferences directly. |
| Pause / resume | `game.beginDisable()`, `confirmDisable(id, minutes)`, `cancelDisable()`, `enable()` |
| Open Android settings | `PermissionHelper.openNotificationAccessSettings`, `openOverlaySettings` (or `ui/common/openOverlaySettings`, which adds a message), `openAppNotificationSettings`, `openBatteryOptimizationSettings` |
| Notification prompt shown | `system.markNotificationPromptShown()` |

`game.updateConfig { copy(…) }` stays for debug panels only.

## Done (from the November 2026 audit)

| Was in the UI | Now |
|---|---|
| `ui/common/InstalledApps.kt` and `AppLabels` queried the package manager, filtered by notification permission, excluded Taptation (two copies) | `service/AppDirectory` (one loader) + `logic/RulePicker`; UI helpers delegate |
| Notification rules page decided eligibility, orphaned rules and the SDK 33 wording | `rulePicker` |
| `catchDetailsForUi` parsed the catch id to find its tap; the unused `CatchDetails.from` could show an older catch from another app | Details recorded on `PendingCatch` at catch time (snapshot v8), `CatchDetails.of`, `feedback.prompt` |
| `service/CatchFeedback` imported `ui.common` / `ui.theme` and built the overlay View tree | Backend `CatchFeedback` + `CatchSurface`; rendering in `ui/overlay/CatchOverlay` (same look) |
| "Not now" and "animate once" were tracked separately by the app and the overlay | `feedback.postpone/resume`, `CatchPrompt.postponed` / `animate` |
| Correction sheet chose the rule itself and looked up the app name | `CorrectionRequest.appLabel`, `game.markAppImportant` |
| Home, Reminders read notification access / overlay / notification permission and stored "asked" in SharedPreferences | `system.permissions`, `system.monitoring`, `markNotificationPromptShown` |
| Settings wrote `updateConfig { copy(...) }` with UI-chosen rules (clear debug chance, default 600, allowed time limits) | Typed actions + constants |
| Pause dialog repeated 15/60 min and the 10 s wait | `DisableGate` constants |
| Rules page read `BuildConfig.CF_*`; Home read `BuildConfig.DEBUG` for the recovery interval | `judges.isConfigured`, `game.recoveryIntervalMinutes` |
| `rememberNow` refreshed the game on resume | `TaptationApplication` refreshes game, permissions and apps on every activity resume |

## Still to do

- **Pause flow:** `rememberPauseFlow` still runs the begin/confirm/cancel sequence and drops errors;
  the countdown reads `SystemClock` (use `game.disableWaitRemainingMillis`). Better: a backend
  `disableFlow: StateFlow<DisableFlowState>`.
- **Rule codes are strings:** `TapRecord.rule` / `reason` codes are matched as text in
  `TapExplanations`; a typed enum would stop a new code silently printing raw. `catchReason` has no
  case for `ai_bait` / `let_us_decide` (generic sentence shown).
- **Stats text in the backend:** `stats/StatsText` holds display names and sentences; move the
  wording to `ui/stats` and keep numbers (`opensPerDismissal`, emptiness) in `StatsSummary`.
  `StatsScreen` computes "no activity" differently from `StatsText.emptiness`.
- **Tutorial flag:** `OnboardingScreen` stores "tutorial complete" in its own SharedPreferences (the
  screen currently has no caller).
- **Ineffective or dead logic (delete or rebuild):** `logic/RuleChoices` writes
  `whitelistedPackages` / `baitPackages` / channel sets that the tap path no longer reads (per-app
  rules are `appRules`); `logic/ObservedTypes` is never recorded or persisted; unused screens
  `SafeAppsPage` (reads the legacy `whitelistedPackages`), `RulesPage` helpers, `OnboardingScreen`,
  `rememberReminderPermissions`, `HomeScreen` `Step` / `SetupRow`, `TreeIllustration`.
- **Debug screens** (`debug/`) are UI too: `TapRecordRow` shows raw package names.

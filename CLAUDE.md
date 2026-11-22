# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## UI design contract

Before creating or changing any UI, read [docs/UI_DESIGN_GUIDE.md](docs/UI_DESIGN_GUIDE.md)
and the nearest existing screen. Follow its visual tokens, component recipes and definition
of done unless the user explicitly requests a different design. No eyebrow labels; prioritize the supplied layouts and functional headings. Reuse the shared theme and
layout components; inspect actual Android renders before claiming visual verification.
The guide includes portable reference screenshots and a ready-to-use prompt for future agents.

## Project

Latest agreed revision: read [docs/revised-catch-flow.md](docs/revised-catch-flow.md) before
working on settings, catch feedback, activation or statistics. It supersedes older probability
and compulsory-lockout instructions here: public difficulties are Easy/Medium/Hard 20/40/70%,
debug overrides are separate, activation has a repository-enforced 10-second confirmation,
known stale taps skip classification/roll, and catches present two destination choices.
Home now uses a grass meadow driven by health. Touch affects appearance only. The user authorized this frontend revision across owners.

**Fake Notifications** (Gradle project `Taptation`, package `com.codejam.taptation`): 1-day hackathon Android app, team of 4. App watches other apps' notifications; tapping a distracting notification may catch the user, lowering health and starting a 15-second catch cooldown. Urgent apps (family, work, rides, banking) can be set to Always important and are never penalized.

Planning docs live in `docs/`, one folder per person (`docs/person-1` … `docs/person-4`), each with a `README.md` listing that person's docs, status and remaining work. Read the relevant one before working on a teammate's area:
- `docs/features-by-person.md`: core vs priority features per person, shared interfaces, go/no-go checkpoint (tap detection on a real phone by hour 4), cut list.
- `docs/classification-and-settings-brief.md`: classification rules (Section A, Person 2), settings page (Section B, Person 3), open questions (Section C). Conflicts with the current code: see "Brief vs current code" below.
- `docs/person-1/notification-service-brief.md`: Person 1 brief and acceptance criteria.
- `docs/person-2/PERSON_2_INTEGRATION.md`: exact game rules, defaults, Person 1 contract, Person 3 API.
- `docs/person-4/testing-plan.txt`: layered test plan, debug-menu wishlist, end-to-end demo checklist, edge cases.

Kotlin + Jetpack Compose, single `:app` module, version catalog in `gradle/libs.versions.toml`, minSdk 26, compile/target SDK 37, AGP 9.x.

## Commands

On Windows use `.\gradlew.bat` in PowerShell, or `./gradlew` in Git Bash.

```bash
./gradlew assembleDebug                 # build
./gradlew installDebug                  # build + install on connected phone
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew testDebugUnitTest --tests "com.codejam.taptation.service.TapDetectionTest"   # single test class
./gradlew connectedDebugAndroidTest     # instrumented tests (needs device)
adb logcat -s FakeNotifs                # all app log lines use tag FakeNotifs
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.codejam.taptation.ai   # live AI judge tests (need the keys below)
adb shell cmd notification allow_listener com.codejam.taptation/com.codejam.taptation.service.TapListenerService   # grant notification access without UI
```

AI judge keys: put `cloudflare.accountId` and `cloudflare.apiToken` (Workers AI Read + Edit) in the git-ignored `local.properties`; Gradle exposes them as `BuildConfig.CF_ACCOUNT_ID` / `CF_API_TOKEN`. Without them the app builds and "Let us decide" stays bait. The token ships inside the APK (fine for the demo; free plan can't be billed). Model comparison (opt-in, spends quota): `JudgeModelComparisonTest` with `-Pandroid.testInstrumentationRunnerArguments.judgeBenchmark=true`.

Real verification needs a phone: see README "Verify" steps (post test notification, tap vs swipe, simulate tap).

## Architecture

Tap pipeline:

```
system -> TapListenerService.onNotificationPosted   (cache NotificationContext by key, 500-entry in-memory LRU)
       -> JudgeQueue.onPosted                       ("Let us decide" apps only: Clef verdict on its own thread, memory-only by key)
       -> TapListenerService.onNotificationRemoved(sbn, rankingMap, reason)
       -> TapDetection.shouldDispatch(...)          (pure filter)
       -> TapLog.record(pkg)                        (in-memory last 20, StateFlow, debug screen)
       -> context.judged = JudgeQueue.classificationFor(context)
       -> TapDispatcher.dispatch(NotificationContext) -> TapDispatcher.handler.onTap(context)
          (swipe / clear-all: TapDispatcher.dispatchDismiss -> handler.onDismiss, stats only)
       -> GameRepository (installed by TaptationApplication.onCreate)
       -> single-thread worker -> GameEngine.onTap -> PreferencesGameStore
       -> StatsRepository.submit(StatsEvent) -> SqliteStatsStore (stats.db, one row per tap/dismissal)
```

- `logic/TapHandler.kt`: the **shared contract**. `TapHandler.onTap(packageName)` plus `onTap(context: NotificationContext)` (defaults to the package-only one); `TapDispatcher` holds a `@Volatile` handler with both `dispatch` overloads. Package-only calls stay supported.
- `logic/NotificationContext.kt`: `NotificationContext` (packageName, key, title, text, notification category, appCategory, isConversation, channelName, ageMillis, firstSeenAgeMillis, action button titles). Transient: never persisted or logged.
- `logic/GameModels.kt`: `GameConfig`, `GameState`, `TapOutcome` (`Bypass` / `NoPenalty` / `Caught`), `CatchLevel`, `AppRule`, `Strictness`.
- Per-app rules (`feature-per-app-rules.md`, mockup `notification-settings.html`): `GameConfig.appRules` stores only changed apps; `ruleFor(pkg)` defaults to `LetUsDecide`. `NotificationClassifier.assess` is the tap path: `AlwaysImportant` -> Important (`app_always_important`, bypass); `AlwaysBait` -> Bait (`app_always_bait`, guaranteed catch without AI or probability roll on an eligible tap); `LetUsDecide` -> the AI verdict in `NotificationContext.judged`: Important (`ai_important`), Bait (`ai_bait`), Unknown when no result is available (`ai_request_failed`, `ai_pending_timeout`, `ai_not_configured`, no penalty). Only a LetUsDecide Bait verdict uses the selected catch probability. The existing stale, paused-game and catch-lockout checks still apply. With a usable Clef score, everything below the selected Bait cutoff is Important; at or above it is Bait. The old ordered rules (whitelist, category, VIP, keywords, channels, profiles) are kept as `assessByRules`, off the tap path. Codec v6 migrates old `whitelistedPackages` / `baitPackages` into `appRules`. UI: Settings > Notification rules (`ui/settings/NotificationRulesScreen.kt`, installed apps from `ui/common/InstalledApps.kt`; on Android 13+ only apps whose POST_NOTIFICATIONS permission is granted, i.e. notifications on in system settings; apps with notifications off are hidden even if they have a rule).
- `ai/`: the "Let us decide" judge. `ClefJudge` (default) asks Cloudflare Workers AI's Clef decision model one typed choice question (`ClefQuestion`: important vs bait, the user's essential / non-essential answers appended to each option) and maps the bait probability using strictness thresholds (Relaxed 0.85, Balanced 0.7, Strict 0.55). Any usable score below the selected threshold is Important; scores at or above it are Bait. `ChatJudge` (Llama 3.1 8B, one-word answer, `JudgePrompt`) is the alternative behind the same `NotificationJudge` interface. `WorkersAi` is the HTTP client (`HttpURLConnection`, no extra dependency). `JudgeQueue` (in `TaptationApplication.judges`) keeps countable notifications as candidates (memory only, by key) and judges "Let us decide" ones; it re-judges when title/text change or when app rules, criteria or strictness change (`TaptationApplication` forwards config changes to `onConfigChanged`, which also judges notifications posted while the game was loading), skips queued requests that became outdated before calling the network, drops late answers, and re-judges active notifications after the listener reconnects. `judgeNow` judges synchronously for the debug "Simulate tap". Sends package, title, text (each clipped to 300 chars), channel, category, conversation flag and button titles to Cloudflare; never logs or stores them. Notifications with no title and no text are treated as Bait without a call. An unavailable model or failed request is Unknown and never penalizes a tap. Free plan: 10,000 Neurons/day, about 1,100 Clef calls; beyond that calls fail and return no-penalty Unknown.
- `logic/GameEngine.kt`: pure, single-thread-confined rules (classification, roll, damage, recovery, lockout, death/reset). Clock and random are injected, so it's JVM unit-testable.
- `logic/GameRepository.kt`: the real `TapHandler`. Serializes every operation on one worker thread (no disk I/O on listener callbacks), resolves `ApplicationInfo.category`, exposes `state` / `error` / `lastResult` StateFlows and `CompletableFuture` mutations. Never `.get()`/`.join()` on the UI thread. Recovery is computed lazily by `refresh()`, so the repository schedules its own refresh at `GameEngine.nextScheduledChangeMillis()` (next recovery step or end of a timed disable), re-armed after every operation: `state` stays current on every tab without screens polling. `rememberNow` is only a 1 s display clock plus an on-resume refresh (the scheduler's clock pauses in deep sleep).
- `logic/PreferencesGameStore.kt`, `logic/GameStateCodec.kt`: versioned, atomic local snapshots. `GameStateCodec.Layout` lists which optional parts each version has; v6 was written in three layouts by parallel branches (`V6_RULES`, `V6_PENALTY`, `V6_MERGED`) and all are tried on load. **Never reuse a version number across branches**: bump it, add a `Layout`, and test old snapshots with `GameStateCodec.encode(state, Layout.X)` (no byte-cutting). An unreadable snapshot no longer bricks the engine: `PreferencesGameStore.load` keeps it under `snapshot_unreadable` and starts a fresh game.
- Debug-only config fields (`debugCatchProbability`, `debugRecoveryIntervalMinutes`) are stripped in release by `GameRepository.updateConfig`; add any new debug field there too. Forced (debug) catches and tap catches build the same `PendingCatch` (same id scheme and `CONTINUE_EXTRA_DAMAGE`).
- Stale notifications: `NotificationClassifier.assessTap` (Unknown `stale` when older than the freshness window, else `assess`) is used for both taps and dismissals so stats compare like with like; don't call `assess` directly on the tap/dismiss path.
- `TaptationApplication.kt`: creates the single `GameRepository` and sets `TapDispatcher.handler`, so it works even when only the service process starts. UI gets it via `(context.applicationContext as TaptationApplication).game`.
- `service/TapDetection.kt`: pure filter logic, framework constants only, so it runs in plain JVM tests. Keep new decision logic here, not in the service. `isCountable` (no group summary, no ongoing, own package only on the debug test channel) is shared by removals and by AI judging, so ongoing/summary/own notifications never spend judge quota.
- `TapListenerService`: `skipWhileDisabled` skips only when a loaded config says the app is disabled; while the game is still loading (state null) callbacks go through and `GameRepository` queues taps behind its load. The context/first-seen caches are access-ordered (`LinkedHashMap(…, true)`), so long-lived notifications aren't evicted first.
- `service/NotificationSignals.kt`: builds the metadata in the `posted`/`removed` Logcat lines (category, channel, importance, conversation flags, auto-cancel, postTime/age, `media=`). Logging only; never title/text.
- `service/ConversationDetection.kt`: `NotificationContext.sender` / `isGroup` for chat notifications (MessagingStyle or category `msg`): the MessagingStyle message's sender and group flag, else the "Name:" prefix of a group line, else the title (Discord and Instagram DMs put the user in the title; checked on a real phone). Personal data: transient, never logged; sent to the AI judge as `sender` / `group_chat`. The debug composer's "Chat sender" and "Group chat" fields post a real MessagingStyle chat.
- `service/MediaDetection.kt`: `NotificationContext.media` (`MediaKind`: Image, Gif, Video, Sticker, Audio) from, in order, the MessagingStyle message's `dataMimeType`, a BigPictureStyle picture, or the app's placeholder text ("📷 Photo", "GIF", "sent you a photo", "a envoyé une photo"; whole last line or a "sent a …" phrase, so ordinary sentences mentioning a photo don't count). Only the kind is known; the attachment is never opened (its URI is granted to the system UI, not to listeners). Sent to the AI judge as `attachment`. Best effort per app: check `media=` in the `posted` log line on a real phone.
- `service/PermissionHelper.kt`: notification-access check/settings intents and battery-optimization helpers; reuse for onboarding UI.
- Onboarding (`ui/OnboardingScreen.kt`): the tutorial shows once; only notification access is required to reach the tabs. "Display over other apps" is recommended but optional (`CatchFeedback` falls back to a notification). Open that settings page only through `ui/common/openOverlaySettings` (some ROMs lack the screen; raw `startActivity` crashes).
- `service/CatchFeedback.kt`: the catch flow's backend. Builds `prompt: StateFlow<CatchPrompt?>` from the pending catch (details recorded on `PendingCatch` at catch time, snapshot v8; no more reconstructing from history), decides overlay vs notification vs in-app dialog, owns "Not now" (`postpone` / `resume`), the animate-once flag and the correction request. `service/AppDirectory.kt`: the only package-manager reader (launchable apps, labels, icons). `service/SystemStatus.kt`: permissions and `MonitoringStatus`.
- `ui/TaptationApp.kt`: bottom tabs Home · Stats · Settings · Debug (Debug only in debug builds); notification test tools, observed notifications, and tap history are inside the Debug tab. `rememberSaveableStateHolder` keeps each tab's state. `ui/home`, `ui/stats`, `ui/settings` read/write `GameRepository`; `ui/common` holds layout helpers, `rememberNow` (lockout ticker + periodic `refresh`) and `TapExplanations` (rule/outcome codes → text).
- `debug/`: `DebugScreen` (system status, `DemoControlsPanel` backed by `RepositoryDemoControls`, and the entry into `NotificationsScreen`), `NotificationsScreen` (composer, saved `NotificationPreset`s with an image in `NotificationIcons`, last 20 taps with reasons, observed notifications with a live classifier preview), `TestNotification` posts an auto-cancel notification with a contentIntent so tapping it yields `REASON_CLICK`; the composer's Attachment chips post a MessagingStyle message with a real attachment (`DebugMedia`, served by a FileProvider declared only in `src/debug/AndroidManifest.xml`; NotificationManager rejects data URIs the poster can't grant). Presets hold only user-typed values; observed notifications stay memory-only.
- `stats/`: `StatsRepository` (built in `TaptationApplication`, `app.stats`) records taps and dismissals with classification, caught and age in `SqliteStatsStore`; `weekly()` / `monthly()` return a precomputed `StatsSummary` (`StatsCalculator`: tap rate %, medians, averages capped at 1 h, bait only by default). See `docs/decision-stats-handler.md`. Debug tab > Stats harness generates fake history.
- `TapRecord.rule` / `TapResult.rule`: the classifier rule behind the classification (`vip_title`, `bait_category`…); `reason` is the final decision (`caught`, `roll_passed`…). Taps stored before codec v3 have an empty rule for bait.
- `service/DebugSimulation.kt`: package/channel name/conversation/app category overrides carried in the extras of our test-channel notification. The listener applies them only in debuggable builds and only on `OWN_TEST_CHANNEL_ID`, so a debug notification can stand in for another app.

Full rules, defaults and the Person 3 API are in `docs/person-2/PERSON_2_INTEGRATION.md`. Key gotchas from it (some superseded by per-app rules: every app now starts on "Let us decide", which follows the AI verdict; unavailable AI results cause no penalty); `Caught` already applied damage, so UI must not call `applyCatch` again; notification title/text are transient and must never be persisted or logged.

## UI ↔ backend contract (read before touching ui/ or the backend)

The UI only **renders backend state and sends user intents**. Every decision (eligibility, allowed
values, defaults, what a catch cost and why, permission/access status, Android system queries,
persistence, timing) lives in the backend (`logic/`, `service/`, `stats/`, `ai/`). Full API list
and what is still to move: [docs/ui-backend-contract.md](docs/ui-backend-contract.md).

- **Backend never imports `ui/`.** The one UI piece the backend needs (the caught overlay drawn over
  other apps) is behind the `service.CatchSurface` interface, implemented by `ui/overlay/CatchOverlay`
  and wired in `TaptationApplication` (the composition root, the only file allowed to see both sides).
- **UI never calls Android for app/permission state** (PackageManager, `Settings.canDrawOverlays`,
  NotificationManager, SharedPreferences): it collects the backend flows below. It may start Android
  screens the user asked for through `PermissionHelper.open…`, and run the POST_NOTIFICATIONS prompt
  (needs an activity), reporting it with `system.markNotificationPromptShown()`.
- **UI never edits `GameConfig` directly** (`updateConfig { copy(…) }` is for debug panels): it calls
  the typed actions, which own the rules (allowed values, defaults, what a choice also resets).
- **What the UI reads** (all via `(context.applicationContext as TaptationApplication)`):
  `game.state` / `error` / `lastResult`; `feedback.prompt` (`CatchPrompt`: app label, cost, rule,
  continue penalty, postponed, animate, canCorrect) and `feedback.corrections`; `rulePicker`
  (`RulePicker`: eligible apps with their rule, orphaned rules, filter supported); `apps` (labels,
  icons, `reload()`); `system.permissions` and `system.monitoring`; `judges.isConfigured`;
  `stats.weekly/monthly`; constants `GameConfig.FRESHNESS_CHOICES_MINUTES`,
  `DisableGate.DURATION_CHOICES_MINUTES` / `WAIT_MILLIS`; `game.recoveryIntervalMinutes(config)`,
  `config.currentDamage`, `config.countOf(rule)`.
- **What the UI calls**: catch choice through `feedback.actionsFor(id)` (`choose`, `postpone`,
  `correct`) and `feedback.resume(id)`; `game.setDifficulty`, `setFreshnessEnabled`,
  `setFreshnessMinutes`, `setFreshnessFromFirstSeen`, `setCriteria`, `setStrictness`, `setAppRule`,
  `clearAppRule`, `markAppImportant`, `beginDisable` / `confirmDisable` / `cancelDisable` / `enable`.
- **UI keeps**: layout, colours, animation, wording (mapping backend codes such as rule codes to
  text in `TapExplanations`), view state (tab, search text, expanded sections, chart selection), a
  1 s display clock (`rememberNow`), saving/error indicators.
- **Missing something?** Add a backend API (a field on a state class, a typed action, a constant)
  and use it from the screen; don't compute it in the screen. Refreshing on resume is already done
  by `TaptationApplication` (permissions, installed apps, game recovery).

## Shared interfaces (agreed between teammates)

- `onTap(context: NotificationContext)` (and legacy `onTap(packageName)`): Person 1 -> Person 2.
- Backend → UI (Person 3): the contract above. Storage is owned by the backend and read through `GameRepository.state`; settings screens write through the typed actions, not `updateConfig`.

## Brief vs current code

`docs/classification-and-settings-brief.md` was written after the first logic version and disagrees with it. Until the team decides, the code and `PERSON_2_INTEGRATION.md` are the source of truth; don't silently change one to match the other.

| Topic | Brief | Code on `main` |
|---|---|---|
| Catch probability default | 10% | Easy 20%; Medium 40%; Hard 70% |
| Damage Mild/Medium/Savage | 5 / 10 / 20 | 10 / 20 / 35 |
| Default level | Medium | Mild |
| Bypass rules | whitelist, call/alarm/reminder/event, VIP title, urgent keyword | only apps set to Always important (`appRules`); the old rules live in `assessByRules`, off the tap path |
| Urgent keywords default | urgent, emergency, code, verification | urgent, code, meeting invite, meeting invitation |
| Unmatched notification | bait (A3 rule 6) | apps on "Let us decide" (the default) follow the Clef verdict; no AI configuration yields no automatic penalty |
| Missing title or text | not covered | "Let us decide" with no title and no text is treated as Bait without making a model call; `assessByRules` still returns `Unknown` |
| Freshness window | 10 min, no roll if older | `freshnessSeconds` default 600 (0 = off); stale ages skip classification/roll. Optional `freshnessFromFirstSeen` uses the original observation instead of latest update |
| Health at 0 | patch resets | stays dead (no recovery) until UI calls `reset()`; every bait tap is caught at 100% without a roll or damage, with the lockout and choice overlay (continue penalty capped at the health left, so 0) |
| Tap interface | `onTap(packageName, ageMillis)` | `onTap(NotificationContext)` with `ageMillis` set by the service; `ResultTapHandler.onTap(packageName, ageMillis)` also exists |
| Settings storage | new `SettingsRepository` + DataStore | `GameConfig` in `GameRepository` (SharedPreferences), incl. `appRules`, the two answers, `strictness`; `setAppRule` / `setWhitelisted` write `appRules` |
| compile/target SDK | 36 | 37 |

Brief C2 asks Person 1 to "log" titles: show them on the debug screen from memory only; the never-log/never-persist rule for title/text still applies.

## Tap detection rules (don't regress)

- Count a removal as a tap **only** when `reason == REASON_CLICK`. Swipe (`REASON_CANCEL`) and clear-all (`REASON_CANCEL_ALL`) are dismissals: stats only, never a game effect. App-cancel, group-summary-cancel and every other reason are ignored.
- Skip notifications with `FLAG_GROUP_SUMMARY` or `FLAG_ONGOING_EVENT`.
- Own package counts only in debuggable builds **and only on the test channel** (`TapDetection.OWN_TEST_CHANNEL_ID`, used by the in-app test notification); ignored in release. Any other notification we post (e.g. a "you got caught" alert) must never count, or tapping it would trigger another catch in a loop.
- Override only the **three-argument** `onNotificationRemoved`. The base three-arg version forwards to the one-arg one, so overriding both double-handles.
- Android can hold **two connections** to the listener (a rebind racing the system's own reconnect; seen on a Pixel 10: two `INotificationListener` proxies in `dumpsys notification`), delivering every callback twice. `DuplicateEventFilter` (in `TapDetection.kt`) drops a second posted/removed with the same key and postTime within 2 s; keep it first in both callbacks. The system log shows Android itself binding twice during an app update (`NotificationListeners.onPackagesChanged`), not our `requestRebind`; `onListenerConnected` collapses it once per process (a second connect while connected -> `requestUnbind()`, then `onListenerDisconnected` requests a single rebind).
- Listener callbacks run on the main thread: keep them to filter + log + dispatch. `TapHandler` implementations must move disk/network work off-thread (`GameRepository` does this with its worker).

## Platform gotchas

- `REASON_CLICK` fires only for notifications that auto-cancel on tap; ones that stay after tapping produce no event.
- `sbn.postTime` restarts on every update (same key re-posted, e.g. a new chat message): AOSP builds a new `StatusBarNotification` with `System.currentTimeMillis()`; the first post time stays inside the system and isn't given to listeners. `ageMillis` is therefore time since the newest update; `firstSeenAgeMillis` is our own first-seen time per key (lost if the listener restarts).
- `adb shell cmd notification post` notifications have no contentIntent, so they can't test taps (`docs/person-4/testing-plan.txt` suggests them; fine for testing posting/logic, not tap detection). Use the in-app test notification, a real app, or a helper app with real contentIntents.
- Notification access can't be granted from code (settings toggle or the adb command above).
- Android 13+ sideloaded APKs: access toggle may be greyed out until App info > ⋮ > Allow restricted settings.
- Battery optimization can kill the listener (non-Pixel especially): app should be set to Unrestricted.
- Instrumented UI tests that render `TaptationApp` must get past onboarding first: call `completeTutorial(context)` and grant the listener with `cmd notification allow_listener …` (read the shell output so it finishes) in `@Before`, and wait for Home (`waitUntil`) since the first test after a cold start renders while the game loads. Look up UI text through string resources (`R.string.caught_*`), not literals; the copy changes.
- UI tests time out with a blank screen when the emulator screen is asleep (the activity never reaches ON_RESUME, so `TaptationApp` never checks access): run `adb shell input keyevent KEYCODE_WAKEUP` and `adb shell svc power stayon true` first. With a phone also plugged in, `installDebug`/`connectedDebugAndroidTest` target every device; use `adb -s emulator-5554 …` (or unplug) to keep tests off the phone.
- `am instrument` (and `connectedDebugAndroidTest`) kills the app process when it ends; the listener reconnects only when something starts the app again, and notifications already in the shade are judged on reconnect.
- Git Bash on Windows rewrites Unix-looking paths in arguments (`adb shell cat /sdcard/x` becomes `C:/Program Files/Git/sdcard/x`): set `MSYS_NO_PATHCONV=1` for adb commands with device paths. To tap a notification from adb: `cmd statusbar expand-notifications`, `uiautomator dump /sdcard/ui.xml`, read the node bounds, `input tap x y`. Several notifications from one app get auto-grouped and a tap may hit the group summary (`GROUP_SUMMARY_CANCELED`, not a click), so post one at a time.

## Team ownership

Each person works on their own branch. The user requested direct pushes/merges without PRs.

| Area | Owner |
|---|---|
| `service/`, `debug/DebugScreen.kt`, `debug/NotificationsScreen.kt`, `debug/TestNotification.kt` | Person 1 (notification service) |
| `ai/`: "Let us decide" AI judge (Cloudflare Clef) | Person 1 |
| `logic/`, `TaptationApplication.kt`: whitelist, classification, roll, health, storage | Person 2 |
| `ui/`: onboarding, tree/pet visuals, lockout, stats, real navigation in `MainActivity` | Person 3 |
| `debug/` extensions, adb scripts, demo | Person 4 |

Coordinate cross-area edits unless the user explicitly authorizes them (as for the revised catch flow). Out of scope for the service: cancelling/snoozing other apps' notifications. Don't add dependencies beyond the standard Compose/AndroidX set unless essential.

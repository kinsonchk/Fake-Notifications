# Developer guide

How to build, install, grant access, verify taps on a phone and read the logs. Architecture and
coding rules are in the root [CLAUDE.md](../CLAUDE.md); game rules are in
[logic/game-rules.md](logic/game-rules.md); the latest agreed behaviour is
[revised-catch-flow.md](revised-catch-flow.md). UI work follows [UI_DESIGN_GUIDE.md](UI_DESIGN_GUIDE.md).

Android app (launcher name **Taptation**, Gradle project `Taptation`, package
`com.codejam.taptation`) that watches other apps' notifications and may "catch" the user when they
tap a distracting one: health drops and a 15-second catch cooldown starts. Apps set to
**Always important** are never penalized.

## Main files

| File | What it does |
|---|---|
| `service/TapListenerService.kt` | `NotificationListenerService`. Caches each notification's context, dispatches only `REASON_CLICK` removals as taps; swipes and clear-all are dismissals (stats only) |
| `service/TapDetection.kt` | Pure filters (`isTap`, `shouldDispatch`, `isCountable`, `DuplicateEventFilter`), unit tested |
| `service/NotificationSignals.kt` | Metadata for the `posted`/`removed` Logcat lines (never title/text) |
| `service/CatchFeedback.kt` | Catch prompt state, overlay vs notification vs in-app dialog, "Not now", corrections |
| `service/PermissionHelper.kt`, `service/SystemStatus.kt` | Notification access, overlay and battery checks and settings intents |
| `ai/` | "Let us decide" judge: `JudgeQueue`, `ClefJudge` (Cloudflare Workers AI Clef), `WorkersAi` HTTP client |
| `logic/TapHandler.kt` | `TapHandler` contract and `TapDispatcher` |
| `logic/NotificationClassifier.kt` | Per-app rule, then AI verdict; stale check |
| `logic/GameEngine.kt` | Pure rules: roll, damage, recovery, lockout, death/reset |
| `logic/GameRepository.kt` | Single worker thread, observable state, typed settings actions |
| `logic/PreferencesGameStore.kt`, `logic/GameStateCodec.kt` | Versioned, atomic local snapshots |
| `stats/` | SQLite event store and weekly/monthly summaries |
| `TaptationApplication.kt` | Composition root: installs the real handler even when only the service process starts |
| `ui/` | Onboarding, Home meadow, Stats, Settings, caught overlay |
| `debug/` | Debug-only tab: system status, demo controls, notification tools, stats harness |

## App layout

Bottom tabs. Debug exists only in debug builds.

| Tab | Shows |
|---|---|
| **Home** | Grass meadow driven by health, catch cooldown, monitoring status. Touch affects appearance only. |
| **Stats** | Weekly/monthly tap rate, timing and per-app statistics from the SQLite store. |
| **Settings** | Difficulty, freshness window, notification rules (per-app rule, essential/non-essential answers, strictness), pause/activation. |
| **Debug** | Access/service/battery status, demo controls (catch chance, set health, force catch, fast-forward, reset), meadow preview, stats harness and **Notification tools** (test notification, custom notification composer with package overrides and attachments, saved presets, last 20 taps with reasons, observed notifications with a live classifier preview). |

## Run

```bash
./gradlew installDebug          # build + install on the connected phone
adb logcat -s FakeNotifs        # watch only our log lines
./gradlew testDebugUnitTest     # unit tests
```

AI judge keys (optional): `cloudflare.accountId` and `cloudflare.apiToken` in the git-ignored
`local.properties`. Without them the app builds and "Let us decide" taps cause no penalty
(`ai_not_configured`).

## Grant notification access

1. Open the app and follow onboarding to the notification access settings.
2. Turn on **Taptation**, confirm, and go back.
3. Recommended: allow **Display over other apps** (the caught overlay; otherwise a notification is
   used) and set battery usage to **Unrestricted** so the system doesn't kill the listener.

Shortcut over adb (works on most devices):

```bash
adb shell cmd notification allow_listener com.codejam.taptation/com.codejam.taptation.service.TapListenerService
```

## Verify

1. **Debug** tab > **Notification tools** > **Post test notification**, then tap the notification: a
   new row shows up in *Last 20 taps* with its classification and reason. Without AI keys a
   "Let us decide" tap is Unknown (`ai_not_configured`, no penalty). To see a catch, use the
   composer's package override with an app set to **Always bait** in Settings > Notification rules,
   or **Force catch** in the demo controls.
2. Post another one and **swipe it away**: no tap row (Logcat shows `reason=CANCEL`); it is recorded
   as a dismissal for stats only.
3. Tap a notification from a real app (Messages, Gmail): a row shows up with that app's package name.
4. **Simulate tap (no notification)** on the composer: a row with the typed package shows up without
   using the shade.
5. Turn notification access off and on again: no crash, and the service shows as connected again.

## Reading the Logcat lines

Every posted and removed notification logs its signals (filter `tag:FakeNotifs`):

```
posted pkg=com.discord update=false cat=msg channelId=... channel="Direct Messages" importance=HIGH conversation=true groupConversation=false style=MessagingStyle autoCancel=true ongoing=false summary=false group=true contentIntent=true media=none postTime=... age=3ms key=...
removed pkg=com.discord reason=CLICK(1) cat=msg channelId=... ... age=8123ms key=...
```

- `autoCancel=false`: tapping won't remove it, so the tap is never detected.
- `update=true`: the app replaced an active notification (same key). A small `age` on an update
  means `postTime` was reset.
- `?` means Android didn't provide the value: `conversation` needs Android 12+,
  `groupConversation` is only set by MessagingStyle, and `channel`/`importance` are often missing
  on `removed` lines (use the `posted` line).
- Title and text are never logged.

## Known limitations

- **Only auto-cancelling notifications count.** `REASON_CLICK` fires only when the notification
  disappears on tap.
- **adb-posted notifications can't test taps.** `adb shell cmd notification post` posts with no tap
  action. Use the in-app test notification or a real app.
- **Access can't be granted from code.** The user turns it on in system settings (or the adb shortcut).
- **Android 13+ restricted settings.** For a sideloaded APK the toggle may be greyed out: App info >
  ⋮ > **Allow restricted settings**.
- **Battery optimization** can kill the listener, mostly on non-Pixel phones.
- Our own notifications count as taps in **debug builds only, and only on the test channel**
  (`debug_test`). Release builds ignore them, so the caught alert can never cause another catch.
- The tap list and observed notifications live in memory and are cleared when the process dies.

## Debug controls

The demo controls go through `RepositoryDemoControls`, which calls the real `GameRepository`, so
they change the same saved game as normal taps. Debug-only config fields are stripped in release.
Details and test commands: [debug-controls-and-testing.md](debug-controls-and-testing.md).

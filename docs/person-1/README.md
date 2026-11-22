# Person 1: Notification service

Docs:
- [notification-service-brief.md](notification-service-brief.md): original brief and acceptance criteria.
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section A8 (`ageMillis`) and Section C (open questions 1, 2, 4) involve Person 1.

## Done (on `main`)

- `TapListenerService`: tap = `REASON_CLICK` only; skips group summaries, ongoing notifications, own package in release.
- Swipes and clear-all are reported as dismissals (`TapHandler.onDismiss`) for stats; see `../decision-stats-handler.md`.
- Caches title/text/category per notification key (500-entry in-memory LRU) and dispatches a `NotificationContext` to `TapDispatcher`.
- `TapDetection` pure filter + unit tests; `TapLog` (last 20 taps).
- `PermissionHelper`: notification-access check/settings, battery-optimization check/settings.
- Rebind on disconnect (skipped when access was revoked); connected flag for the debug screen.
- Debug tab: access/connected/battery status, last tap seen by the listener, demo controls. Notifications tab: test notification, custom notification form + saved presets with images, last 20 taps with reasons, observed notifications.
- Loop guard: in debug builds only our **test notification** channel (`debug_test`) counts as a tap, so our own "you got caught" alert can never cause another catch (branch `person1/signal-logging`).
- Logcat `posted`/`removed` lines include category, channel ID/name, importance, conversation and group-conversation flags, style, auto-cancel, `postTime`/age and `update` (branch `person1/signal-logging`; see README "Reading the Logcat lines").
- Real `GameRepository` handler installed at app start (by Person 2).

## In progress elsewhere: review it

Person 2's branch `codex/person-2-logic` (commit `cbf3925`, not merged to `main` yet) edits `service/TapListenerService.kt`:

- overrides the two-argument `onNotificationPosted(sbn, rankingMap)` and adds `onNotificationRankingUpdate`;
- adds `channelName` and `isConversation` (API 31+) from `Ranking`, and `ageMillis = now - sbn.postTime` (brief A8);
- calls new `TapHandler.onNotificationPosted / onNotificationRemoved / onListenerReset` hooks;
- exposes `TapListenerService.observations` (last 10 posted, debug builds only, memory only) shown on the Notifications tab with the classification each would get now.

Check before it merges: the tap rules in `CLAUDE.md` still hold, `onNotificationRankingUpdate` stays cheap (it walks up to 500 cached entries on the main thread), and nothing logs title/text.

## Left to do

1. **Phone verification** (brief section 7): tap vs swipe on the test notification, a real app's tap, access off/on without crash, `Service connected` returns to yes.
2. **Collect real data** (brief C2): with `adb logcat -s FakeNotifs` running, get notifications from Outlook, Instagram, Discord (DM, server message, like, email, meeting invite), tap some, swipe some, and save the `posted`/`removed` lines. Check `autoCancel`: false = no `REASON_CLICK` = tap never detected.
3. **Missing signals** (brief A2/C2): channel ID, importance and group-conversation flag are now logged but not passed to the logic. Passing them needs `NotificationContext` fields (Person 2's file): agree with Person 2 first, using the collected data.
4. **`postTime` on updated notifications** (brief C4): send several messages in one chat and compare `postTime` across the `update=true` lines. Decides how the freshness window behaves.
5. **Reliability** (priority feature): reboot (listener should rebind without opening the app), 10+ minutes idle with screen off, app killed from recents, revoke access mid-session.
6. **Catch notification support**: when Person 3 adds the "you got caught" alert, check its channel is not `debug_test` and that tapping it does not log a tap.
7. **Hand-offs**: Person 3 reuses `PermissionHelper` for onboarding; Person 4 uses the debug screen and test notification.
8. **Idea, not implemented: adaptive tap wait for the AI verdict.** A tap on a "Let us decide" notification whose
   verdict hasn't arrived waits a fixed `JudgeQueue.TAP_GRACE_MILLIS` (1 s; commit `823dc71`'s message says 2 s but
   the code has always been 1 s), then goes through as `ai_pending_timeout` (no penalty). The wait could adapt instead:
   - Verdict arrived within the wait: shorten the next wait towards the observed latency (for example
     `max(latency × 1.5, 300 ms)`), so fast connections don't hold taps longer than needed.
   - Wait expired without a verdict: lengthen the next wait (for example × 1.5, capped around 3 s), so a slow network
     stops turning every quick tap into a free pass.
   - Keep it in memory in `JudgeQueue` (resets to 1 s on restart), never block the listener callback, and log only
     the wait length. Trade-off: a longer wait delays the catch overlay after a tap.

Privacy rule: notification title/text may be shown on the debug screen from memory but must never be logged or persisted. Brief C2 asks to "log ... title": show it on screen, not in Logcat.
## Discord #general: expected result

On `main`: `Unknown` -> no penalty, unless Discord's package is marked as bait. On Person 2's branch it depends on Android's conversation flag (see [person-2 README](../person-2/README.md#discord-general)). Tomorrow's logs decide it: compare `conversation=`, `channel=` and `channelId=` on a Discord DM vs a #general message.

## AI classification

Person 1 does not build the classifier, but feeds it: the signals in the log lines are what an AI step (or tuned rules) would use. See [person-2 README](../person-2/README.md#ai-classification-optional).

## UI ↔ backend contract (done)

Added the backend APIs the screens use (`service/AppDirectory`, `service/SystemStatus`,
`logic/RulePicker`, `CatchFeedback.prompt` + `CatchSurface`, typed settings actions) and moved the
caught overlay's drawing to `ui/overlay`. Details and remaining items: [docs/ui-backend-contract.md](../ui-backend-contract.md).

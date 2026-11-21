# Person 1: Notification service

Docs:
- [notification-service-brief.md](notification-service-brief.md): original brief and acceptance criteria.
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section A8 (`ageMillis`) and Section C (open questions 1, 2, 4) involve Person 1.

## Done (on `main`)

- `TapListenerService`: tap = `REASON_CLICK` only; skips group summaries, ongoing notifications, own package in release.
- Caches title/text/category per notification key (500-entry in-memory LRU) and dispatches a `NotificationContext` to `TapDispatcher`.
- `TapDetection` pure filter + unit tests; `TapLog` (last 20 taps).
- `PermissionHelper`: notification-access check/settings, battery-optimization check/settings.
- Rebind on disconnect (skipped when access was revoked); connected flag for the debug screen.
- Debug screen: access/connected/battery status, post test notification, simulate tap, last 20 taps.
- Real `GameRepository` handler installed at app start (by Person 2).

## In progress elsewhere: review it

Person 2's branch `codex/person-2-logic` (commit `cbf3925`, not merged to `main` yet) edits `service/TapListenerService.kt`:

- overrides the two-argument `onNotificationPosted(sbn, rankingMap)` and adds `onNotificationRankingUpdate`;
- adds `channelName` and `isConversation` (API 31+) from `Ranking`, and `ageMillis = now - sbn.postTime` (brief A8);
- calls new `TapHandler.onNotificationPosted / onNotificationRemoved / onListenerReset` hooks;
- exposes `TapListenerService.observations` (last 10 posted, debug builds only, memory only) shown in `LogicDebugPanel`.

Check before it merges: the tap rules in `CLAUDE.md` still hold, `onNotificationRankingUpdate` stays cheap (it walks up to 500 cached entries on the main thread), and nothing logs title/text.

## Left to do

1. **Phone verification** (brief section 7): tap vs swipe on the test notification, a real app's tap, access off/on without crash, `Service connected` returns to yes.
2. **Collect real data** (brief C2): post/tap notifications from Outlook, Instagram, Discord and record package, category, channel, conversation flag, and whether they auto-cancel (no auto-cancel = no `REASON_CLICK` = tap never detected).
3. **Missing signals** asked for in brief A2/C2 and not captured yet: channel **ID** (only channel name on Person 2's branch), **importance**, group-conversation flag (`Notification.EXTRA_IS_GROUP_CONVERSATION`). Each needs a `NotificationContext` field (Person 2's file): agree with Person 2 first.
4. **`postTime` on updated notifications** (brief C4): send several messages in one chat and check whether `sbn.postTime` moves to the newest message. Decides how the freshness window behaves.
5. **Reliability** (priority feature): reboot (listener should rebind without opening the app), 10+ minutes idle with screen off, app killed from recents, revoke access mid-session.
6. **Hand-offs**: Person 3 reuses `PermissionHelper` for onboarding; Person 4 uses the debug screen and test notification.

Privacy rule: notification title/text may be shown on the debug screen from memory but must never be logged or persisted. Brief C2 asks to "log ... title": show it on screen, not in Logcat.

# Archive

Hackathon-era planning documents, written when the work was split between four people
(notification service, game logic, UI, testing/demo). That split no longer applies and these
documents are **not** current: defaults, rules, interfaces and ownership described here were
superseded. Kept for history only. For current behaviour read the root
[CLAUDE.md](../../CLAUDE.md), [revised-catch-flow.md](../revised-catch-flow.md) and
[logic/game-rules.md](../logic/game-rules.md).

| File | What it was |
|---|---|
| [hackathon-feature-plan.md](hackathon-feature-plan.md) | Core vs priority features per person, go/no-go checkpoint, cut list |
| [notification-service-brief.md](notification-service-brief.md) | Original brief and acceptance criteria for the notification listener |
| [classification-and-settings-brief.md](classification-and-settings-brief.md) | First classification rules and settings page spec; disagrees with the code (see below) |
| [testing-plan.txt](testing-plan.txt) | Layered test plan, debug-menu wishlist, demo checklist, edge cases |

## Classification brief vs current code

| Topic | Brief | Code |
|---|---|---|
| Catch probability | 10% | Easy 20%, Medium 40%, Hard 70% |
| Bypass rules | whitelist, call/alarm/reminder/event, VIP title, urgent keyword | only apps set to Always important; the old rules live in `assessByRules`, off the tap path |
| Unmatched notification | bait | "Let us decide" (default) follows the AI verdict; no verdict means no penalty |
| Freshness window | 10 min, no roll if older | `freshnessSeconds` default 600 (0 = off); stale taps skip classification and roll |
| Health at 0 | patch resets | stays dead until `reset()`; bait taps are still caught (no extra damage) |
| Tap interface | `onTap(packageName, ageMillis)` | `onTap(NotificationContext)` |
| Settings storage | new `SettingsRepository` + DataStore | `GameConfig` in `GameRepository` (SharedPreferences) |

The testing plan suggests `adb shell cmd notification post` for tap tests: those notifications have
no contentIntent, so they can't produce a tap.

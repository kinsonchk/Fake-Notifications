# Person 3: UI and stats

Docs:
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section B (settings page spec and acceptance criteria).
- [../person-2/PERSON_2_INTEGRATION.md](../person-2/PERSON_2_INTEGRATION.md): "Person 3 API" (`GameRepository` state, whitelist, `updateConfig`, reset).
- [../features-by-person.md](../features-by-person.md): core features (onboarding, whitelist, home tree/pet, lockout, stats) and priority feature (catch feedback).

Status: `MainActivity` shows bottom tabs (`ui/TaptationApp.kt`). Home, Stats and Settings in `ui/` are placeholders built by Person 1 on the existing `GameRepository` API, to be replaced or restyled: Home needs the tree visual, onboarding and lockout are still missing.

Before building the settings page: Section B proposes a new `SettingsRepository` + DataStore, but settings already live in `GameConfig` inside `GameRepository` (SharedPreferences). A second store would split the source of truth. Agree with Person 2 whether the screen writes through `GameRepository.updateConfig` / `setWhitelisted` instead.

Reuse `service/PermissionHelper.kt` for the onboarding permission step and battery-optimization prompt.


## Catch notification ("you lost a life")

A notification is the only practical way to show a catch at the moment it happens. A tap on a bait notification opens the other app, and since Android 10 a background app can't start its own screen (being a notification listener doesn't exempt us). The alternative is an overlay (`SYSTEM_ALERT_WINDOW`), which needs an extra, scary permission.

Plan:
- **When**: only on `Caught`, plus death ("Your tree died. Tap to restart"). Never on `NoPenalty` or `Bypass`.
- **Trigger**: observe `GameRepository.state` and fire when `totalCatches` increases (not `lastResult`, which can repeat). Never call `applyCatch`: damage is already applied.
- **Content**: app name only, never the bait notification's title or text. E.g. "You tapped Instagram. Your tree lost 20 health (80/100). Locked for 30 s."
- **Tap action**: opens our lockout screen (starting an activity from a notification tap is allowed).
- **Channel**: its own high-importance channel (e.g. `catches`) so it shows as a heads-up. **Never `debug_test`**: the tap filter counts only that channel of our own package, which is what stops this alert from causing another catch when tapped.
- **Lifetime**: `setTimeoutAfter(lockout)` so it disappears when the lockout ends; auto-cancel; needs `POST_NOTIFICATIONS` on Android 13+ (the debug screen already requests it).

Needs Person 1's loop guard (branch `person1/signal-logging`) merged first.



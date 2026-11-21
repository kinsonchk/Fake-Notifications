# Person 3: UI and stats

Docs:
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section B (settings page spec and acceptance criteria).
- [../person-2/PERSON_2_INTEGRATION.md](../person-2/PERSON_2_INTEGRATION.md): "Person 3 API" (`GameRepository` state, whitelist, `updateConfig`, reset).
- [../features-by-person.md](../features-by-person.md): core features (onboarding, whitelist, home tree/pet, lockout, stats) and priority feature (catch feedback).

Status: only `ui/theme/` exists on `main`; `MainActivity` still shows the debug screen. Remote branch `person3/ui-stats` has no commits beyond `main`.

Before building the settings page: Section B proposes a new `SettingsRepository` + DataStore, but settings already live in `GameConfig` inside `GameRepository` (SharedPreferences). A second store would split the source of truth. Agree with Person 2 whether the screen writes through `GameRepository.updateConfig` / `setWhitelisted` instead.

Reuse `service/PermissionHelper.kt` for the onboarding permission step and battery-optimization prompt.

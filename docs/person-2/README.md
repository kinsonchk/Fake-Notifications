# Person 2: Classification and consequence logic

Docs:
- [PERSON_2_INTEGRATION.md](PERSON_2_INTEGRATION.md): exact rules, defaults, Person 1 contract, Person 3 API, persistence, demo steps.
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section A (classification rules) and Section C (open questions).

Status: game engine, repository, storage and debug panel are on `main`. Branch `codex/person-2-logic` (`cbf3925`) adds importance-first classification (VIP names, important keywords, app profiles for Outlook/Instagram/Discord, channel rules) and age-aware results; not merged yet.

Open items from the brief:
- **Freshness window** (A6.1, default 10 min, no roll if older): not implemented. Branch records `ageMillis` but ignores it.
- **Defaults differ from the brief** (probability, damage, default level, "everything else is bait"): see "Brief vs current code" in the root `CLAUDE.md`. Decide which wins.
- **Settings ownership** (C5) and default urgent keywords (C3).

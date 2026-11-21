# Person 2: Classification and consequence logic

Docs:
- [PERSON_2_INTEGRATION.md](PERSON_2_INTEGRATION.md): exact rules, defaults, Person 1 contract, Person 3 API, persistence, demo steps.
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section A (classification rules) and Section C (open questions).

Status: game engine, repository, storage and debug panel are on `main`. Branch `codex/person-2-logic` (`cbf3925`) adds importance-first classification (VIP names, important keywords, app profiles for Outlook/Instagram/Discord, channel rules) and age-aware results; not merged yet.

Open items from the brief:
- **Freshness window** (A6.1, default 10 min, no roll if older): not implemented. Branch records `ageMillis` but ignores it.
- **Defaults differ from the brief** (probability, damage, default level, "everything else is bait"): see "Brief vs current code" in the root `CLAUDE.md`. Decide which wins.
- **Settings ownership** (C5) and default urgent keywords (C3).

## Discord #general

Expected outcome for an ordinary message in a Discord server channel such as #general, with the rules on `codex/person-2-logic`:

| Discord app profile set? | Android `conversation` flag | Result |
|---|---|---|
| no (default) | true | `Unknown` -> no penalty |
| no | false | category `msg` -> **Bait** -> roll |
| yes | true | **Important** (`discord_conversation`) |
| yes | false | **Bait** |
| either | missing (Android < 12) | `Unknown` -> no penalty |

Earlier rules win first: whitelist, VIP name in title (Discord's title is usually the sender), important keyword anywhere (note: `code` bypasses every message saying "code", common in programming servers), `@mention`/`@here`/`@everyone` with the Discord profile.

Open: if Discord marks **server** messages as conversations (not only DMs), #general is never penalized. The bait-channel rule matches Android's notification channel name, not the Discord text channel. Decide from Person 1's Logcat data (`conversation=`, `channel=`, `channelId=` for a DM vs #general).

## AI classification (optional)

Brief A5: optional, only for unsure cases, app must work fully without it. Proposed design:

```
posted -> rules -> Important / Bait -> done
               -> Unknown -> AI (async, at post time) -> cache verdict by key -> tap reads cache
                          -> timeout / error / AI off -> NoPenalty (fail safe)
```

Run it in `onNotificationPosted` (hook already on the branch), never on the tap, so taps stay instant.

| Option | Pros | Cons |
|---|---|---|
| On-device LLM (Gemini Nano via ML Kit GenAI) | Text stays on the phone, free, offline | Only some recent phones: check the demo phone first |
| Cloud LLM (Claude API, or Gemini via Firebase AI Logic) | Any phone, best quality, can return a reason for the catch message | Sends other people's messages off the device (needs an opt-in toggle), API key in the APK is extractable (use a proxy), ~1 s latency, new dependency |
| AI offline at dev time | No runtime risk or cost: feed Person 1's metadata-only logs to an LLM to propose app profiles, channels, keywords | Static rules afterwards |
| Learn from the user | "That was important" button after a catch adds the sender/channel/app to the important rules | Only learns after a mistake |

Recommended order: collect real data, tune rules (AI offline), then add the `Unknown` -> AI step if time allows (on-device if supported, else cloud with an opt-in toggle).

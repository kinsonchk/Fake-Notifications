# Docs

One folder per team member. Each folder's `README.md` lists that person's docs, current status and what is left.

| Folder | Owner | Area |
|---|---|---|
| [person-1/](person-1/README.md) | Person 1 | Notification service (`service/`, `debug/DebugScreen.kt`, `debug/NotificationsScreen.kt`, `debug/TestNotification.kt`) |
| [person-2/](person-2/README.md) | Person 2 | Classification, game rules, storage (`logic/`, `TaptationApplication.kt`) |
| [person-3/](person-3/README.md) | Person 3 | UI: onboarding, settings, tree/pet, lockout, stats (`ui/`) |
| [person-4/](person-4/README.md) | Person 4 | Testing, debug menu, demo |

Shared docs (whole team):

- [caught-fishing-scenes.md](caught-fishing-scenes.md): shared fishing/cameo renderer, headline, earned visitor eligibility, unchanged catch actions and isolated preview.
- [living-meadow.md](living-meadow.md): earned meadow growth, permanent discoveries, creature stories, underground play and isolated debug preview.
- [ai/tutorial-personal-ai-criteria-design.md](ai/tutorial-personal-ai-criteria-design.md): onboarding page for collecting the user's important/non-essential notification preferences and feeding the existing AI judge.
- [onboarding-and-app-copy-options.md](onboarding-and-app-copy-options.md): current onboarding and catch/settings wording, with three alternatives per main copy section and a short spoken demo script.
- [app-rule-and-catch-flow-findings.md](app-rule-and-catch-flow-findings.md): traces Always important, Always bait, and Let us decide through classification, AI use, catch chance, and the guaranteed-catch fix.
- [ai/clef-prompt-alternatives.md](ai/clef-prompt-alternatives.md): three candidate prompts for the active Clef notification classifier, with their improvements, tradeoffs and evaluation guidance.
- [UI_DESIGN_GUIDE.md](UI_DESIGN_GUIDE.md): required visual rules for UI work, component recipes, reference screenshots, review checklist and a reusable AI prompt.
- [features-by-person.md](features-by-person.md): core vs priority features per person, shared interfaces, go/no-go checkpoint, cut list.
- [classification-and-settings-brief.md](classification-and-settings-brief.md): classification rules (Section A, Person 2), settings page (Section B, Person 3), open questions (Section C, Persons 1 and 2). Written after the first logic version: see "Brief vs current code" in the root `CLAUDE.md` before building from it.

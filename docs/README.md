# Docs

Start with the root [CLAUDE.md](../CLAUDE.md) (architecture and rules for contributors) and the
[developer guide](developer-guide.md) (build, install, grant access, verify on a phone, read logs).
Code is the source of truth; when a doc disagrees with it, fix the doc.

## Current behaviour

- [revised-catch-flow.md](revised-catch-flow.md): latest agreed product behaviour (difficulties, activation gate, freshness, two-choice caught feedback, statistics). Supersedes older docs.
- [logic/game-rules.md](logic/game-rules.md): exact game rules and defaults, listener -> logic contract, backend API for the UI, storage.
- [ui-backend-contract.md](ui-backend-contract.md): what the UI reads and calls; everything else lives in the backend.
- [ai/how-ai-classification-works.md](ai/how-ai-classification-works.md): "Let us decide" AI judge (Clef), inputs, prompt, strictness cutoffs, fail-safe behaviour.
- [ai/clef-prompt-alternatives.md](ai/clef-prompt-alternatives.md): the old Clef prompt and three alternatives (Option A implemented).
- [app-rule-and-catch-flow-findings.md](app-rule-and-catch-flow-findings.md): Always important, Always bait and Let us decide traced through classification, AI use and catch chance.
- [decision-stats-handler.md](decision-stats-handler.md): why stats are a repository, not a service; SQLite event store and summaries.
- [hp/healthRegeneration.md](hp/healthRegeneration.md): health recovery over time and the debug recovery interval.
- [living-meadow.md](living-meadow.md): health-driven meadow, earned growth, discoveries, underground play, debug preview.
- [caught-fishing-scenes.md](caught-fishing-scenes.md): fishing/cameo caught scenes shared by overlay and in-app dialog.

## UI

- [UI_DESIGN_GUIDE.md](UI_DESIGN_GUIDE.md): required visual rules, component recipes, reference screenshots, review checklist and a reusable AI prompt.
- [ui-reference/feature-per-app-rules.md](ui-reference/feature-per-app-rules.md) and [notification-settings.html](ui-reference/notification-settings.html): per-app notification rules and their mockup.
- [ui-reference/onboarding-slides-3-5-mockup.html](ui-reference/onboarding-slides-3-5-mockup.html), [grass-screen-mockup.html](grass-screen-mockup.html), [underground-mockup.html](underground-mockup.html), [valley-reveal-mockup.html](valley-reveal-mockup.html): interactive mockups.
- [design-reference/](design-reference/): reference screenshots and [branding](design-reference/branding/README.md).
- [onboarding-and-app-copy-options.md](onboarding-and-app-copy-options.md): current onboarding and catch/settings wording with alternatives and a short demo script.
- [ai/tutorial-personal-ai-criteria-design.md](ai/tutorial-personal-ai-criteria-design.md): onboarding page collecting the user's essential / non-essential criteria for the AI judge.

## Design proposals (partly superseded)

- [caught/caught-feedback-presentation-design.md](caught/caught-feedback-presentation-design.md), [caught/caught-choice-penalty-design.md](caught/caught-choice-penalty-design.md), [caught/catch-chance-settings-design.md](caught/catch-chance-settings-design.md): caught overlay, continue penalty and catch-chance settings proposals.
- [whitelist/](whitelist/): app whitelist picker and demo notes. The whitelist was replaced by per-app rules (Always important / Always bait / Let us decide); codec v6 migrates old whitelists into `appRules`.

## Testing

- [debug-controls-and-testing.md](debug-controls-and-testing.md): Java/Gradle setup, debug demo controls, test notification, unit and instrumented test commands.

## Archive

- [archive/](archive/README.md): hackathon-era plans and briefs (team split, first classification brief, original listener brief, early test plan). Historical only.

# Docs

Start with the root [CLAUDE.md](../CLAUDE.md) (architecture and rules for contributors) and the
[developer guide](developer-guide.md) (build, install, grant access, verify on a phone, read logs).
Code is the source of truth; when a doc disagrees with it, fix the doc.

## Current behaviour

- [revised-catch-flow.md](revised-catch-flow.md): latest agreed product behaviour (difficulties, activation gate, freshness, two-choice caught feedback, statistics). Supersedes older docs.
- [logic/game-rules.md](logic/game-rules.md): exact game rules and defaults (including health recovery), listener -> logic contract, backend API for the UI, storage.
- [ui-backend-contract.md](ui-backend-contract.md): what the UI reads and calls; everything else lives in the backend.
- [ai/how-ai-classification-works.md](ai/how-ai-classification-works.md): "Let us decide" AI judge (Clef), inputs, prompt, strictness cutoffs, fail-safe behaviour.
- [ui-reference/feature-per-app-rules.md](ui-reference/feature-per-app-rules.md): Always important, Always bait and Let us decide.
- [decision-stats-handler.md](decision-stats-handler.md): why stats are a repository, not a service; SQLite event store and summaries.
- [living-meadow.md](living-meadow.md): health-driven meadow, earned growth, valley reveal, underground play, debug preview.
- [caught-fishing-scenes.md](caught-fishing-scenes.md): fishing/cameo caught scenes shared by the overlay and the in-app dialog.

## UI

- [UI_DESIGN_GUIDE.md](UI_DESIGN_GUIDE.md): required visual rules, component recipes, reference screenshots, review checklist and a reusable AI prompt.
- Interactive mockups: [ui-reference/unslop-mockups.html](ui-reference/unslop-mockups.html) (redesign directions), [grass-screen-mockup.html](grass-screen-mockup.html), [underground-mockup.html](underground-mockup.html), [valley-reveal-mockup.html](valley-reveal-mockup.html).
- [design-reference/branding/](design-reference/branding/README.md): logo and launcher icon.

## Testing

- [debug-controls-and-testing.md](debug-controls-and-testing.md): Java/Gradle setup, the Debug tab, custom notifications, unit and instrumented tests, phone checks.

## Archive

Historical design notes, kept for reference. Each starts with a note on what replaced it; they disagree with the code in places.

- [archive/caught-feedback-presentation-design.md](archive/caught-feedback-presentation-design.md), [archive/caught-choice-penalty-design.md](archive/caught-choice-penalty-design.md), [archive/catch-chance-settings-design.md](archive/catch-chance-settings-design.md): caught overlay, continue penalty and catch-chance proposals.
- [archive/app-whitelist-demo-explanation.md](archive/app-whitelist-demo-explanation.md), [archive/app-whitelist-implementation-design.md](archive/app-whitelist-implementation-design.md), [archive/app-whitelist-picker-design.md](archive/app-whitelist-picker-design.md): the app whitelist, replaced by per-app rules (codec v6 migrates old whitelists into `appRules`).
- [archive/health-regeneration-design.md](archive/health-regeneration-design.md): design for interval recovery and the debug recovery interval.
- [archive/app-rule-and-catch-flow-findings.md](archive/app-rule-and-catch-flow-findings.md): change note for Always bait as a guaranteed catch.
- [archive/tutorial-personal-ai-criteria-design.md](archive/tutorial-personal-ai-criteria-design.md): design for the onboarding criteria page.
- [archive/onboarding-and-app-copy-options.md](archive/onboarding-and-app-copy-options.md): onboarding and settings wording options.
- [archive/clef-prompt-alternatives.md](archive/clef-prompt-alternatives.md): earlier Clef prompts and alternatives.

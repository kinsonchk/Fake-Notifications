# Person 4: Testing and demo

Docs:
- [testing-plan.txt](testing-plan.txt): layered test plan, debug menu wishlist, end-to-end checklist, edge cases, demo rehearsal.
- [../features-by-person.md](../features-by-person.md): Person 4 core and priority features.
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section B6 acceptance criteria to test once the settings page exists.

Ready to use: in-app **Post test notification** (real `contentIntent`, so taps register), **Simulate tap**, and `LogicDebugPanel` (probability, level, bait/whitelist, health 0/10/100, force catch, reset).

Note: `adb shell cmd notification post` notifications have no tap action, so they test posting/logic only, not tap detection.

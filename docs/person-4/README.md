# Person 4: Testing and demo

Docs:
- [testing-plan.txt](testing-plan.txt): layered test plan, debug menu wishlist, end-to-end checklist, edge cases, demo rehearsal.
- [../features-by-person.md](../features-by-person.md): Person 4 core and priority features.
- [../classification-and-settings-brief.md](../classification-and-settings-brief.md): Section B6 acceptance criteria to test once the settings page exists.

Ready to use: **Notifications** tab (Post test notification with a real `contentIntent`, custom notifications that stand in for another package, saved presets, tap reasons) and **Debug** tab (demo controls backed by the real game: 0/100 %, set health, force catch, fast-forward, reset). Probability, level, whitelist and bait rules are on the **Settings** tab.

Note: `adb shell cmd notification post` notifications have no tap action, so they test posting/logic only, not tap detection.

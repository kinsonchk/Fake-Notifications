# Catch Chance Settings: User-Facing Design

## Decision to make

Taptation needs a clear way for a person to choose how often a bait notification can cause a catch. The two main choices are named difficulty presets (Easy, Medium, Hard) and a custom percentage.

## Recommendation

Offer a small set of named presets as the main choice, with an optional **Custom** setting for people who want a specific percentage.

Suggested choices:

| Choice | Catch chance per eligible bait tap | User-facing meaning |
|---|---:|---|
| Easy | 5% | Catches are occasional; most bait taps are safe. |
| Medium | 10% | Catches happen sometimes. |
| Hard | 20% | Catches are more likely. |
| Custom | User chooses 0–100% | Set the exact chance. |

These percentages are starting suggestions, not fixed truths. The team should try them during playtesting and adjust them if the game feels too punishing or too easy. The app should clearly call these values a **catch chance**, so “Hard” does not sound like a guaranteed catch.

## Why use presets and custom?

Presets make the first choice quick and understandable. A new user can select a familiar difficulty without interpreting a slider. Custom gives players, accessibility testers, and demo presenters control when the presets do not fit.

Showing only a percentage slider can make people wonder what the number means and what is a reasonable setting. Showing only presets can frustrate people who want a gentler setting or want to make a demo deterministic. Combining them keeps the ordinary choice simple while still allowing control.

## How much control to show

### Regular settings screen

Keep the main screen calm and show:

1. **Difficulty:** Easy / Medium / Hard / Custom.
2. A one-line explanation of the selected setting, such as “Medium: a 10% chance on each eligible bait tap.”
3. If **Custom** is selected, show a percentage slider or number picker from 0% to 100%, plus the current value.

Do not show random-roll details, package identifiers, internal classification names, or test-only controls in normal settings. Keep those in the debug screen.

### Debug/demo screen

Keep the exact percentage control because it makes demos repeatable. For example, 0% proves that a bait tap does not catch, while 100% makes the next eligible bait tap catch every time. Label this clearly as a demo/testing control so people do not confuse it with a recommended everyday setting.

## Example screen

```text
Catch chance
Choose how often a distracting notification can catch you.

( ) Easy       5% chance
(*) Medium    10% chance
( ) Hard      20% chance
( ) Custom

Medium: about 1 in 10 eligible bait taps may catch you.
```

When Custom is selected:

```text
Custom catch chance
[────────●────────────]  12%

Each eligible bait tap has a 12% chance to catch you.
```

Avoid promises such as “exactly one catch in every ten taps.” Random chance means that the result can be different each time. A 10% chance does not guarantee one catch out of ten taps.

## What counts as a catch chance attempt?

Only a notification tap that the game has classified as bait, and that is not whitelisted, should use the catch chance. A whitelisted app must bypass the roll. Swiping a notification away is not a tap and must not roll. A non-bait or unknown notification should follow the project's classification rules and should not be presented as a catch attempt unless the rules explicitly classify it as bait.

The sequence should be understandable and consistent:

```text
Notification is tapped
        ↓
Is its app whitelisted? ── yes → safe bypass; no roll
        ↓ no
Is the notification classified as bait? ── no → no catch roll
        ↓ yes
Roll against the selected catch chance
        ↓
Catch or no catch
```

## Data and implementation guidance

Store one numeric catch probability in the existing game configuration (for example, `0.10` for 10%). Presets simply select their corresponding value. Custom selects a value in the allowed range. Do not store a second, separate setting that can disagree with the percentage.

When someone selects a preset, save its percentage. When someone adjusts the custom value, save that percentage. If the user later returns to the difficulty options, the app may show **Custom** when the saved percentage does not match a preset. This keeps the displayed choice consistent with the actual game behavior.

The game engine should receive the final numeric probability and apply it only after whitelist and bait classification. UI code should update the shared settings repository; it should not implement catch rules itself.

## Testing examples

- **0% custom:** eligible bait taps do not catch; the random comparison should never pass.
- **5%, 10%, or 20% presets:** the engine uses the matching stored probability. Unit tests should use a deterministic random value to check both sides of the threshold rather than expecting a particular rate over a short run.
- **100% custom:** every eligible bait tap catches unless another rule (whitelist, active lockout) prevents that attempt. At 0 health every bait tap catches anyway, whatever the chance.
- **Whitelisted bait app:** no random roll at any selected chance.
- **Swipe-dismissed notification:** no tap and no random roll.
- **Custom value is saved:** after closing and reopening the app, the selected percentage and displayed option agree.

For live playtesting, a larger batch of taps can estimate whether a probability feels right, but it should not be treated as an exact pass/fail test. Random outcomes naturally vary.

## Recommendation summary

Make **Easy / Medium / Hard** the quick, beginner-friendly choice and include **Custom** for an exact 0–100% chance. Keep 0% and 100% visible in the debug/demo controls for reliable testing. Explain that the percentage is the chance for each eligible, non-whitelisted bait tap, and keep the random mechanics out of the ordinary settings screen.

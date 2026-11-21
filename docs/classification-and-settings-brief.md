# Claude Code Brief: Classification Rules (Person 2) and Settings Page (Person 3)

> Paste into Claude Code or save as `CLAUDE.md`. Section A is for Person 2's logic. Section B is for the settings page. Section C lists open questions that must be settled before or while building.

**App:** Fake Notifications (Android, Kotlin, Jetpack Compose, minSdk 26, compile/target SDK 36). It watches other apps' notifications. A tap on a *bait* notification has a small random chance of a consequence (grass patch gets browner plus a lockout). *Important* notifications are never penalized.

---

## Section A: Classification rules (Person 2)

### A1. What this code does
Decides, for each tapped notification, one of:
- `Bypass`: important, never penalize
- `NoPenalty`: bait, but the dice roll missed, or the notification is too old, or the classifier is unsure
- `Caught(level)`: bait, the roll hit, apply the consequence

### A2. Signals available on each notification
- App package name
- Category (message, social, promo, email, call, alarm, event, reminder), set by the posting app, optional, and not always accurate
- Conversation flag and channel (from the ranking data), plus importance
- Channel name or ID (many apps split e.g. "Messages" vs "Likes")
- Title and text
- Group-conversation flag

### A3. Rules, in this order (first match wins)
1. App is on the whitelist: **Bypass**
2. Category is call, alarm, reminder, or event: **Bypass**
3. Title matches a VIP name the user typed in settings: **Bypass**
4. Text contains an urgent keyword (e.g. "urgent", "emergency", "code", "verification"): **Bypass**
5. Category is promo or social: **bait**
6. Everything else: **bait**, except the unsure handling in A5

### A4. App examples (starting assumptions, verify with real data)
- **Outlook:** email is bulk by category, so bait unless the sender is a VIP, it is a meeting invite or reminder, or it has urgent keywords.
- **Instagram:** likes, follows, and suggestions are bait. DMs are bait unless the sender is a VIP.
- **Discord:** server chatter is bait. DMs and @mentions can be important (group-conversation flag plus the user's name in the text).

### A5. Unsure cases must be handled (fail safe)
- If the classifier cannot decide (missing category, no channel info, ambiguous title), return `NoPenalty`. A false catch on a real emergency is worse than a missed catch.
- If anything throws or times out, return `NoPenalty`.
- Log every unsure decision with its signals, so rules can be improved.
- Optional later (out of scope for now): send only unsure cases to a small model (Gemini via Firebase AI Logic, or an on-device classifier). The app must work fully without it.

### A6. After classification
1. **Freshness check:** if `ageMillis` is greater than the freshness window (default 10 minutes, from settings), return `NoPenalty` without rolling.
2. **Roll:** random chance from settings (default 10%). Hit means `Caught(level)`, miss means `NoPenalty`.
3. `Caught` calls `applyCatch(level)`, which lowers grass health (Mild -5, Medium -10, Savage -20) and triggers the lockout.
4. Clean time regrows health (+5 per clean hour). Health at 0 resets the patch.

### A7. Interfaces
- In: `onTap(packageName, ageMillis)` from Person 1.
- Out: `Bypass`, `NoPenalty`, or `Caught(level)`; `applyCatch(level)` and `getHealth()` to the UI.
- Reads settings from the shared settings store (Section B).
- Pure functions where possible, and unit tests for: whitelist bypass, category bypass, VIP bypass, keyword bypass, stale tap, probability 0% and 100%, unsure returning `NoPenalty`.

### A8. Where `ageMillis` comes from
`System.currentTimeMillis() - sbn.postTime`, computed by Person 1 in the listener at the moment of the tap. Use `sbn.postTime`, not the notification's `when` field.

---

## Section B: Settings page (Claude Code task)

### B1. Goal
A single Compose settings screen whose values are stored locally and read by Person 2's logic. Keep it simple and demo-friendly.

### B2. Settings to include
| Setting | Type | Default | Notes |
|---|---|---|---|
| Catch probability | Slider, 0-50% | 10% | Debug build also allows 0% and 100% |
| Freshness window | Slider, 1-60 min | 10 min | Debug build allows a few seconds, for demos |
| Penalty level | Mild / Medium / Savage | Medium | Maps to health damage |
| VIP names | Editable list of text entries | empty | Matched against notification titles (case-insensitive) |
| Whitelisted apps | List of installed apps with toggles | empty | Search box if time allows |
| Urgent sound | Toggle | on | Plays the app's own sound for Bypass notifications (optional) |
| Reset data | Button, with confirmation | n/a | Clears stats and health |

### B3. Storage and interface
- Use Jetpack **DataStore (Preferences)** or SharedPreferences. Pick the simpler one.
- Create a `SettingsRepository` with typed getters and a way to observe changes, plus a `Settings` data class:

```kotlin
data class Settings(
    val catchProbability: Float = 0.10f,
    val freshnessWindowMillis: Long = 10 * 60 * 1000L,
    val level: PenaltyLevel = PenaltyLevel.MEDIUM,
    val vipNames: List<String> = emptyList(),
    val whitelistedPackages: Set<String> = emptySet(),
    val urgentSoundEnabled: Boolean = true
)
enum class PenaltyLevel { MILD, MEDIUM, SAVAGE }
```

- `isWhitelisted(packageName)` reads from the same store.
- Person 2 reads `Settings`, and does not write it. Person 3's screen writes it.

### B4. Installed-apps list
- Android 11+ restricts which apps are visible. Use a `<queries>` entry in the manifest for apps with a launcher intent (`MAIN` plus `LAUNCHER`) instead of `QUERY_ALL_PACKAGES`.
- Show app name and icon, and hide system apps unless they have a launcher entry.

### B5. UI requirements
- One scrollable screen with grouped sections (Detection, People, Apps, Sound, Data).
- Changes save immediately, with no save button.
- Show the current value next to each slider.
- Add a short explanation line under the freshness window: "Only taps within this time can get you caught."

### B6. Acceptance criteria
1. Changing a setting persists across an app restart.
2. Setting probability to 100% and tapping a bait notification always triggers a catch.
3. Setting probability to 0% never triggers one.
4. A whitelisted app's notification returns `Bypass`.
5. A VIP name in the title returns `Bypass`.
6. A tap older than the freshness window returns `NoPenalty` without a roll.
7. The installed-apps list shows launchable apps on the demo phone.
8. Reset data clears stats and health after confirmation.

### B7. Out of scope
Accounts, cloud sync, theming, localization, and the Gemini or model integration.

---

## Section C: Open questions to settle early

1. **When does classification run?** Rules need category, channel, title, and text, but the agreed `onTap(packageName, ageMillis)` only passes the package and age. Recommended: Person 1 also calls `onPosted(info)` in `onNotificationPosted` with a small `NotificationInfo` (key, package, category, channel, conversation flag, title, text). Person 2 classifies and caches by notification key, and `onTap` looks up the cached result. Person 1 and Person 2 must agree on this in the first 30 minutes.
2. **What are the real values?** Person 1's debug screen should log category, channel ID, conversation flag, importance, and title for real notifications from Outlook, Instagram, and Discord. Adjust the rules from that data, not from assumptions.
3. **What are the default urgent keywords?** Agree on a short list, and make it editable later.
4. **Do updated notifications reset `postTime`?** Test with a real messaging app. This decides whether the freshness window counts from the newest message.
5. **Who owns `Settings` defaults?** Suggest Person 3 owns the screen and the repository, and Person 2 owns the meaning of each value.
6. **Is the classifier fast enough?** It must stay cheap at post time. Any model call (later) must be asynchronous and fail safe.

---

## Working agreement for Claude Code
1. List what already exists in the repo first. Do not overwrite other people's files.
2. Plan briefly, then implement the simplest working version first.
3. Stay in your folders (`logic/` for Section A, `ui/` and `data/` for Section B) and add `// TODO(Person X)` for anything outside your scope.
4. Add unit tests for the classification rules, and run `./gradlew testDebugUnitTest`.
5. Finish with a summary: files changed, how to verify, and any open question still unresolved.

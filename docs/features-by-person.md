# Fake Notifications: Core and Priority Features by Person

**How to read this:**
- **Core features** are the must-haves. The demo doesn't work without them.
- **Priority feature** is the one extra each person should add first once their core is done and tested. Do not start it until the core works.

**Go/no-go checkpoint:** if tap detection doesn't work on a real phone by hour 4, simplify (use a helper app that posts notifications and a listener that logs them) and build the rest around that.

---

## Person 1: Notification service

**Core features**
- `NotificationListenerService` set up and declared in the manifest
- Permission flow that sends the user to notification access settings
- Tap detection using `REASON_CLICK` in `onNotificationRemoved`
- Swipe-dismiss correctly ignored (dismiss is not a tap)
- Calls `onTap(packageName)` to hand each tap to Person 2
- Service stays alive in the background

**Priority feature**
- **Reliability on the demo phone:** handle battery optimization, make the service rebind after a reboot, and detect/handle a revoked permission. A demo that dies mid-presentation costs more than any missing feature.

---

## Person 2: Urgency and consequence logic

**Core features**
- `onTap(packageName)` handler that returns `Bypass`, `NoPenalty`, or `Caught`
- Whitelist check (`isWhitelisted(packageName)`) so urgent apps bypass
- Configurable random roll (default 5-10%)
- Consequence rules: health damage per catch, regrowth per clean hour, death/reset at 0
- `applyCatch(level)` and `getHealth()` exposed to the UI
- Local store (Room or SharedPreferences) logging taps, catches, and health

**Priority feature**
- **Smarter bait vs. urgent classification:** use app category and simple keywords (e.g. "ride", "code", "emergency") so important notifications bypass even from apps that are not whitelisted.

---

## Person 3: UI and stats

**Core features**
- Onboarding screen that explains the app and requests notification access
- Whitelist screen listing installed apps with toggles
- Home screen showing the visual state (tree or pet) that changes with health
- Lockout screen (about 30 seconds, not skippable) shown on a catch
- Simple stats screen: taps, catches, and current streak
- Reads all data from the shared store

**Priority feature**
- **Catch feedback effect:** falling leaves or a wilt animation plus a short shame message ("You tapped TikTok. Your tree lost a branch") the moment a catch happens. This is what makes the demo feel real.

---

## Person 4: Testing and demo

**Core features**
- Way to post fake notifications on demand (adb commands or a small helper app)
- Debug menu: force catch, set health, set probability to 0% or 100%, reset data
- End-to-end checklist run on the exact demo phone:
  1. Fresh install, onboarding, permission granted
  2. Tap bait notification at 100%: lockout appears and health drops
  3. Tap whitelisted notification: nothing happens
  4. Swipe-dismiss bait notification: nothing happens
  5. Health reaches 0: reset happens
  6. Stats match what was just done
- Edge case testing: battery optimization, reboot, revoked permission, multiple notifications, app killed from recents

**Priority feature**
- **Fast-forward time control and the backup demo video:** a debug control that jumps time so regrowth and death can be shown in seconds, plus a recorded successful run in case the live demo fails.

---

## Shared (agree in the first 30 minutes)

- **Interfaces:**
  - `onTap(packageName)`: Person 1 to Person 2
  - `applyCatch(level)` and `getHealth()`: Person 2 to Person 3
  - `isWhitelisted(packageName)`: Person 2 and Person 3
- **Storage:** Person 2 owns it, Person 3 reads it.
- **Settings:** Person 3's settings screen feeds probability and level to Person 2.

## Still unassigned

- **Pitch and presentation:** decide who writes and delivers it. Suggest Person 3 or Person 4 once their core is done, with everyone rehearsing together.

## Cut list if time runs short (cut in this order)

1. Weekly report and extra stats
2. Mild/Medium/Savage levels (use one fixed level)
3. Keyword-based classification (use whitelist only)
4. Animations (use static images)

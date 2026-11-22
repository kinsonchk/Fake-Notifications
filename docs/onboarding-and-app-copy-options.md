# Onboarding and app wording options

This is a copywriting guide for Taptation's onboarding and the related app screens. It records the wording currently in the app and offers three alternatives for the main headings and explanations. The alternatives are suggestions only; the app still uses the **Current wording** until someone chooses and implements a revision.

The goal is to sound calm, clear, and honest. Explain that Taptation watches notification taps, distracting taps can trigger a catch and reduce meadow health, and important notifications should be protected. In automatic mode, the catch chance depends on the user's difficulty. **Always bait** is the exception: it skips AI and the probability roll, so an eligible tap is caught.

## Onboarding page 1 — Welcome

### Main title

**Current wording:** “A little more room”

1. “Make room for what matters”
2. “A calmer way to check notifications”
3. [x]“Give your attention a little space”

### Card title

**Current wording:** [x] “Make space for what matters.”

1. “Choose where your attention goes.”
2. “Make your notifications work for you.”
3. “Keep more room for your priorities.”

### Card explanation

**Current wording:** “Some distracting notification taps trigger a reminder and lower your meadow’s health. Important notifications are excluded.”

1. “Some distracting notification taps can trigger a catch reminder and lower your meadow’s health. Important notifications are protected.”
2. “When you open a distracting notification, Taptation may show a reminder and take some health from your meadow. Important notifications are not penalized.”
3. “Taptation helps you pause before opening distracting notifications. A catch can lower your meadow’s health; important notifications stay safe.”

The first alternative is the clearest small edit: it says **can** trigger a catch and uses “protected” to explain “excluded” in familiar language.

## Onboarding page 2 — Personal priorities

### Main title

**Current wording:** [x] “What matters to you?”

1. “What would you like to protect?”
2. “Which notifications matter most?”
3. “Tell Taptation what matters to you”

### Card title

**Current wording:** “Help the AI understand your priorities”

1. “Teach the AI what matters to you”
2. “Set your personal notification priorities”
3. “Help Taptation recognize what matters”

### Card explanation

**Current wording:** “These answers guide the AI for apps set to Let us decide. You can change them later in Settings.”

1. “Your answers help the AI judge notifications from apps set to Let us decide. You can edit them any time in Settings.”
2. “Tell us what you consider important or distracting. The AI uses this guidance for apps set to Let us decide.”
3. “These preferences guide automatic checks. They only affect apps set to Let us decide, and you can change them later in Settings.”

### Input labels and examples

**Current important label:** “What notifications are important in your life?”

1. “Which notifications should always matter to Taptation?”
2. “What kinds of notifications do you want to protect?”
3. “What messages would you not want Taptation to interrupt?”

**Current important example:** “Family messages, work shifts, delivery updates, appointment changes…”

1. “For example: family, work changes, deliveries, appointments, or travel updates.”
2. “For example: messages from people close to you, schedule changes, and security alerts.”
3. “For example: family news, work requests, delivery details, or health reminders.”

**Current non-essential label:** “What notifications are usually not important to you?”

1. “Which notifications would you like help ignoring?”
2. “What kinds of alerts usually pull you in without helping?”
3. “Which notifications can usually wait or be skipped?”

**Current non-essential example:** “Likes, follows, game rewards, sales, suggested posts…”

1. “For example: likes, suggested videos, routine rewards, and promotions.”
2. “For example: follower updates, streak reminders, sales, or suggested posts.”
3. “For example: activity counts, game rewards, shopping offers, or trends.”

### Privacy and availability note

The app currently shows different text depending on whether AI credentials are configured in the build. Keep those two cases accurate if the wording changes.

**Current, AI available:** “Your answers are saved on this device. When AI judging runs, Taptation sends your preferences and notification details to Cloudflare. Notification details are not saved by Taptation.”

1. “Your preferences stay on this device. To classify a notification, Taptation sends its details and your preferences to Cloudflare. Taptation does not save notification text.”
2. “Automatic checks use Cloudflare AI. The notification and your guidance are sent for the check; Taptation does not save the notification details.”
3. “Taptation stores your preferences on this device. Notification details are sent to Cloudflare only for an AI check and are not saved by Taptation.”

**Current, AI unavailable:** “Your answers are saved on this device. Automatic AI checks are not available in this build, so apps set to Let us decide will not be penalized automatically.”

1. “Your answers are saved here, but automatic AI checks are unavailable in this build. Apps set to Let us decide will not trigger an automatic penalty.”
2. “AI checking is not set up in this build. You can still choose Always important or Always distracting for an app in Settings.”
3. “Your preferences are saved, but they cannot guide automatic checks until AI is enabled. Apps set to Let us decide are not penalized automatically.”

## Onboarding page 3 — Choose app rules

Each app has three choices in Notification rules: **Always important** (safe; never penalized), **Always bait** (every notification tap considered as caught), and **Let us decide** (the default; AI classifies each notification as important or distracting, then the catch percentage applies to distracting results). Always bait skips AI and the catch-percentage roll. The normal freshness, paused-game, and catch-lockout protections still apply.

### Main title

**Current wording:** “Set rules for your apps”

1. “Choose how Taptation treats each app.”
2. “Protect, mark as distracting, or let Taptation decide.”
3. “Set a rule—or let AI decide—for each app.”

### Card title

**Current wording:** “Choose which apps are safe or always bait”

1. “Choose which apps are safe, distracting, or automatic”
2. “Always important, Always bait, or Let us decide”
3. “Choose a rule or use automatic decisions”

### Card explanation

**Current wording:** “Always important keeps an app safe. Always bait catches each eligible tap. Apps left on Let us decide are checked automatically; we explain that next.”

1. “Choose Always important to protect every notification from an app, Always bait to catch eligible taps without an AI check, or Let us decide to have AI classify each notification. In automatic mode, your catch chance applies to notifications AI marks as distracting.”
2. “For each app, choose safe, always bait, or automatic. Always bait catches every eligible tap; automatic mode asks AI whether each notification is important or distracting, then uses your catch percentage for distracting ones.”
3. “Set an app to Always important, Always bait, or Let us decide. Always bait skips AI and catches eligible taps. In automatic mode, AI judges the notification first and the catch setting controls the chance of a catch.”

### Additional note

**Current wording:** “You can choose safe apps later in Settings.”

1. “You can change an app’s rule any time in Settings.”
2. “No need to choose now; choose Always important, Always bait, or Let us decide later.”
3. “Later, choose which apps to protect, mark as distracting, or leave to automatic decisions.”

## Onboarding page 4 — What a catch does

### Main title

**Current wording:** “A tap can be a moment to pause.”

1. “Pause, then choose what to do next.”
2. “A catch gives you a moment to reset.”
3. “Notice the tap. Choose your next step.”

### Card title

**Current wording:** “A tap can be a moment to pause.”

1. “A catch creates a pause”
2. “Choose what happens after a catch”
3. “Take a breath before you continue”

### Card explanation

**Current wording:** “For apps on Let us decide, AI checks whether each notification is important or distracting. Important ones stay safe; distracting ones use your catch chance. Always bait will catches all notification tapped, while Always important stays safe. If caught, your meadow loses health and a reminder explains the cost.”

1. “For apps on Let us decide, AI first checks whether a notification is important or distracting. Important ones are safe; distracting ones use your catch chance. A catch lowers your meadow’s health and gives you a choice of where to go next.”
2. “AI helps sort notifications from apps on Let us decide. Important notifications are protected; distracting ones may catch you based on your setting. Always bait apps catch eligible taps, while Always important apps stay safe.”
3. “Taptation checks notifications from apps on Let us decide with AI. If one is distracting, your catch chance determines whether you get caught. The reminder shows the health cost and lets you continue to the app or open Taptation.”

For apps on **Let us decide**, keep “some,” “may,” or “can”: AI first classifies the notification, then the catch percentage determines whether a distracting tap is caught. For **Always bait**, the tap is caught whenever it is eligible; the app still honors the freshness limit, paused state, and catch lockout.

## Onboarding page 5 — Permissions and setup

### Main title

**Current wording:** “A couple of settings”

1. “Finish setting up Taptation”
2. “Let Taptation notice notification taps”
3. “One required permission, one optional extra”

### Introductory explanation

**Current wording:** “Taptation needs notification access to detect notification taps. Showing catch feedback over other apps is optional but recommended.”

1. “Notification access is required so Taptation can detect taps. Displaying catch reminders over other apps is optional.”
2. “Allow notification access to use Taptation. You can also allow the catch reminder to appear over other apps.”
3. “Taptation needs access to notice notification taps. The overlay permission lets a catch reminder appear without leaving the app you opened.”

### Permission cards

**Notification access — current:** “Lets Taptation notice when you open a notification. Message text is not stored.”

1. “Required. Lets Taptation detect when you open a notification; it does not save the message text.”
2. “Taptation needs this to recognize notification taps. Notification text is not stored.”
3. “Allow this so Taptation can respond to notification taps. The message content stays out of saved history.”

**Display over other apps — current title:** “Display over other apps”

1. “Show catch reminders over other apps”
2. “Catch reminder overlay”
3. “Show the reminder right away”

**Display over other apps — current explanation:** “Recommended. Lets catch feedback appear while you’re using another app. Without it, a notification opens the choice instead.”

1. “Optional. Shows the catch reminder over the app you opened. Without it, Taptation uses a notification when possible.”
2. “Optional. Lets you see the catch and choose what to do without switching apps first.”
3. “Allow this for an on-screen reminder. If it is off, the reminder may arrive as a notification instead.”

**Current return instruction:** “Return here after changing either setting. Taptation will check again automatically.”

1. “Come back to Taptation when you’re done. Your settings will update automatically.”
2. “After changing a permission, return here to continue setup.”
3. “Taptation will check your permission when you return to this screen.”

## Related in-app wording

### Settings — Catch reminders

**Current screen introduction:** “When you're caught, a reminder shows what it cost and lets you choose where to go next.”

1. “A catch reminder explains the health cost and lets you choose your next step.”
2. “Choose how Taptation shows a catch and where its buttons take you.”
3. “Set up the on-screen reminder and notification fallback for catches.”

**Current overlay card:** “Show reminders over other apps” / “Lets the reminder appear right away, on top of the app you opened.”

1. “Show catch reminders on screen” / “See the reminder over the app you opened.”
2. “Allow the catch overlay” / “Taptation can show the catch details without switching apps.”
3. “Display reminders over other apps” / “When allowed, the catch choice appears immediately on top.”

**Current fallback card:** “Allow reminder notifications” / “Used when a reminder can't appear over other apps.”

1. “Allow catch notifications” / “Taptation can use a notification if the overlay is unavailable.”
2. “Notification backup” / “Receive the catch reminder as a notification when it cannot appear on screen.”
3. “Show a notification instead” / “This is the fallback when Android or another app blocks the overlay.”

### Settings — Notification rules

**Current description:** “Choose which apps are always important or always bait. Let us decide uses your answers below.”

1. “Choose which apps are always safe, always distracting, or checked using your preferences.”
2. “Set a rule for each app. ‘Let us decide’ uses your personal criteria when AI checking is available.”
3. “Protect important apps, mark distracting apps, or let Taptation make an automatic decision.”

**Current app-rule explanation:** “Always important: never penalized. Always bait: eligible taps are always caught, without AI or a chance roll. Let us decide: AI classifies each notification, then the catch chance applies to distracting ones.”

1. “Always important protects every notification. Always bait catches eligible taps without AI. Let us decide uses AI to classify notifications, then your catch chance applies to distracting ones.”
2. “Choose Always important to stay safe, Always bait for a guaranteed catch on eligible taps, or Let us decide for AI classification and your selected catch chance.”
3. “You control each app: keep it safe, always catch eligible taps, or let AI decide whether each notification is distracting before applying the catch chance.”

## Suggested spoken demo script

This is a short narration a presenter can use while showing the onboarding and app. It describes the current behavior without implying that every tap causes a catch.

> “Taptation helps me make more room for the things I care about. For each app, I can choose Always important, Always bait, or Let us decide. Always important is safe. Always bait skips AI and catches eligible taps. Let us decide uses AI to classify each notification, then my catch setting controls the chance of a catch if it is distracting. The reminder shows the health cost and lets me continue to the original app or open Taptation. Notification access is needed to detect taps; the overlay permission is optional and lets the reminder appear on top of another app. If an automatic check cannot run, the app does not penalize that tap.”

## Suggested set to use together

If the team wants a consistent first draft, use these together:

- Page 1 title: “Make room for what matters”
- Page 1 card title: “Choose where your attention goes.”
- Page 1 description: “Some distracting notification taps can trigger a catch reminder and lower your meadow’s health. Important notifications are protected.”
- Page 2 title: “What matters to you?”
- Page 3 title: “Choose how Taptation treats each app.”
- Page 4 title: “Pause, then choose what to do next.”
- Page 5 title: “Finish setting up Taptation”

This keeps the copy direct and specific while preserving the current product behavior.

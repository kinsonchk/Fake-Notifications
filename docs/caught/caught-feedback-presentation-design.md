# Caught Feedback Overlay: Design Proposal

## Goal

When a person taps a bait notification and Taptation catches them, show a brief Taptation screen over the app that just opened. The screen should make the catch visible immediately and give the person a deliberate pause before they continue.

Because the user wants this behavior, Taptation should ask for the user's explicit **Display over other apps** permission during onboarding or when enabling the feature. Do not try to show an overlay before the person grants access.

## What Android can and cannot do here

The notification belongs to another app. Its own `PendingIntent` tells Android what to open when tapped. Taptation's notification listener observes the tap after Android has handled that action; it does not take ownership of or replace the other app's tap action.

The expected sequence is therefore:

```text
User taps Instagram / YouTube / another bait notification
        ↓
Android opens that app normally
        ↓
Taptation receives the notification click event
        ↓
Game logic checks whitelist and bait rules
        ↓
If caught, Taptation displays an authorized overlay above the opened app
        ↓
User sees “Caught!” and chooses when to continue
```

Android restricts background activity launches to prevent apps unexpectedly taking over the screen. A regular Taptation activity cannot be relied on to pop over the source app from a notification-listener callback. A system application-overlay window is the relevant design for this goal. Android requires the user to grant `SYSTEM_ALERT_WINDOW` access in Settings. Android describes this as a powerful capability intended for relatively few apps, so Taptation must explain it plainly and let the user disable it.

## Recommended experience

### Permission setup

Add an onboarding/settings option such as:

> **Show a catch pause over distracting apps**  
> If you are caught, Taptation will briefly cover the app you opened so you can notice the interruption. Android asks you to allow “Display over other apps.” You can turn this off at any time.

Use a button like **Enable catch overlay** to open Android's overlay-access settings. The permission is special access, not a normal runtime popup. Check `Settings.canDrawOverlays(context)` when Taptation resumes; do not assume access was granted. The user can later revoke it in Android Settings, so check again before every display.

This permission is separate from **Notification access**. Notification access detects the tap; overlay access displays the catch screen over the other app.

### Overlay content

Use a simple, opaque, high-contrast screen rather than a small floating bubble. It should be brief and understandable:

```text
You got caught
This notification pulled you away from your goal.
Your tree lost 10 health.

[Continue in this app]  [Open Taptation]
```

The overlay should:

- show only for a genuine `Caught` result;
- never show for a whitelisted app, a swipe, a non-bait tap, or a missed random roll;
- show the consequence already applied by the game engine, not apply damage a second time;
- provide two explicit choices: **Continue in this app** dismisses the overlay and leaves the source app open; **Open Taptation** opens Taptation's catch/progress screen;
- have an obvious way to dismiss it (no endless or hidden lockout);
- avoid showing the original notification's private title or message;
- close cleanly and release its window when dismissed, when the service is destroyed, or if permission is revoked.

Both choices should be available and clearly labeled. **Continue in this app** removes the overlay and returns interaction to the source app. **Open Taptation** starts Taptation's catch/progress screen with the already-saved result. Because the person deliberately tapped that button, this is a user-requested navigation action; still test it on supported Android versions. Do not make the overlay impossible to dismiss.

### Permission-denied and unavailable behavior

If the person declines or revokes overlay access:

1. Do not attempt to force an activity over the source app.
2. Keep the catch and health change saved normally.
3. Post a Taptation notification describing the catch, if Taptation's notification permission is allowed.
4. Show the catch result the next time Taptation is opened.
5. Offer a settings link to enable the overlay later.

Some Android system screens and apps may suppress or cover third-party overlays for security. The fallback above is required even after the user grants permission. Android also restricts background activity launches, so do not substitute an activity launch for the overlay and assume it will always work.

## Suggested architecture

Keep game rules, permission checks, and visual presentation separate:

1. `TapListenerService` continues to filter for actual notification clicks.
2. `GameRepository` processes the tap asynchronously.
3. After the future completes, only `TapOutcome.Caught` requests catch feedback.
4. A main-thread `CatchFeedbackCoordinator` checks overlay permission and asks an overlay controller to display the result. Otherwise it posts the fallback notification and records that an in-app catch screen should be shown next time.
5. `CaughtFeedbackManager` owns a `WindowManager` application-overlay view in the app process and removes it on dismissal or cleanup.

Conceptual Kotlin only (adapt to the project's API and lifecycle):

```kotlin
repository.processTap(context).whenComplete { result, failure ->
    if (failure == null && result?.outcome == TapOutcome.Caught) {
        mainHandler.post {
            if (Settings.canDrawOverlays(appContext)) {
                catchOverlay.show(result.state)
            } else {
                catchFeedbackNotifier.post(result.state)
            }
        }
    }
}
```

Do not call `.get()` or `.join()` inside the notification callback. The repository uses a worker thread; keep the listener callback non-blocking and display the result only after successful processing.

An overlay window is typically created with `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`. The manifest declares `android.permission.SYSTEM_ALERT_WINDOW`, but declaring it alone does not grant access; the user must approve it in Android's overlay settings. A Compose `ComposeView` can host the UI, or the team can start with a small native `View`. Keep the overlay controller responsible for adding/removing its window and avoid applying health damage there. For **Open Taptation**, the button handler should launch a catch-result destination (or the main activity with a catch-result route) from the user tap. If launching from a service context, include `FLAG_ACTIVITY_NEW_TASK`. Pass a result identifier or saved-state reference and read the authoritative result from the repository; do not apply the catch again.

## Important limits and safeguards

- Showing an overlay is an interruption by design. Ask permission with a specific explanation before opening Settings.
- Keep the overlay content branded as Taptation so it cannot be mistaken for part of Instagram, YouTube, or another app.
- Do not imitate the source app's login UI, notification, or controls.
- Do not intercept, cancel, or rewrite another app's notification tap.
- Keep the catch result correct even if overlay rendering fails.
- Respect a user setting to turn off catch overlays and use the fallback notification instead.
- Test on multiple Android versions and at least one physical phone. Overlay access screens and system behavior can vary by manufacturer.
- Do not use a full-screen notification intent for this purpose. Android reserves that intrusive mechanism for urgent, time-sensitive situations such as incoming calls and alarms.

## Demo plan

1. Install the debug app on the Android phone and grant **Notification access**.
2. In Taptation settings, read the overlay explanation, tap **Enable catch overlay**, and allow **Display over other apps** in Android Settings.
3. Return to Taptation and verify that overlay status shows enabled.
4. Configure a real installed app as bait and set the demo catch chance to 100%.
5. Make that app post a notification and tap it.
6. Confirm the source app opens, then the Taptation **You got caught** overlay appears over it.
7. Tap **Continue in this app** and confirm the overlay disappears and the source app remains usable. Repeat the caught tap and tap **Open Taptation**; confirm Taptation opens its catch/progress screen and the catch is not applied a second time.
8. Whitelist the app and repeat: its notification opens the app without a catch overlay.
9. Turn overlay access off in Android Settings and repeat a caught tap: no overlay should appear; the catch should still be recorded, and the fallback notification/in-app result should be available.
10. Swipe away a bait notification: no overlay should appear because a swipe is not a click.

## Recommendation

For the requested “stop me after I tap” experience, build the **opt-in application overlay** as the primary caught feedback, with a Taptation notification and in-app result as fallbacks. Keep the original app's notification action intact. The user-granted overlay permission is the explicit consent that makes the interruption understandable; the fallback makes sure game state and feedback still work when Android or the user does not allow the overlay.

## Android references

- [Background activity launch security](https://developer.android.com/guide/components/activities/secure-bal): Android's restrictions on activities launched from the background.
- [`SYSTEM_ALERT_WINDOW`](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW): application overlays and explicit user approval through Settings.
- [`Settings.canDrawOverlays`](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)): checking whether overlay access is currently granted.
- [Full-screen notification intents](https://developer.android.com/develop/ui/compose/notifications/create-notification#show-an-urgent-message): Android's intended use for urgent, time-sensitive events.



## Implementation status

The current app now has a **Catch overlay** switch in Settings. Turning it on opens Android's **Display over other apps** special-access page when needed. A caught result is emitted after `GameRepository` has saved it. `CaughtFeedbackManager` shows the full-screen overlay with **Continue in this app** and **Open Taptation** actions; if overlay access is unavailable or disabled, it posts a Taptation notification when notification permission allows and keeps a pending result for the next app launch. `CaughtResultActivity` reads the saved catch details and does not apply damage again.

The debug APK has compiled successfully. The overlay, Android permission grant/revocation, button behavior, and fallback still need manual verification on an emulator or phone using section C.2 of `docs/debug-controls-and-testing.md`.


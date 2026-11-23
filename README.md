# About Taptation
_Taptation_ is an Android app designed to help you break the habit of impulsively tapping on phone notifications. Instead, it helps you build the habit of always checking the context of a notification before tapping on it. The app aims to train you to avoid distracting social media notifications while ensuring you don't ignore important ones.

The app is able to automatically determine whether a notification is a distracting one or an important one based on its context, such as its text content, the app it was sent from, and whether it contains media (photos, reels, links, etc.). Users can also define their own criteria for what constitutes a distracting notification, as well as manage rules for individual apps.

The app monitors and keeps track of your taps on notifications. There is a gamification feature in the form of a health bar that shows how successful you have been at avoiding distracting notifications. The higher the health bar, the better. If you tap on a notification determined to be distracting, you may be "caught", in which case a pop-up prompt will appear and the health bar will decrease. The aim is to keep the health bar full, which will hopefully encourage users to stop impulsively tapping on notifications and to think before doing so each time moving forward.

<p align="center">
  <img alt="Screenshot_20261122-154826" src="https://github.com/user-attachments/assets/fe864b49-fd6f-4ec1-a41c-4eb27b87e49d" width="23%" />
  <img alt="Screenshot_20261122-154608" src="https://github.com/user-attachments/assets/dbed23aa-7caf-4f0b-9511-1122a43dc64d" width="23%" />
  <img alt="Screenshot_20261122-154943" src="https://github.com/user-attachments/assets/e2b2bce4-5c18-486c-9bd8-e4e5891f4740" width="23%" />
  <img alt="Screenshot_20261122-154731" src="https://github.com/user-attachments/assets/8e1b36cd-9265-4eda-8ba4-24389b62be2a" width="23%" />
</p>


# Our Inspiration
We've all opened our phones to check one thing and then ended up scrolling far longer than intended. Notifications from social media apps can make that especially easy. They are often designed to grab your attention to make you stick with the app for as long as possible.

Now, you could just enable "Do Not Disturb" on your phone to stop all notifications, but this would block the important ones too, like a text from a friend reminding you that you should study for your exam.

We believe it is important for people to be able to distinguish between distracting and important notifications. Simply turning on "Do Not Disturb" and silencing all notifications merely pushes the problem for later rather than solving it in the long term. This is why we wanted to build an app to help people practise choosing which notifications deserve their attention, and make that practice engaging and sustained through a gamification feature.


# How we built it
The app was developed using Android Studio (https://developer.android.com/studio) with Kotlin.

In order for the app to determine whether a notification is distracting or important, we need to use Cloudflare Workers AI (https://www.cloudflare.com/products/workers-ai/), which processes and analyses the context of the notifications sent to it.


# What's next
As this app was developed as a prototype over the course of just under two days during a hackathon, we could refine the user experience and add more features if we have time in the future.

We are also considering putting the app into production and releasing it on the Google Play Store. However, we would need to address privacy issues relating to notification data being processed by a third-party AI service.

Lastly, as _Taptation_ is an Android app, it only benefits Android users. We could also create an iOS version of the app for iPhone users.

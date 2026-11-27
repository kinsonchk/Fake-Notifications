> Historical, kept for reference: the app whitelist was replaced by per-app rules (Always important / Always bait / Let us decide); see [ui-reference/feature-per-app-rules.md](../ui-reference/feature-per-app-rules.md).

# App Whitelist Picker: User-Friendly Design

## The question

Seeing or typing a value such as `com.google.android.youtube` looks technical. Should a person need to know that value to protect YouTube?

**No.** Package names are Android's internal identifiers. They are useful to the app and to developers, but a normal user should choose **YouTube** from a list of installed apps.

## Recommended design

Show a simple **Whitelisted apps** section with installed apps as rows:

```text
Whitelisted apps
Choose apps whose notification taps are always safe.

[ Search apps...                 ]

[YouTube icon] YouTube                  [switch]
               com.google.android.youtube
[Instagram icon] Instagram              [switch]
                 com.instagram.android
[Messages icon] Messages                [switch]
                 com.google.android.apps.messaging
```

The person searches for or recognizes the app by its familiar name and icon, then turns on its switch. The package ID can be hidden from the normal screen, or shown in small secondary text only while the app is in its developer/debug view. The name and icon are for people; the package ID is the stable key used by the code.

A short explanation should appear above the list: **“Every notification tap from a selected app is safe from catches.”** This makes clear that the choice protects the whole app, not just one notification or message.

## How it works behind the screen

Android gives every installed app a package name, for example `com.google.android.youtube`. Taptation uses that value to identify which app posted a notification. The picker can ask Android for launchable installed apps and display each app's label and icon. When someone turns on a row, Taptation saves that app's package name in its whitelist settings.

In simplified terms:

```text
Person chooses “YouTube”
        ↓
Android identifies that row as “com.google.android.youtube”
        ↓
Taptation saves that ID in its whitelist
        ↓
A tapped YouTube notification bypasses the catch rule
```

This is similar to choosing a contact by their name while the phone stores a unique contact ID. The internal ID is needed for a reliable match; the person does not need to type or remember it.

## What the current project does

The whitelist picker has already been added to the debug/settings surface. It lists installed apps by their readable names, supports searching, and saves the selected package through the existing `GameRepository` whitelist setting. The package ID is also displayed in small text under the app name to help with debugging.

The text field labeled **Bait app package name (demo control)** is a separate developer control. It lets the demo setup mark an app as bait so the team can prove that whitelisting overrides a catch. It is not the way a regular user adds an app to the whitelist. For the whitelist, use the app list and its switch.

## User-facing version and debug version

For the current project/demo, the existing picker is a reasonable small solution. A polished normal settings screen can later:

- Keep the app name, icon, search, and switch.
- Hide package IDs unless a developer opens a details/debug view.
- Group selected apps near the top, or include a **Show only selected** filter.
- Keep the explanatory sentence that the exception covers every notification from that app.
- Show saved entries for apps that have since been uninstalled, with a remove action.

No new database is needed for these improvements. Continue storing package IDs in `GameConfig.whitelistedPackages` and changing them through `GameRepository.setWhitelisted`.

## Important platform choice

Only ask Android to reveal apps that have launcher icons, since that is enough for a person to pick common apps. The manifest should keep the narrow launcher-app query. Do not request broad access to every installed package. Apps without a launcher icon may not appear in this first version; that is an acceptable limitation for the simple picker.

## Recommendation

Use the installed-app picker as the normal way to add or remove whitelist entries. Keep package-name entry only as a developer tool for configuring bait during a controlled test. Store and match package IDs internally, while presenting recognizable app names to people.

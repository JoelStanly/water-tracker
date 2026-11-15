# Water Tracker

A personal Android water-reminder app built around one 1 L bottle.

## Install on your phone

Open this link on your Android phone and install the APK:

**https://github.com/JoelStanly/water-tracker/releases/latest/download/water-tracker.apk**

The first time, Android will ask you to allow installing apps from your browser. Allow it.
New versions install over the old one and keep your data.

## How it works

- **Logging:** after drinking, tap the bottle where the water level is now. The app works out how much you drank.
  Tap higher than the current level to top up; use **Finished & refilled** when the bottle is empty.
- **Goal:** automatic 2.0 L in the first week, then 2.5 L. +0.5 L when *Workout day* is switched on.
  You can set your own goal in Settings.
- **Reminders:** one interval (default 60 min) after your last drink, only between your wake-up time and bedtime.
  They stop once the day's goal is reached, and get more insistent if you haven't drunk in a while.
  **Snooze 15 min** is on the notification.
- If the orange banner shows, tap it to allow notifications / alarms / battery exemption so reminders arrive on time.

## Build

Every push to `main` builds the APK on GitHub Actions and publishes it as the latest release.

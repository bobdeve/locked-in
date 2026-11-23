# Focus Hours

An Android app that locks the apps you choose (Instagram, TikTok, Facebook, YouTube…) and only lets
you open them during the hours you set, for example 12:00–13:00 and 20:00–21:00.

## What it does
- Lists every app on your phone. Tap an app to lock or unlock it.
- **Allowed hours:** tap "+ Add time" to add a window, and tap a window to remove it. Windows can cross midnight.
- Outside those hours, opening a locked app shows a lock screen with "Go back to home screen".
- If an app is open when the allowed window ends, it gets locked within about 20 seconds.
- **Emergency pass:** 3 per day. Each gives 5 minutes, after a 15-second wait to break the autopilot habit.
- Everything stays on the phone. No internet permission, no accounts.

## Build and install (Android Studio)
1. Install Android Studio (free) on your laptop or MacBook.
2. **File → Open** and choose the `FocusHours` folder. Wait for "Gradle sync" to finish. The first time, it downloads what it needs.
3. Connect your phone with USB. On the phone, turn on **Developer options → USB debugging**.
   (Developer options: Settings → About phone → tap "Build number" 7 times.)
4. Press the green ▶ **Run** button. The app installs and opens.
   To get an APK file instead: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Turn on the lock (once)
Open Focus Hours → **Turn on app lock** → find "Focus Hours" in Accessibility → switch it on.
If the switch is greyed out (Android 13+ for apps not from the Play Store):
Settings → Apps → Focus Hours → ⋮ (top right) → **Allow restricted settings**, then try again.

## How it works
- `AppBlockerService.kt`: an Accessibility Service. Android tells it when a new app comes to the front.
  If that app is locked and it's outside the allowed hours, it opens the lock screen on top.
- `BlockActivity.kt`: the lock screen, with the emergency pass.
- `MainActivity.kt`: the settings screen with the app list and allowed hours.
- `Prefs.kt`: saves your choices on the phone and works out whether "now" is allowed.

## Limits
- You can still switch the lock off in Accessibility settings. It's a commitment tool, not parental control.
- Some phones (Xiaomi, Huawei, Oppo) stop background services to save battery. If the lock stops working,
  set Focus Hours' battery usage to "Unrestricted".

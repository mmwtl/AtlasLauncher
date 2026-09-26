# AtlasLauncher Android emulator QA

Date: 2026-09-25
Target: `app/build/outputs/apk/debug/app-debug.apk` (`com.mmwtl.atlaslauncher`)
Device: `emulator-5554`, Android emulator, 1440×1864 UI

## Results

- Installed the rebuilt APK and launched `HomeActivity`. The current HOME resolver lists AtlasLauncher. Starting the HOME intent opened Android's chooser; on the final build, choosing **Just once** returned to AtlasLauncher. A subsequent HOME resolve still returned `ResolverActivity`, so no persistent default launcher was set.
- Opened **Все приложения**, searched for `Clock`, and got a single matching entry. A long press added `Clock` to **Избранное**; tapping its favorite tile opened `com.google.android.deskclock/.DeskClock`.
- Opened the widget picker, selected the standard **Analog clock** provider, and accepted the per-widget **Create** prompt with **Always allow** unchecked. `dumpsys appwidget` reports the AtlasLauncher host bound to `com.google.android.deskclock/.AnalogAppWidgetProvider`.
- Force-stopped and relaunched AtlasLauncher. The Analog clock widget and Clock favorite both remained visible in the UI hierarchy, and the widget host binding remained registered.
- The emulator crash buffer was empty after the flow. No AtlasLauncher fatal exception was found in the inspected logcat output.

## Environment interference and limits

An already running `com.mmwtl.atlasmediawidget` overlay covered the launcher and app-list dialog. WindowManager identified the separate package, and its `SYSTEM_ALERT_WINDOW` app-op was allowed. After confirming the obstruction, its process was temporarily force-stopped without clearing its data; the overlay disappeared and the remaining interactions worked. That external app remains force-stopped in the emulator.

This verifies Android emulator behavior and the standard Android widget provider path. It does not verify any OEM launcher, automotive climate control, or vehicle top panel.

## Evidence

- Final HOME chooser and one-time return: `24-home-chooser-latest.png` / `.xml`, `25-home-from-chooser.png` / `.xml`.
- Search: `05-app-list.png` / `.xml`, `06-app-search-clock.png` / `.xml`.
- Favorite and launch: `17-home-favorite.png`, `17-returned-list.xml`, `18-favorite-launch.png` / `.xml`.
- Widget picker, permission prompt, binding, and restart persistence: `20-widget-picker.png` / `.xml`, `21-widget-bind.png` / `.xml`, `22-widget-bound.png` / `.xml`, `23-after-force-stop.png` / `.xml`.
- Logs: `01-launch-logcat.txt`, `25-crash-logcat.txt`.

# AtlasLauncher portrait QA

Date: 2026-09-25

- Device: `emulator-5554`, Android 11, 1440×1920 at 160 dpi. It was already in portrait before installation and remained there.
- Installed `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`; installation succeeded without clearing app data. Installed package reports versionCode 2, versionName 0.2.0.
- Final HOME capture: [home-final-retry.png](home-final-retry.png). `Analog clock` is still rendered as an AppWidgetHostView and the `Clock` favorite remains visible after the update.
- The application catalog opened and displayed its app grid and search field: [app-catalog.png](app-catalog.png).
- The add-widget button opened the provider picker. I added `Digital clock`, verified it rendered beside `Analog clock`, then removed that test widget. The original Clock favorite and Analog clock stayed in place. Evidence: [widget-picker.png](widget-picker.png), [widget-added.png](widget-added.png).
- The final layout build was reinstalled with `-r`. HOME remained portrait and the saved widget and favorite were present. The [initial screenshot](home-initial-gray.png) taken about two seconds after launch was a flat gray frame while the UI tree was populated; a later frame [rendered normally](home-final-retry.png). The app process remained alive and `HomeActivity` stayed focused; no crash was observed in the checked log output.
- The visible panels end around y=776 on the 1920 px screen, leaving a large empty area below. The saved widget and favorite are not clipped.

This verifies emulator rendering only; it does not establish behavior on an OEM launcher or head unit.

## Restart rendering check

After `am force-stop`, I started `HomeActivity` and captured frames at about 2 s and 5 s. Both frames rendered HOME normally; the flat gray frame did not recur. The process remained alive and focused, and the UI tree still contained Analog clock and Clock. Evidence: [restart-2s.png](restart-2s.png), [restart-5s.png](restart-5s.png), [restart-ui.xml](restart-ui.xml). No code or app data was changed for this check.

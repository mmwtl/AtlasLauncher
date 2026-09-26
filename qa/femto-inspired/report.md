# AtlasLauncher emulator visual QA

APK: `app/build/outputs/apk/debug/app-debug.apk`, version 0.2.0 (code 2)
Device: `emulator-5554`, portrait, 1440×1920

## Findings

- HOME launched and rendered without clipping or overlapping controls. The widget section, add button, favorites section, catalog button, header, and clock are visible in the saved screenshot and UI hierarchy.
- **Все приложения** opened the catalog. Its title, close button, search field, and app grid were present in the hierarchy; the screenshot shows the centered catalog over the dimmed HOME screen.
- Long-pressing Maps added it to favorites. Maps and the pre-existing Clock favorite appeared on HOME.
- The **＋** widget button opened the picker, which listed providers including Analog clock, Search, Calendar, and Chrome.
- After `force-stop`, `HomeActivity` resumed. Analog clock and Search widgets, plus Clock and Maps favorites, remained visible. The crash buffer was empty and no AtlasLauncher fatal exception was found in logcat.
- Final `artifacts/AtlasLauncher-0.3.0-debug.apk` installed over the app as version 0.3.0/code 3; HOME started with both widgets and both favorites preserved, with no crash entries (`07-final-0.3.0-home.png` / `.xml`).

No concrete visual or startup defect was observed in this portrait emulator pass.

## Evidence

- HOME: `01-home.png` / `.xml`.
- Catalog: `02-catalog.png` / `.xml`.
- Added favorite: `04-home-after-pin.png` / `.xml`.
- Widget picker: `05-widget-picker.png` / `.xml`.
- Restart persistence: `06-after-force-stop.png` / `.xml`, `06-crash-logcat.txt`.

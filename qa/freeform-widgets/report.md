# Freeform widgets emulator QA

Target: `app/build/outputs/apk/debug/app-debug.apk` (version 0.3.0, code 3)
Device: `emulator-5554`, portrait, 1440×1920. Updated with `adb install -r`; app data was retained.

## Results

- Before the update, the existing widget JSON and AppWidgetManager state held Analog clock (id 2), Search (id 5), and AIMP (id 7). On the refreshed build, after force-stop, launch, and a two-second wait with no UI input, all three rendered in normal mode. The widgets did not overlap; crash buffer was empty.
- **Изменить** showed host views, outlines, drag targets, and resize controls. Analog clock, Search, and AIMP contents were populated.
- Dragged Search from `[18,456]` to `[432,926]`; its new bounds persisted after **Готово**, force-stop, and relaunch. Resized Search horizontally from 391 px to 496 px; the Google Search content expanded with it. Search remained 96 px tall.
- In normal mode, tapping the Search field opened `com.google.android.googlequicksearchbox/.SearchNowActivity`; Back returned to AtlasLauncher.
- **＋** opened the widget picker. Added Digital clock (AppWidget id 8); it appeared at `[18,1034]` with live clock/date content. Existing widgets remained. No widget was removed.
- After repositioning and resize, force-stop/relaunch showed AIMP `[18,666]–[268,762]`, Analog clock `[404,702]–[574,872]`, and Search `[432,926]–[928,1022]`. AppWidgetManager retained all three original IDs.

## Evidence

- Before update: `00-before-update.png` / `.xml`.
- Refreshed build on first launch: `08-fresh-home-after-restart.png` / `.xml`.
- Edit controls: `09-edit-mode-new.png` / `.xml`.
- Search drag: `10-edit-after-drag-search.png` / `.xml`.
- Search resize: `11-search-resized-new-build.png` / `.xml`.
- After **Готово**: `12-home-done.png` / `.xml`.
- Search interaction: `13-search-tap.png` / `.xml`.
- Position persistence after restart: `15-after-force-stop.png` / `.xml`.
- Add flow and new widget: `16-add-picker.png`, `17-add-digital-clock.png`, `18-after-add-digital-clock.png` and corresponding `.xml` files.

## Final build 0.4.0 (code 4)

- Installed `artifacts/AtlasLauncher-0.4.0-debug.apk` with `adb install -r`; package reports versionName `0.4.0`, versionCode `4`. Data was retained.
- After force-stop and relaunch, all four widgets rendered: AIMP `[18,666]–[268,762]`, Analog clock `[404,702]–[574,872]`, Search `[432,926]–[928,1022]`, and Digital clock `[18,1034]–[318,1204]`. Search stayed at its saved position. AppWidgetManager kept IDs 2, 5, 7, and 8; host reports `widgets.size=4`, `zombie=false`.
- In edit mode, narrowed Search to `[432,926]–[751,1022]` (319×96 px). The delete button was `[703,926]–[751,974]`; the 48×48 dp resize button was `[703,974]–[751,1022]`. Their bounds meet at y=974 without overlapping. Restored Search to `[432,926]–[928,1022]` before exiting edit mode.
- After **Готово**, force-stop, and relaunch, all four widgets remained visible at the saved bounds. Crash buffer was empty. No widget was removed.

Evidence: `19-v040-after-force-stop.png` / `.xml`, `19-v040-appwidget.txt`, `19-v040-crash-buffer.txt`; `20-v040-edit-mode.png` / `.xml`; `21-v040-search-narrow.png` / `.xml`; `22-v040-search-restored-edit.xml`, `23-v040-restored-edit.xml`; `24-v040-final-after-edit-force-stop.png` / `.xml`, `24-v040-final-appwidget.txt`, `24-v040-crash-buffer.txt`.

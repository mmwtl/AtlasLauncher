# Кнопка «Все приложения» на панели климата

Дата: 2026-10-01. Устройство: эмулятор `emulator-5554` (портрет 1440×1920), APK `0.5.0[15]AtlasLauncher-debug.apk`.

## Проверено

- Сборка `assembleDebug` успешна; `lintDebug` — только ожидаемая `ExpiredTargetSdkVersion`, число предупреждений не изменилось (13).
- `am start -n com.geely.atlaslauncher/com.mmwtl.atlaslauncher.HomeActivity --ez open_all_apps true` при запущенном AtlasLauncher (`onNewIntent`) открывает действие кнопки «Все приложения» дока — встроенный каталог при настройке по умолчанию.

## Не проверено

- Распознавание нажатия кнопки панели: на эмуляторе нет OEM-плагина SystemUI. Признак (первый элемент `rv_nav_sub` в `com.ecarx.systemui.plugin`, событие `TYPE_VIEW_CLICKED`) выведен из декомпиляции `oneOS_SystemUIPlugin.apk`; на ГУ нужно проверить, что событие приходит, `getViewIdResourceName()` возвращает `…:id/rv_nav_sub`, а «Домой» и другие кнопки не путаются с «Все приложения».
- Запуск выбранной Activity (например, GLauncher) из этого пути и холодный старт AtlasLauncher с extra.

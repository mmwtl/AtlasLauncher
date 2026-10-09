# Запуск DIM-службы приборки

## Проблема

После загрузки с Atlas в роли HOME BT-информация не появлялась на приборке, пока кнопка Home на климатической панели не открывала штатный Launcher3.

## Причина

`com.geely.dimservice/.service.DimService` (передача медиа/телефона на приборку, `impl/MediaImpl`) не стартует при загрузке сам: в нём нет `BOOT_COMPLETED`, запускает его `Launcher.onCreate()` штатного Launcher3 через `startService`. Служба экспортирована, без permission и проверки вызывающего. `com.geely.inputservice/.InputService`, который Launcher3 тоже запускает, уже поднимает при загрузке `com.geely.service.oneosapi`, поэтому Atlas его не трогает.

## Изменение

`HomeActivity.onCreate` запускает `DimService` так же, как Launcher3.

## Проверка

- ГУ `G636`, OneOS `1.0.20250623G(312)`, release `0.5.0[15]` с изменением, 2026-09-29.
- До изменения в этой загрузке `dumpsys activity services com.geely.dimservice` показывал `recentCallingPackage=com.android.launcher3` (после нажатия Home на климате).
- `am force-stop com.geely.dimservice`, `am force-stop com.geely.atlaslauncher`, запуск HOME → служба создана заново, `recentCallingPackage=com.geely.atlaslauncher`, `isForeground=true`.
- Эмулятор: пакета нет, `startService` лишь пишет «not found», Atlas работает.

## Ограничения

Отображение на приборке после холодной загрузки без нажатия Home пользователь ещё не подтвердил.

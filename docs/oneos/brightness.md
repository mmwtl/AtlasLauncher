# Яркость экрана и приборки

Разбор `PowerService.apk`, `autolink.adapterservice.jar` и `ecarx.adaptapi.jar` прошивки G636, 2026-10-16. `PowerService.apk` на ГУ побайтно совпадает с копией в `reference-apks`.

## Служба `com.autolink.powerservice`

Обе яркости — экрана и приборки — задаёт системная служба `com.autolink.powerservice/.PowerService` (uid 1000, action `com.autolink.powerservice.PowerService`). Шторка и системные настройки обращаются к ней через adaptapi (`SETTING_FUNC_BRIGHTNESS_SCREEN` = 688063744, `SETTING_FUNC_BRIGHTNESS_DIM` = 687998208). GInputBridge обращается так же, через `ICarFunction`. Служба экспортирована без permission и не проверяет вызывающего, поэтому привязаться к ней может обычное приложение. Для targetSdk 30 нужен `<queries>` с её пакетом. В `ServiceManager` её нет, так что `adb shell service call` к ней не применить.

Интерфейс `com.autolink.adapterbinder.IPower`, нужные вызовы:

| Код | Метод |
| --- | --- |
| 1 / 2 | `registerPowerCallback` / `unregisterPowerCallback(IPowerCallback)` |
| 3 / 4 | `setScreenBrightness(int)` / `getScreenBrightness()` |
| 5 / 6 | `setDisBrightness(int)` / `getDisBrightness()`: приборка |

Колбэк `com.autolink.adapterbinder.IPowerCallback`: код 1 — `onScreenBrightnessChanged(int)`, 3 — `onDisBrightnessChanged(int)`. Все вызовы двусторонние. При регистрации служба сразу сообщает уровень приборки.

## Уровни

Обе яркости задаются уровнями 0–15, как два ползунка в шторке. Служба переводит уровень в проценты по своим таблицам:

- экран: `1, 5, 9, 11, 13, 15, 18, 22, 27, 32, 39, 47, 57, 69, 83, 100`;
- приборка: `6, 7, 9, 11, 13, 17, 21, 26, 32, 40, 50, 62, 78, 90, 95, 100`.

Границы уровня служба не проверяет: значение вне 0–15 даёт `ArrayIndexOutOfBoundsException` у неё внутри. Уровень экрана, которого нет в таблице, приходит в колбэк как −1. `setDisBrightness` применяет уровень через 50 мс и сохраняет его в профиль водителя.

`onScreenBrightnessChanged` приходит, когда дисплей подтвердил уровень, заданный через `setScreenBrightness`, и при каждом изменении в автоматическом режиме. Если в конфигурации машины есть датчик дождя и света, в автоматическом режиме приходят только уровни 1, 4, 8, 12 и 14.

## Как это использует Atlas

Переключатель «Приборная панель» → «Яркость как у экрана» привязывается к службе из `HomeActivity` и выставляет приборке тот же уровень, что у экрана: сразу при включении и затем по каждому `onScreenBrightnessChanged`.

На ГУ подтверждено (2026-10-16): приборка меняется вслед за ползунком яркости экрана. Подробных логов `DispCtrl` на этой прошивке не видно (`persist.log.tag=ERROR`); ошибок от Atlas и службы при проверке не было. Поведение в автоматическом режиме выведено только из кода.

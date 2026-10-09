# Сигнал запуска HOME для LifeControlService

## Проблема

С Atlas в роли HOME после загрузки не стартуют OEM-службы, которые штатно поднимаются после появления Launcher3 (в загрузке 2026-09-30 `com.geely.hvac`, `com.geely.settings`, `com.kotei.trackball` запустились только на ~86-й секунде, когда Launcher3 открылся по кнопке «Домой» на панели).

## Причина

Launcher3 (`com.geely.view.common.listener.SimpleLauncherCallback`) в первом `onResume` после `onCreate` шлёт неявный broadcast `com.android.launcher3.startup`. Его ловит динамический receiver постоянной службы `com.geely.lifeControl` (`/system/app/LifeControlService`, без permission; broadcast не защищённый) и выполняет третий этап из `/system/etc/whiteListPersistentFilter.cfg`: `startService` для `inputservice`, `hvac`, `dimservice` (`GEELY_DIM_MENU_SERVICE`), `geely.settings`, `kotei.trackball`, `screengestureservice`, `energymanagement`, записывает свойство `com.android.launcher3.startup=7` и через 5 с заново применяет ночную тему из `persist.sys.theme`. Atlas этот сигнал не слал.

## Изменение

`HomeActivity` шлёт тот же broadcast в первом `onResume` после `onCreate`, как Launcher3. Повтор безопасен: служба пропускает уже запущенные пакеты этапа.

## Проверка

- ГУ `G636`, OneOS `1.0.20250623G(312)`, release `0.5.0[15]` с изменением, 2026-09-30.
- До изменения (загрузка 22:13): сигнала от Atlas нет, `com.android.launcher3.startup` пустое через минуту после загрузки; `hvac`, `settings`, `trackball` запустились только после открытия Launcher3 по кнопке «Домой» на панели (~86 с аптайма).
- После изменения (`adb install -r`, `adb reboot`, загрузка 22:19): Launcher3 не запускался; Atlas стартовал на 19,8 с, `LifeCtlReceiver action = com.android.launcher3.startup` через ~1,3 с после старта `LifeControlService`, `recordList.size() = 7`; `hvac` 22,3 с, `dimservice` 22,1 с, `settings` 22,4 с, `trackball` 22,5 с; свойство `com.android.launcher3.startup=7`, `sys.uimode.pending=false`. `energymanagement` после загрузки не работает — так же, как при старте через Launcher3.
- Эмулятор: receiver'а нет, broadcast уходит впустую.

## Ограничения

Как изменение влияет на длительность бут-лого, не измерено: на ГУ логи урезаны до ERROR (`persist.log.tag=ERROR`), событий `boot_progress` нет. Связи сигнала с лого в коде `LifeControlService` нет.

# Климатическая панель и док OneOS

Разбор `oneOS_SystemUIPlugin.apk` и `SystemUI.apk` G636 `1.0.20250623G(312)`, 2026-09-29. Панель рисует OEM-плагин SystemUI `com.ecarx.systemui.plugin`; HOME ею не управляет.

## Как плагин выбирает состояние панели

Фреймворк при смене фокуса вызывает `CommandQueue.windowChange(Bundle)` в SystemUI (`DisplayPolicy.updateSystemUiVisibilityLw`; в Bundle — `title`, `type`, `displayId`, `systemUiVisibility`, `packageName` окна в фокусе). Дальше `MainActivity.onWindowChange` плагина → `DynamicControlManager.processDockByWindowConfig`:

| Условие | Состояние |
| --- | --- |
| пакет в `needShowDockAppList` (там есть `com.android.launcher3`) | `DOCK_SHOW_ALL` — полная панель |
| пакет в `needHideDockCPAA` | панель скрыта |
| пакет в `needHideDockAppList` **или** имя не начинается с `com.android` / `com.geely` | `DOCK_SHOW_LINE` — свёрнутая полоса |
| иначе | по `systemUiVisibility`: `HIDE_NAVIGATION` — скрыта, иначе полная |

Поэтому при пакете `com.mmwtl.atlaslauncher` панель всегда была свёрнутой, и никакие флаги окна этого не меняли. Atlas получил `applicationId` `com.geely.atlaslauncher` (Java-пакет и `namespace` остались `com.mmwtl.atlaslauncher`); на ГУ 2026-09-29 с ним показывается полная панель — окно `geely_dock` высотой 124 px внизу экрана 1440×1920. Отсюда же константа `CLIMATE_PANEL_PX` в физических пикселях.

Скрытие панели в настройках Atlas использует последнюю строку таблицы: Activity выставляет `SYSTEM_UI_FLAG_HIDE_NAVIGATION` с `SYSTEM_UI_FLAG_IMMERSIVE_STICKY`, и для пакета `com.geely.*` плагин скрывает панель. Сетка тогда заканчивается в 24 px от низа вместо 150. На ГУ не проверено.

Тупиковые пути:

- `IDockBarService.showDock()` в этой сборке плагина только вызывает QNX `handleOverDock(0)`; маршрут `DockBarManager.showDock` из GInputBridge, на который рассчитывал [план прототипа](../archive/prototype-plan.md), ничего не даёт.
- `AppWatcherService` плагина — мёртвый код, в манифесте его нет.
- `ECarXCarDockbarManager` из `ecarx.car` умеет только `CB_CLIMATEAPP_SHOW`, видимостью дока не управляет.

## Кнопки «Домой» и «Все приложения»

Обе кнопки вызывают `JumpUtils.jumpToHome`, который явно запускает `com.android.launcher3/com.android.launcher3.Launcher`, а не текущий HOME. Кнопки отличаются только extra `screenId` (0 — «Домой», 2 — «Все приложения»). Обычное приложение этот интент перенаправить не может.

Atlas перехватывает их необязательной службой специальных возможностей `StockHomeRedirectService` ([как включить](../guide/settings.md#кнопки-домой-и-все-приложения)):

- **По нажатию.** У кнопок нет ни подписи, ни описания, поэтому служба узнаёт их по положению: «Домой» — первый элемент `rv_nav_main` (`itemId=100`, `dock_main`), «Все приложения» — первый элемент `rv_nav_sub`. Обе закреплены (`canMove=0` в `dockPresetData.json`, `SubAreaViewModel`). Для этого нужны `TYPE_VIEW_CLICKED` в пакетах `com.ecarx.systemui.plugin` и `com.android.systemui` и `canRetrieveWindowContent`. Плагин успевает запросить Launcher3, но Atlas стартует следом и оказывается сверху — Launcher3 не мелькает. Подтверждено на ГУ ATLAS ОД 2026-10-05 ([отчёт](../../qa/climate-home-click/report.md)).
- **Запасной путь.** Когда на экране всё же появляется окно `com.android.launcher3.Launcher`, служба открывает Atlas. В течение 2 с после открытия «Все приложения» он не срабатывает, чтобы не закрыть только что открытый каталог; «Открыть штатный Launcher3» из настроек Atlas не перехватывается 5 с.
- **Каталог поверх приложения.** Нажатие на «Все приложения» выводит задачу Launcher3 вперёд на том дисплее, где она уже есть. Если окно Launcher3 есть на дисплее, отличном от основного, а так бывает после прогрева (см. ниже), закрывать на основном экране нечего. Тогда служба открывает каталог (`AllAppsActivity`, отдельная задача в стиле диалога) или выбранную Activity поверх текущего приложения, не открывая Atlas. Иначе открывает Atlas, как раньше. Дисплей определяется по `getWindowsOnAllDisplays()`. Этот список служба получает только с флагом `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, который выставляется при включённой функции «Все приложения». Повторное нажатие закрывает каталог. На ГУ не проверено: в том числе, попадают ли окна дисплея 1 в этот список.
- **Прогрев.** Первое после загрузки нажатие всё равно показывало недогруженное окно Launcher3. Поэтому при включённой функции «Домой» Atlas через 2 с после первого кадра открывает Launcher3 на дисплее 1, который никуда не выводится ([дисплеи](displays.md)). Последующие нажатия выводят задачу Launcher3 вперёд там же, на основном экране его не видно, а служба по запасному пути возвращает Atlas. «Открыть штатный Launcher3» в настройках явно запускает его на дисплее 0. Подтверждено на ГУ ATLAS ОД 2026-10-10: холодная загрузка без мелькания, «Домой» и «Все приложения» возвращают Atlas за 25–35 мс ([лого загрузки](boot-logo.md#прогрев-launcher3-на-невидимом-дисплее)).

Особенности Android 11:

- Включать и выключать службу состоянием компонента (`setComponentEnabledSetting`) нельзя: `AccessibilityManagerService` на API 30 не перечитывает службы при изменении отдельного компонента. Поэтому служба всегда объявлена, а при выключенных функциях запрашивает пустой набор `eventTypes`.
- `am force-stop` убирает службу из включённых и держит её «упавшей» до перезагрузки или обновления APK; после обычного сбоя процесса она подключается сама.

Проверка на эмуляторе с OEM Launcher3 — [отчёт](../../qa/stock-home-redirect/report.md); OEM SystemUI и плагин на эмулятор не ставятся (платформенная подпись, `windowChange` есть только в OEM-фреймворке).

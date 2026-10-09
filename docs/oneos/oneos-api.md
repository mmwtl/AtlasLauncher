# OneOS API, климат и прочие службы

Разбор `oneOS_Hvac.apk`, `oneOS_ApiService.apk` и jar фреймворка G636, 2026-09-29.

## OneOS API (`com.geely.service.oneosapi`)

Экспортированная служба `OneOSApiService`: `getService(type)` и `addService` не проверяют вызывающего (белый список есть только у `getPermissionService`). Большинства служб, которые она отдаёт (погода, навигация, энергия), на G636 нет, так что брать оттуда почти нечего. Зато через `addService` можно зарегистрировать свою службу — так работает [обход плашки в строке состояния](status-bar.md).

`com.geely.inputservice/.InputService` OneOS API сам запускает при загрузке, поэтому HOME его запускать не нужно.

## Климат (`com.geely.hvac`)

- Экспортированный провайдер `content://com.geely.hvac/hvac/query` без permission: `projection[0]` = `open`, `close` или `getVisibility` открывает, закрывает или проверяет полноэкранный климат `GlyMainActivity`.
- Четыре AppWidget пакета (ароматизатор, массаж сидений) — обычные провайдеры, их можно разместить в Atlas.

## Прочее

- Проверки на Launcher3 в ATMS и `ActivityStack` касаются разделённого экрана, пассажирского экрана и анимаций переходов; логика HOME опирается на `isActivityTypeHome()`, поэтому Atlas в роли HOME работает нормально.
- Связь HOME с загрузкой: [лого](boot-logo.md), [этап запуска служб](launcher-startup-stage.md), [DIM-служба](dim-service.md).

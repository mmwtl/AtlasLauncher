# Проверка AtlasLauncher на ГУ OneOS и возврат к штатному HOME

Статус: протокол подготовлен, **на ГУ не выполнялся**. Сейчас `adb devices` показывает только эмулятор. Целевое ГУ — Android 11; APK для проверки: `artifacts/AtlasLauncher-0.4.0-debug.apk` (`com.mmwtl.atlaslauncher`, versionCode 4, targetSdk 30). Все команды ниже выполняются на компьютере из корня этого репозитория. В машине проводить проверки на стоянке.

Главное правило: **не удалять, не отключать и не очищать данные `com.android.launcher3`, `com.android.systemui`, `com.ecarx.systemui.plugin` или GInputBridge**. Для смены HOME не нужны root, запись в `/system`, копирование OEM APK на устройство или смена системных разрешений. Наличие root ADB оставляем как запасной доступ, а не как повод менять системные разделы.

## 0. Подключение и фиксация исходного состояния

Подставить реальный serial из `adb devices -l`. Все дальнейшие команды адресованы ему явно, чтобы не попасть в эмулятор.

```sh
cd /Users/wital/dev/AtlasLauncher
ADB=/Users/wital/Library/Android/sdk/platform-tools/adb
"$ADB" devices -l
GU_SERIAL='ВСТАВИТЬ_SERIAL_ГУ'
"$ADB" -s "$GU_SERIAL" get-state
"$ADB" -s "$GU_SERIAL" shell getprop ro.build.version.release
"$ADB" -s "$GU_SERIAL" shell getprop ro.build.version.sdk
GU_USER=$("$ADB" -s "$GU_SERIAL" shell am get-current-user | tr -d '\r')
printf 'Current user: %s\n' "$GU_USER"
```

Продолжать, только если serial однозначен, устройство отвечает `device`, SDK = `30`, `GU_USER` — число, а на экране работает штатный HOME. Если `adb root` уже включён, это не меняет последовательность; **не делайте `adb root` ради обычных команд**.

```sh
GU_LOG="qa/head-unit-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$GU_LOG"
"$ADB" -s "$GU_SERIAL" shell getprop ro.build.fingerprint > "$GU_LOG/build-fingerprint.txt"
"$ADB" -s "$GU_SERIAL" shell wm size > "$GU_LOG/display-size.txt"
"$ADB" -s "$GU_SERIAL" shell wm density > "$GU_LOG/display-density.txt"
"$ADB" -s "$GU_SERIAL" shell cmd package help > "$GU_LOG/package-help.txt"
"$ADB" -s "$GU_SERIAL" shell cmd role help > "$GU_LOG/role-help.txt"
"$ADB" -s "$GU_SERIAL" shell cmd package query-activities --user "$GU_USER" --components -a android.intent.action.MAIN -c android.intent.category.HOME > "$GU_LOG/home-candidates.txt"
"$ADB" -s "$GU_SERIAL" shell cmd package resolve-activity --user "$GU_USER" --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$GU_LOG/home-before.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys role > "$GU_LOG/roles-before.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys activity activities > "$GU_LOG/activities-before.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys window > "$GU_LOG/windows-before.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys appwidget > "$GU_LOG/widgets-before.txt"
"$ADB" -s "$GU_SERIAL" exec-out screencap -p > "$GU_LOG/stock-home.png"
```

Открыть `home-before.txt` и `roles-before.txt`. Записать **пакет прежнего владельца роли HOME** (далее `ORIGINAL_HOME`) и проверить, что он установлен и включён для `GU_USER`:

```sh
ORIGINAL_HOME='com.android.launcher3'
"$ADB" -s "$GU_SERIAL" shell pm list packages --user "$GU_USER" "$ORIGINAL_HOME"
"$ADB" -s "$GU_SERIAL" shell pm list packages -d --user "$GU_USER" "$ORIGINAL_HOME"
```

`com.android.launcher3` — значение из локального OEM APK, **не замена фактическому результату на ГУ**. Если исходный владелец другой, использовать именно его. Если роль HOME, выбранный Activity и видимый штатный экран противоречат друг другу, остановиться и выяснить OEM-механику до установки.

Штатная Activity в изученном APK — `com.android.launcher3/.Launcher`. До переключения проверить, что её можно открыть напрямую и что после Home она остаётся доступной:

```sh
"$ADB" -s "$GU_SERIAL" shell am start --user "$GU_USER" -n com.android.launcher3/.Launcher
"$ADB" -s "$GU_SERIAL" shell input keyevent KEYCODE_HOME
```

Если OEM изменил Activity или команда не сработала, узнать актуальный компонент из `home-before.txt`/`home-candidates.txt`; не полагаться на приведённый пример. Сохранить этот файл, serial и команды отката рядом с устройством. Перед продолжением убедиться, что ADB остаётся доступен при открытом штатном HOME.

До первой проверки Atlas как обычного приложения никакие команды смены HOME не выполнять.

## 1. Установка без переключения HOME

Установить APK удобным способом на ГУ. Если удобнее через ADB:

```sh
shasum -a 256 artifacts/AtlasLauncher-0.4.0-debug.apk
"$ADB" -s "$GU_SERIAL" install -r artifacts/AtlasLauncher-0.4.0-debug.apk
"$ADB" -s "$GU_SERIAL" shell dumpsys package com.geely.atlaslauncher > "$GU_LOG/atlas-package.txt"
```

Ожидаемый SHA-256 APK: `7feae925769d6c2ad93fdd1c63c3ecad9d665c786a5fd0d785663c2a4a297635`. `install -r` сохраняет данные существующей установки. При `INSTALL_FAILED_UPDATE_INCOMPATIBLE` **остановиться**: это несовпадение подписи; не делать `uninstall` или `pm clear`, если нужно сохранить виджеты/настройки старой установки.

**Открыть Atlas нажатием на его ярлык в системном списке приложений.** ADB-команда `am start` для этого не нужна. Пока HOME не назначен, проверить старт Atlas, каталог приложений, добавление одного обычного виджета, его нажатие, перенос, изменение размера, возврат в штатный HOME по физической/штатной кнопке Home. Сделать скриншот; отдельно снять верхнюю панель и климатический dock при открытом Atlas. Если уже здесь панель исчезает или виджет ломается, закрыть Atlas, нажать Home и собрать логи. Штатный HOME при такой проверке не менялся.

```sh
"$ADB" -s "$GU_SERIAL" exec-out screencap -p > "$GU_LOG/atlas-before-home-role.png"
"$ADB" -s "$GU_SERIAL" shell dumpsys window > "$GU_LOG/windows-atlas-before-role.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys appwidget > "$GU_LOG/widgets-atlas-before-role.txt"
```

## 2. Короткая проба Atlas как HOME

Этот этап **не нужен для проверки Atlas по ярлыку**. Выполнять его, только если отдельно нужно проверить Atlas как назначенный HOME после успешных шагов 0–1 и при сохранённом ADB-доступе. Сначала проверить основной путь отката, повторно назначив уже выбранный штатный HOME; команда должна вернуть `Success`, а экран и роль остаться штатными:

```sh
"$ADB" -s "$GU_SERIAL" shell cmd package set-home-activity --user "$GU_USER" "$ORIGINAL_HOME"
"$ADB" -s "$GU_SERIAL" shell input keyevent KEYCODE_HOME
"$ADB" -s "$GU_SERIAL" shell cmd package resolve-activity --user "$GU_USER" --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Если повторное назначение не проходит или меняет поведение штатного экрана, не переключать HOME на Atlas. `set-home-activity` меняет системную роль HOME для указанного пользователя; повторное назначение `ORIGINAL_HOME` ниже возвращает прежний выбор.

```sh
"$ADB" -s "$GU_SERIAL" shell cmd package set-home-activity --user "$GU_USER" com.geely.atlaslauncher
"$ADB" -s "$GU_SERIAL" shell input keyevent KEYCODE_HOME
"$ADB" -s "$GU_SERIAL" shell cmd package resolve-activity --user "$GU_USER" --brief -a android.intent.action.MAIN -c android.intent.category.HOME
"$ADB" -s "$GU_SERIAL" shell dumpsys role > "$GU_LOG/roles-atlas.txt"
```

Ожидать `Success` и `com.geely.atlaslauncher/com.mmwtl.atlaslauncher.HomeActivity` как resolved HOME. Если результат другой, **не продолжать тест**, выполнить откат. Проверить вручную по пунктам:

1. Низ: климат/dock видны, кнопки нажимаются, панель не закрывает виджеты или список приложений.
2. Верх: свайп открывает штатную панель, быстрые настройки работают, панель закрывается и повторно открывается.
3. Home/Back: из 2–3 приложений кнопка Home возвращает Atlas; Back не выводит в пустой экран.
4. Каталог: видны штатные и вручную установленные приложения, поиск и запуск работают.
5. Виджеты: стандартный и, отдельно, OEM `com.geely.mediawidget` добавляются через системное подтверждение, обновляются, реагируют на нажатия, перемещаются и сохраняются после `am force-stop`.
6. После нескольких переходов между приложениями повторить пункты 1–2. Сравнить с исходным `stock-home.png` и снимками окон, а не только с внешним видом Atlas.

На каждой контрольной точке записывать скриншот, `dumpsys window`, `dumpsys activity activities`, `dumpsys appwidget` и ошибки процесса. Пример:

```sh
"$ADB" -s "$GU_SERIAL" exec-out screencap -p > "$GU_LOG/atlas-home.png"
"$ADB" -s "$GU_SERIAL" shell dumpsys window > "$GU_LOG/windows-atlas-home.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys activity activities > "$GU_LOG/activities-atlas-home.txt"
"$ADB" -s "$GU_SERIAL" shell dumpsys appwidget > "$GU_LOG/widgets-atlas-home.txt"
"$ADB" -s "$GU_SERIAL" logcat -d -b crash > "$GU_LOG/crash.txt"
```

Перезагрузку делать **только после** того, как короткая проба прошла и команда возврата `ORIGINAL_HOME` уже проверена. После reboot заново проверить ADB, текущего пользователя, HOME, климат, верхнюю панель и виджеты. Root ADB после перезагрузки может сброситься; для отката системной роли root не нужен.

## 3. Откат, если что-то не так

### A. ADB доступен — основной путь

Восстановить реальный пакет из шага 0; пример ниже предполагает, что это `com.android.launcher3`:

```sh
"$ADB" -s "$GU_SERIAL" shell cmd package set-home-activity --user "$GU_USER" "$ORIGINAL_HOME"
"$ADB" -s "$GU_SERIAL" shell am force-stop com.geely.atlaslauncher
"$ADB" -s "$GU_SERIAL" shell input keyevent KEYCODE_HOME
"$ADB" -s "$GU_SERIAL" shell cmd package resolve-activity --user "$GU_USER" --brief -a android.intent.action.MAIN -c android.intent.category.HOME
"$ADB" -s "$GU_SERIAL" shell dumpsys role > "$GU_LOG/roles-restored.txt"
```

Проверить, что разрешается исходный HOME, он виден на экране, а климат и верхняя панель работают. `am force-stop` не удаляет виджеты и данные Atlas.

### B. Роль не переключается, но ADB доступен

Сначала явно открыть штатную Activity. Если экран появился, попробовать установить HOME через `cmd role`; команда есть в AOSP Android 11, но OEM может ограничить её. Проверить фактический результат `dumpsys role` и Home:

```sh
"$ADB" -s "$GU_SERIAL" shell am start --user "$GU_USER" -n com.android.launcher3/.Launcher
"$ADB" -s "$GU_SERIAL" shell cmd role add-role-holder --user "$GU_USER" android.app.role.HOME "$ORIGINAL_HOME"
"$ADB" -s "$GU_SERIAL" shell input keyevent KEYCODE_HOME
"$ADB" -s "$GU_SERIAL" shell dumpsys role > "$GU_LOG/roles-restored-fallback.txt"
```

Если OEM не принимает обе команды, временно отключить **только Atlas** для текущего пользователя — это исключает его из HOME-кандидатов без удаления данных. Затем открыть штатную Activity; после диагностики Atlas можно вернуть командой `pm enable`:

```sh
"$ADB" -s "$GU_SERIAL" shell pm disable-user --user "$GU_USER" com.geely.atlaslauncher
"$ADB" -s "$GU_SERIAL" shell am start --user "$GU_USER" -n com.android.launcher3/.Launcher
"$ADB" -s "$GU_SERIAL" shell input keyevent KEYCODE_HOME
"$ADB" -s "$GU_SERIAL" shell cmd package resolve-activity --user "$GU_USER" --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Для повторной проверки: `"$ADB" -s "$GU_SERIAL" shell pm enable --user "$GU_USER" com.geely.atlaslauncher`. **Не выполнять `pm disable-user` для штатного Launcher3, SystemUI или OEM-плагина.**

Если именно shell-команды роли/пакета возвращают `SecurityException`, а на этом ГУ действительно поддерживается root ADB, можно перезапустить `adbd` от root и повторить **те же команды A/B**:

```sh
"$ADB" -s "$GU_SERIAL" root
"$ADB" -s "$GU_SERIAL" wait-for-device
"$ADB" -s "$GU_SERIAL" shell id
```

Ожидать `uid=0(root)`. `adb root` не перепрошивает ГУ и не даёт гарантии, что OEM разрешит смену HOME; если команда всё равно не проходит, сохранить её вывод и остановиться. Не использовать root для ручного редактирования `/data/system`, role XML или раздела `/system`.

### C. Экран не отвечает, но ADB доступен

Выполнить A, затем B, наблюдая вывод каждой команды. При сохранённой проблеме после восстановления роли можно перезапустить устройство:

```sh
"$ADB" -s "$GU_SERIAL" reboot
```

После загрузки снова проверить `adb devices -l`, resolved HOME, экран, климат и верхнюю панель. Не применять `pm clear`, `uninstall`, `wm size`, `wm density`, `settings put`, `setprop` или запись в системные разделы как «быстрый сброс».

### D. ADB недоступен

ADB root не поможет, если не удаётся подключиться к `adbd`. Использовать штатный интерфейс выбора HOME, если он доступен, либо физическую процедуру перезагрузки ГУ. **Удалённое восстановление командами в этом состоянии гарантировать нельзя**. До теста нужно убедиться, что известен способ вернуть ADB/штатные настройки без стирания автомобиля. Не начинать долгую пробу и reboot, если такого способа нет.

После успешного возврата можно оставить Atlas установленным: он не влияет на работу штатного HOME. Если удалить его, его настройки и привязки виджетов будут потеряны; это отдельное, необязательное действие после проверки восстановления.

## Источники команд и локальные факты

- [Android Debug Bridge](https://developer.android.com/tools/adb): `install -r`, `pm`, `am`, `logcat`.
- [Android 11/AOSP PackageManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/bcb4d3cf6385/services/core/java/com/android/server/pm/PackageManagerShellCommand.java): `set-home-activity`, `query-activities`, `resolve-activity`.
- [AOSP Role shell](https://android.googlesource.com/platform/frameworks/base/+/android-mainline-10.0.0_r9/services/core/java/com/android/server/role/RoleManagerShellCommand.java): `add-role-holder --user`.
- [AOSP roles](https://android.googlesource.com/platform/packages/apps/PackageInstaller/+/HEAD/src/com/android/permissioncontroller/role/Role.md): просмотр роли через `dumpsys role`, управление через `cmd role`.
- Локальный `reference-apks/oneOS_Launcher3.apk`: package `com.android.launcher3`, HOME Activity `com.android.launcher3.Launcher`, `sharedUserId=android.uid.system`. Это не доказывает, что на подключённом ГУ та же сборка; шаг 0 проверяет фактическое состояние.

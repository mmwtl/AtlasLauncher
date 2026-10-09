# APK для исследования лаунчера OneOS

Файлы скопированы 2026-09-25 для статического анализа первого прототипа AtlasLauncher. Исходники в `~/Downloads` не изменены. APK из `~/Downloads/aaa/app` побайтово совпадают с одноимёнными APK из `~/Downloads/app`.

| Файл | Пакет | Зачем нужен | Исходный путь | SHA-256 |
| --- | --- | --- | --- | --- |
| `oneOS_Launcher3.apk` | `com.android.launcher3` | Штатный HOME, правила списка приложений и виджетов | `~/Downloads/app/oneOS_Launcher3.apk` | `51fabb0a4f5104b0472058f16d7271c20e119e660681a2bef5f24260b195e10f` |
| `SystemUI.apk` | `com.android.systemui` | Системный статус-бар и Quick Settings | `~/Downloads/app/SystemUI.apk` | `3ed7d3535026462349307ca93aa2ca87b5fbce9e291c3a5744c270bfbd4b9489` |
| `oneOS_SystemUIPlugin.apk` | `com.ecarx.systemui.plugin` | OEM dock, климатическая и верхняя панели | `~/Downloads/app/oneOS_SystemUIPlugin.apk` | `f5efaa2510e88a72f42745cce23bb7439691b1b3a2b82a381a0d5f34c62b4103` |
| `oneOS_MediaCenter.apk` | `com.geely.mediacenterservice` | Сервис, связанный со штатным медиавиджетом | `~/Downloads/app/oneOS_MediaCenter.apk` | `842c847d7aa591ea3b1cabeacbb86b68b25b60df4915b4b61f4b338e58567fd1` |
| `oneOS_MediaCenterUI.apk` | `com.geely.mediawidget` | Провайдеры штатных Android AppWidget | `~/Downloads/app/oneOS_MediaCenterUI.apk` | `6ac48b15d64e488d8cd5b59f2325fc0457f6cba0ffc3e236e9a3d4ad0ad85d79` |
| `4.6.1.1719.GInputBridge-release.apk` | `com.salat.gbinder` | Проверка фактически выпущенных компонентов и broadcast-действий GInputBridge | `~/Downloads/4.6.1.1719.GInputBridge-release.apk` | `1dd87810207bc0b6035281f8726df10f6f2175cc4d79e46f8994cbbcd07533c0` |
| `SCS_global_2.1.20260829.1509.apk` | `com.example.climateseats` | Сравнение стороннего способа скрывать OEM-климат; это не основа прототипа | `~/Downloads/SCS_global_2.1.20260829.1509.apk` | `2c33cedf3a4868287cf237d1f908f85b0288891cc04ec69c3f1a189294d19803` |
| `oneOS_Hvac.apk` | `com.geely.hvac` | Климатическое приложение: внутренние команды климата | ГУ, `/system/app/oneOS_Hvac/` | `4a6f6df8e8ded385abac65dd9084d7cd105e38c07824172c12051d8e616acc5a` |
| `oneOS_ApiService.apk` | `com.geely.service.oneosapi` | Реализация сервиса OneOS API | ГУ, `/system/app/oneOS_ApiService/` | `e688190de2756c7580ea408c3033cc239e4d6e0445adc6205698d58df8d6b4c0` |
| `oneOS_DIMService.apk` | `com.geely.dimservice` | Служба передачи медиа/телефона на приборку (DIM); запускает её только Launcher3 | ГУ, `/system/app/oneOS_DIMService/` | `75992690345b551268b3365a81bf3692a1ce925d27b8f605549f08db17dfa0d5` |
| `oneOS_InputService.apk` | `com.geely.inputservice` | Служба ввода; стартует при загрузке через OneOS API | ГУ, `/system/app/oneOS_InputService/` | `fb8f5488a86b157c6ccc9607142a828f9e9dbbac4a9568898a8da7387c81f819` |

Папка `oneOS_Launcher3/` — точная копия ранее распакованного лаунчера из корня проекта: `diff -qr` различий не нашёл.

## Снято с ГУ 2026-09-29

ГУ `G636`, `ecarx-userdebug 11 RQ3A.211001.001 752 test-keys`, экран 1440×1920, 160 dpi; OEM-пакеты версии `1.0.20250623G(312)` (versionCode 786). Launcher3, SystemUI, SystemUIPlugin, MediaCenter и MediaCenterUI с ГУ побайтово совпадают с файлами выше. `oneOS_Hvac.apk`, `oneOS_ApiService.apk`, `oneOS_DIMService.apk` и `oneOS_InputService.apk` сняты с ГУ.

`framework/` (игнорируется Git) — jar из `/system/framework` и `/system/system_ext/framework` с dex внутри, пригодные для декомпиляции:

| Файл | SHA-256 |
| --- | --- |
| `framework.jar` | `c9812e2a59dd9dd8c7eb92887a2ee9108532c42b9c93ba48a521ae3f6eea92dd` |
| `services.jar` | `4b39316718ee8b92317a3a2f3707cc75a3954c0f0afe46baa7cee601a0889c63` |
| `framework-res.apk` | `5518165441d7b899faaec304c7feda1855c0417af2c81cfe0d3742af27f2d5fe` |
| `ActivityExt.jar` | `e0736cacb81c6cce67bb348282ca93700286665273206f9e5c01115fdb75a781` |
| `ecarx.car.jar` | `03aac946f1b98deb5e189e79265a6d778c161416612e958bde72e3e2e28fc716` |
| `ecarx.adaptapi.jar` | `dc072481bcc26545db9f9bb4f75a188f1808e932586f8ef619393564999eec72` |
| `ecarx-gkui-openapi-impl.jar` | `e2a0c97f40514a99e642f672d3ae20759a30835678f64730c5e3e2315466dce4` |
| `ecarx-carservice-starter.jar` | `4114d3e007fcde7890d0a80d40df9017b88cc14647696a4d1235c84564e75e35` |
| `android.car.jar` | `5b480507537cae5b25027e913e36bc1058d69ecb7c8a25462ffb4f20cd979cb6` |
| `car-frameworks-service.jar` | `14db620c117eba7d3e58aad5e2d2e0663f2665f66100728331d7b9c6df1514e0` |

Дампы состояния (`pm list packages`, `getprop`, `dumpsys window/activity/appwidget/display`, снимок экрана) лежат в `../qa/head-unit-state/` и тоже игнорируются Git.

## Прошивка 1.0.20260326G(87), другой автомобиль

`city-20260326G87/` (игнорируется Git) — выборка из выгрузки всех APK чужого ГУ (Яндекс Диск, папка «сити», скачано 2026-09-30). У OEM-пакетов versionCode 135 (на G636 было 786). Jar-файлов фреймворка в выгрузке нет. Остальные APK выгрузки (сторонние приложения, Qualcomm, autolink-службы, WebView и т. п.) к лаунчеру отношения не имеют и не скачивались.

| Файл | Пакет | SHA-256 |
| --- | --- | --- |
| `Launcher3 1.0.20260326G(87).apk` | `com.android.launcher3` | `29c41c62afd9a97fb70e2095925ba0069ff4991c4276819fdca81992794c47c6` |
| `SystemUIPlugin 1.0.20260326G(87).apk` | `com.ecarx.systemui.plugin` | `d9fbb0adb10153ac96925b048abf2b6469047d3b49259bfbe3ccdc69547f59ee` |
| `Интерфейс системы 11.apk` | `com.android.systemui` | `21fc5ae5274ddd0d14a87b541e9926b603dd22e8ffb58a48058d9ea5a3c61f8a` |
| `Система Android 11.apk` | `android` (framework-res) | `08f20156f028700ff1129b76328c10606be6d61a1b02a9ab12a437eb8f95bfaf` |
| `XCGestureService 1.0.20260326G(87).apk` | `com.geely.screengestureservice` | `cdae29871e69d953303c9f9905c57cba4834ae852af36e13308aff09ede73dd0` |
| `GlyControlBoard 1.0.20260326G(87).apk` | `ecarx.controlboard` | `89a74e428ebe0414c184185b8805cc717bd02ec86c2241517f1ce64ae742edce` |
| `A_C 1.0.20260326G(87).apk` | `com.geely.hvac` | `a16914fcab036c5a4797639632e32c07d21d42d5d64cca0c0abbe0db784bba8a` |
| `1.0.20260326G(87).apk` | `com.geely.mediawidget` | `6a178fbe8b0904932804a4dce5bbd4a9e7c850d1b5c02035ac9ba2cc72205502` |
| `MediaCenterService 1.0.20260326G(87).apk` | `com.geely.mediacenterservice` | `065f4534ea63673b1fdf99419149e6bc70548ce948256c8f0995540aa1109d7a` |
| `DimService 1.0.20260326G(87).apk` | `com.geely.dimservice` | `1f478392271501b8a013384b976103b54cd44c343f6fb83297b162030ba20efc` |
| `inputService 1.0.20260326G(87).apk` | `com.geely.inputservice` | `58b5a6c6c363b795e3bb6ffbad6c7836b26f3bdef837e7e622fbb1cac465eae2` |
| `Медиа 1.0.20260326G(87).apk` | `com.tencent.wecarflow` | `41c9d3d76ce443f35c6d1c6ef467ef373a25540e6536a5b97ed5c7d222812c7f` |
| `ScreenSaver 1.0.20260326G(87).apk` | `com.geely.screensaver` | `65d47e19955a51eef950e64197f10450e2a6d3b731f06be10d015706784f46b3` |
| `com.geely.permission.service.PermissionApplication 11.apk` | `com.geely.permission.service` | `346d2a2e87a6261b089dc495123286bdaa4e7eb7a04c125d7be6a4d46a000ab8` |
| `Контролер разрешений 30 system image.apk` | `com.android.permissioncontroller` | `d81ca214b31991297094d2aecfde2b26511b3fe7dc9611d89b2e2e7caac345c6` |
| `CarActivityResolver 11.apk` | `com.android.car.activityresolver` | `02f0453b2fe0058dd5af10603ca0d0d796a2d43799fbaac38c9b95fa8f0f4b96` |
| `MultiWindow 11.apk` | `com.autolink.multiwindow.service` | `5c07c60ab7350e80509f416922650ea5f3f8da5cdd341405e3c00bddcb571ce1` |
| `ECarX Car service 11.apk` | `com.ecarx.car` | `58290481267eb8d6b0b3664735feaa292fec4ca089bb8e8e1f35b7e8aeb6db83` |
| `GLauncher Link 1.1.apk` | `com.maxinf.car` (сторонний ярлык на GInputBridge) | `f1476aca1b5a0f9726603932a37595315a315c4d82d53631c44bc156707ac1a2` |
| `Настройки 1.0.20260326G(87).apk` | `com.geely.settings` | `1c25ae43053527bf16a7110e56ca4f1d56c17fba9c6da7adf2fbd5ae69a565aa` |
| `Настройки 11.apk` | `com.android.settings` | `26a8daeffb0166924d82470eb02b52a82e9645e113e4376ee2687639ef0d1804` |

Отличия от G636, найденные статическим анализом:

- `SystemUIPlugin`: `JumpUtils.jumpToHome` по-прежнему явно запускает `com.android.launcher3/.Launcher`, но теперь добавляет `CATEGORY_HOME` и флаги `NEW_TASK | RESET_TASK_IF_NEEDED | REORDER_TO_FRONT` (раньше только `NEW_TASK`) и запускает из контекста CSD-дисплея. Правило дока по префиксу `com.android`/`com.geely` не изменилось; добавлен `ProvisionObserver` (`Settings.Global.device_provisioned`).
- В приложениях выгрузки нет кода, который назначает или сбрасывает HOME (`addPreferredActivity`, `setHomeActivity`, `RoleManager`) или отключает пакеты.
- `XCGestureService` включает свою службу специальных возможностей: только дописывает её в `enabled_accessibility_services`, чужие не удаляет.
- Два приложения настроек. `android.settings.HOME_SETTINGS` не обрабатывает ни одно: его принимает PermissionController (`role.ui.HomeSettingsActivity`, роль HOME). `ACCESSIBILITY_SETTINGS` и `SETTINGS` обрабатывает `com.android.settings`. `com.geely.settings` не трогает роль HOME, preferred activities и `enabled_accessibility_services`; `setApplicationEnabledSetting` в нём только для `com.geely.energymanagement`. Его очистка памяти (тот же 360 CleanSDK) не входит в белый список Atlas, но исключает текущий HOME по умолчанию.
- В Launcher3 встроен 360 CleanSDK с `forceStopPackage`; он вызывается только ручной карточкой очистки памяти на нулевом экране Launcher3.

Выводы по этим APK собраны в [docs/oneos](../docs/oneos/README.md); первоначальный план — [docs/archive/prototype-plan.md](../docs/archive/prototype-plan.md).

## Полный архив G636, снят 2026-10-02

`g636-20250623G312/` (игнорируется Git) — выгрузка с того же ГУ `G636` (OEM-пакеты `1.0.20250623G(312)`) для будущего анализа без доступа к машине. Всё снято только чтением: с Android — `adb exec-out tar`, с QNX — `cat` в telnet-сессии из shell ГУ (`busybox telnet 192.168.118.2`).

| Папка | Содержимое |
| --- | --- |
| `apks/` | APK с исходными путями, без `oat`/`odex`/`vdex`. Пока 54 из 110: весь `/system/app` и ключевые пакеты из `/system/priv-app` и `/system_ext/priv-app` (`DIMInteraction`, `CarService`, `ECarXCarService`, `MultiWindowService`, `MiscService`, `CarSettings`, `Settings`). Не скачаны остальные `/system/priv-app`, `/product`, `/system_ext/app`, `/vendor`; Launcher3, SystemUI и SystemUIPlugin побайтно совпадают с файлами в корне этой папки. Размеры скачанных файлов сверены с ГУ (`stat -c %s`) |
| `framework/` | jar и apk из `/system/framework` и `/system_ext/framework` |
| `fs/` | `/system/etc`, `/product/etc`, `/system_ext/etc`, `build.prop` всех разделов, текстовые файлы `/vendor/etc` до 1 МБ (`*.rc`, `*.xml`, `*.conf`, `*.cfg`, `*.prop`, `*.json`, `*.txt`, `*.sh`, `*.ini`), `*.sh` из `/system/bin` и `/vendor/bin` |
| `dumps/` | `getprop`, `pm list packages -f -U --show-versioncode`, полный `dumpsys package`, получатели `BOOT_COMPLETED` в порядке рассылки |
| `qnx/` | QNX 7.0, сборка `rb-ecarx-dhu_hqx121c1-pcs03_oneos-fx11-j1-sop_release_2025.26.1`: текстовые файлы `/scripts`, `/etc`, `/proc/boot`, `/vm/images` (конфиги VM Android), `/apps/cluster/FX11_J1/etc` в `files/`; `ls -lR` этих каталогов и `/apps`, `pidin arg`, `Buildinfo.txt` |

Не взяты: бинарные калибровки и прошивки из `/vendor/etc`, библиотеки и исполняемые файлы QNX, а также `shadow`, ssh-ключи и `*.pem`.

Остальные APK докачиваются так (около 1 МБ/с по Wi-Fi; уже скачанные пропускаются):

```sh
cd reference-apks/g636-20250623G312/apks
adb -s 192.168.1.86:5555 shell 'find /system/priv-app /product/app /product/priv-app /product/overlay /system_ext/app /system_ext/priv-app /vendor/app /vendor/overlay -name "*.apk"' | tr -d '\r' |
  while read p; do [ -f ".$p" ] || { mkdir -p ".$(dirname "$p")"; adb -s 192.168.1.86:5555 pull "$p" ".$p"; }; done
```

Пригодится в первую очередь:

- `apks/system/priv-app/DIMInteraction/` (`com.autolink.diminteraction`) — `DIMKeyService`, по сигналу которого QNX убирает лого загрузки; разбор — [docs/oneos/boot-logo.md](../docs/oneos/boot-logo.md).
- `apks/system/app/LifeControlService/` (`com.geely.lifeControl`) — этапы запуска OEM-служб по `com.android.launcher3.startup`.
- `apks/system_ext/priv-app/CarSettings/` — `FallbackHome` (`com.android.car.settings`), первый HOME при загрузке.
- `apks/system/app/oneOS_ThemeManager/` — системные обои `GeelyWallpaperService`.
- `qnx/ls-lR.txt` — `ivi-anim` и ролик лого `IVIBootAnimation.mp4` в `/apps/cluster/FX11_J1`.

Подписи этих APK и что они значат для правок: [docs/oneos/firmware-signatures.md](../docs/oneos/firmware-signatures.md).

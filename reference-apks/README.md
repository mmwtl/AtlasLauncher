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

Подробные выводы и план: [`../docs/launcher-prototype-plan.md`](../docs/launcher-prototype-plan.md).

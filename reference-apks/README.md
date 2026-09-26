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

Папка `oneOS_Launcher3/` — точная копия ранее распакованного лаунчера из корня проекта: `diff -qr` различий не нашёл.

В просмотренных `~/Downloads/app` и `~/Downloads/aaa` нет APK `com.geely.hvac` и `com.geely.service.oneosapi`. Они нужны для углублённого разбора внутренних климатических команд или проверки реализации сервиса OneOS API, но не для сборки минимального HOME-прототипа, сохраняющего штатные панели.

Подробные выводы и план: [`../docs/launcher-prototype-plan.md`](../docs/launcher-prototype-plan.md).

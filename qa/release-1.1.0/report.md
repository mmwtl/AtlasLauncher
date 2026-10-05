# Релиз AtlasLauncher 1.1.0 (17)

Дата: 2026-10-05. Ветка `dock-home-click-redirect` объединена в `main` без конфликтов (fast-forward). Версия повышена отдельным коммитом.

## Проверки сборки

- JDK 17, Android SDK 35; `:app:assembleRelease` с `-Psecure.signing=secure.signing.gradle` и `-x :app:lintVitalRelease` собрал подписанный APK `1.1.0[17]AtlasLauncher-release.apk`.
- `apksigner verify --verbose --print-certs`: подпись v2 валидна, один подписант; SHA-256 сертификата `eaf9f1b2dc55db196b41c2c947964d6809a6c954d8aa7b45ad6d72133af0217e` совпадает с APK релиза 1.0.0, скачанным с GitHub.
- `aapt dump badging`: пакет `com.geely.atlaslauncher`, версия `1.1.0`, versionCode `17`, minSdk `26`, targetSdk `30`.
- `unzip -t`: ошибок целостности APK нет. `git diff --check` пройден.
- `:app:lintDebug`: только ожидаемая ошибка `ExpiredTargetSdkVersion` и 12 предупреждений; замечаний в изменённом `StockHomeRedirectService.java` нет. SDK и настройки lint не менялись.
- `:app:testDebugUnitTest`: `NO-SOURCE`; JVM-тестов в проекте нет.

## Проверка на ГУ

Пользователь подтвердил работу кнопок «Домой» и «Все приложения» на ГУ ATLAS ОД для кода ветки перед выпуском; см. [отчёт](../climate-home-click/report.md). Итоговый APK 1.1.0 отдельно на устройство в рамках подготовки релиза не устанавливался.

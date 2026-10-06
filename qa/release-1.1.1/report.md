# Релиз AtlasLauncher 1.1.1 (18)

Дата: 2026-10-06. Ветка `stock-launcher-preload` объединена в `main` без конфликтов (fast-forward). Версия повышена отдельным коммитом.

## Проверки сборки

- JDK 17, Android SDK 35; `:app:assembleRelease -x :app:lintVitalRelease` собрал подписанный APK `1.1.1[18]AtlasLauncher-release.apk` (2059076 байт), SHA-256 `5490c2fac4ed0329890bd5cf8139f212236b40a686736e5e6a23d126f6d96863`.
- `apksigner verify --verbose --print-certs`: подпись v2 валидна, один подписант; SHA-256 сертификата `eaf9f1b2dc55db196b41c2c947964d6809a6c954d8aa7b45ad6d72133af0217e` совпадает с релизом 1.1.0.
- `aapt dump badging`: пакет `com.geely.atlaslauncher`, версия `1.1.1`, versionCode `18`, minSdk `26`.
- `unzip -t`: ошибок целостности нет. `git diff --check` пройден.
- `:app:lintDebug`: только ожидаемая ошибка `ExpiredTargetSdkVersion` и 13 предупреждений, как до изменения.
- `:app:testDebugUnitTest`: `NO-SOURCE`; JVM-тестов в проекте нет.

## Проверка на ГУ

Release-сборка ветки (`1.1.0-stock-launcher-preload[17]`) передана пользователю через сетевой диск; после этого пользователь поручил объединение и выпуск. Явного отчёта о результатах на ГУ в сессии нет, пункты проверки — в [отчёте изменения](../stock-launcher-preload/report.md). Итоговый APK 1.1.1 на устройство в рамках подготовки релиза не устанавливался.

# Релиз AtlasLauncher 1.2.0 (19)

Дата: 2026-10-06. Ветка `force-widget-resize` объединена в `main` без конфликтов (fast-forward). Версия повышена отдельным коммитом.

## Проверки сборки

- JDK 17, Android SDK 35; `:app:assembleRelease -x :app:lintVitalRelease` собрал подписанный APK `1.2.0[19]AtlasLauncher-release.apk` (2059036 байт), SHA-256 `a8433299c8f3b8aacdf51af83f83b415372409ed527603d1e7f50073c77e7cbd`.
- `apksigner verify --verbose --print-certs`: подпись v2 валидна, один подписант; SHA-256 сертификата `eaf9f1b2dc55db196b41c2c947964d6809a6c954d8aa7b45ad6d72133af0217e` совпадает с релизом 1.1.1.
- `aapt dump badging`: пакет `com.geely.atlaslauncher`, версия `1.2.0`, versionCode `19`, minSdk `26`.
- `unzip -t`: ошибок целостности нет. `git diff --check` пройден.
- `:app:lintDebug`: только ожидаемая ошибка `ExpiredTargetSdkVersion` и 13 предупреждений, как до изменения.
- `:app:testDebugUnitTest`: `NO-SOURCE`; JVM-тестов в проекте нет.

## Проверка на ГУ

Изменение проверено только на эмуляторе, см. [отчёт изменения](../widget-resize/report.md). На ГУ не проверялось; пользователь поручил выпуск до проверки на ГУ. Итоговый APK 1.2.0 на устройство в рамках подготовки релиза не устанавливался.

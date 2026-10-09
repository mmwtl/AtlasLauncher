# Сборка и проверки

Нужны JDK 17 и Android SDK API 35. Используйте Gradle Wrapper из корня репозитория. `ANDROID_USER_HOME` держит настройки Android SDK внутри проекта.

```sh
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew :app:assembleDebug
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew :app:testDebugUnitTest
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew :app:lintDebug
```

`lintDebug` завершается `BUILD FAILED` из-за единственной ошибки `ExpiredTargetSdkVersion`: `targetSdk=30` намеренно оставлен под Android 11 на ГУ и ради требований Google Play не повышается. Новые замечания смотрите в `app/build/reports/lint-results-debug.*`.

## Release

```sh
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew :app:assembleRelease -x :app:lintVitalRelease
```

`lintVitalRelease` пропускается по той же причине.

Подпись подключается локальным игнорируемым `secure.signing.gradle`: скопируйте [app/secure.signing.gradle.example](../../app/secure.signing.gradle.example) в корень под этим именем и укажите ключ, alias и пароли (общий ключ с AtlasMediaWidget). Другой скрипт можно передать через `-Psecure.signing=/path/to/secure.signing.gradle`; относительный путь к ключу считается от корня проекта. Без скрипта получается APK с суффиксом `-unsigned`, он не готов к установке. Ключи и пароли в Git не добавляются.

Debug и release подписаны разными ключами и не обновляют друг друга.

## Версия и имя APK

Версия задаётся `appVersionName` и `appVersionCode` в [app/build.gradle.kts](../../app/build.gradle.kts). Вне ветки `main` к имени версии добавляется имя ветки. APK называется `<версия>[<versionCode>]AtlasLauncher-<тип>.apk` и лежит в `app/build/outputs/apk/<тип>/`, например `1.2.1[20]AtlasLauncher-release.apk`.

## Другие модули

`gestureprobe/` — отдельный диагностический APK для OEM-службы касаний, к лаунчеру не относится. Сборка и запуск — в [его README](../../gestureprobe/README.md).

## Проверка на ГУ

Эмулятор не воспроизводит OEM SystemUI, панель климата и службы OneOS. Порядок установки на ГУ, смены HOME и отката — в [протоколе проверки](head-unit-testing.md).

# Подписи OEM APK и тип сборки прошивки

Проверено 2026-10-01 по файлам `reference-apks/` и `reference-apks/city-20260326G87/` (`apksigner verify --print-certs`, `apkanalyzer manifest print`, `aapt2 dump badging`, jadx).

## Один ключ на все системные пакеты

Все OEM-пакеты обеих выгрузок, включая `framework/framework-res.apk` и `Система Android 11.apk`, подписаны одним сертификатом:

| Поле | Значение |
| --- | --- |
| Subject | `EMAILADDRESS=system@auto-link.com.cn, CN=platform, OU=SCM Dept., O="Autolink Co., Ltd.", L=Shanghai, ST=Shanghai, C=CN` |
| SHA-256 | `bacc31f553959a75e82cbadb72ad1ca72176bb2d66df2cfadfc52ad8ad20989e` |
| SHA-1 | `38126de431bcfad9e81d51c58aca38dd6360011a` |

На G636 это Launcher3, SystemUI, SystemUIPlugin, MediaCenter, MediaCenterUI, Hvac, ApiService, DIMService, InputService и `framework-res.apk`; во второй выгрузке — все 20 файлов, кроме `GLauncher Link 1.1.apk`. Так и должно быть: пакеты с `sharedUserId="android.uid.system"` (Launcher3, Hvac, SystemUI) и `android.uid.systemui` (SystemUIPlugin) обязаны иметь единую подпись.

Сторонние приложения подписаны другими ключами:

| Файл | Subject | SHA-256 |
| --- | --- | --- |
| `4.6.1.1719.GInputBridge-release.apk` | `O=SALAT, CN=Andrei` | `e888757137be0a635de771649bbdb19aad45eabd2764852ac48cb512d0794328` |
| `SCS_global_2.1.20260829.1509.apk` | `CN=SCS, OU=Dev, O=SCS, L=Unknown, ST=Unknown, C=RU` | `f952a2591acd6b1852c00b0dbc032ca13a0b489565a2335de9c68971e6f81d8b` |
| `city-20260326G87/GLauncher Link 1.1.apk` | `L=World, O=KostylCustom, CN=Salat` | `9ac6a159ee3c90da00b1de89fe329970777a1d1f8509eee8188729b3a0ab02e8` |

## Это не публичный AOSP testkey

`platform.x509.pem` из AOSP (`build/target/product/security/`) имеет subject `CN=Android, O=Android, C=US` и SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`. Совпадения с сертификатом пакетов нет: ключ собственный, поэтому опубликованные dev-ключи AOSP для переподписи не годятся.

## `test-keys` в прошивке относится к образу, а не к APK

`ro.build.fingerprint: qti/ecarx/ecarx:11/RQ3A.211001.001/752:userdebug/test-keys`, `ro.build.tags: test-keys`, `ro.build.type: userdebug` (и то же для odm/system/vendor/product), `ro.build.user: jenkins`. Это инженерная сборка образа ROM — отсюда же `ro.debuggable=1` и работающий `adb root`; сборку отдельных приложений этот штамп не описывает. Чем именно сформирована строка (вариантом userdebug или незаменённым `PRODUCT_DEFAULT_DEV_CERTIFICATE`) не устанавливалось.

## Сами APK собраны как release

- `android:debuggable` отсутствует у всех APK обеих выгрузок.
- `BuildConfig` из деха: Hvac — `BUILD_TYPE="release"`, `DEBUG=false`, `BUILD_TIME="062302"`, `FLAVOR="g636"`, `VERSION_CODE=786`, `VERSION_NAME="1.0.20250623G(312)"`; SystemUIPlugin — `BUILD_TYPE="release"`, `DEBUG=false`, `FLAVOR="g636"`. У остальных APK содержимое деха не проверялось.
- Схемы подписи: v2+v3 у Hvac, SystemUIPlugin, Launcher3, MediaCenterUI; только v3 у SystemUI, MediaCenter, `framework-res`; только v2 у GInputBridge. Схемы v1 (JAR) нет ни у одного пакета.

Значит, различать надо три независимые вещи: `BUILD_TYPE` приложения (release), вариант сборки ROM (userdebug) и штамп `test-keys` в fingerprint образа.

## Что это значит для правок

- Переподписать OEM APK тем же сертификатом нельзя: приватного ключа нет, ключи AOSP не подходят.
- Изменить код внутри APK и сохранить подпись нельзя: v2/v3 считаются по всем байтам APK, v1 — по дайджестам файлов.
- Обход проверки подписи проблему не решает: Hvac и Launcher3 живут в `android.uid.system`, SystemUIPlugin — в `android.uid.systemui`, без platform-подписи они теряют signature-level разрешения (климат, `WRITE_SECURE_SETTINGS`, `CAR_POWER` и т. п.).
- Запись в `/system` тоже закрыта: `ro.boot.veritymode=enforcing`, `ro.boot.flash.locked=1`, `ro.boot.verifiedbootstate=green`, `ro.build.ab_update=true`.

Проверка повторяется так: `apksigner verify --print-certs <apk>`, `apksigner verify -v <apk>` для схем, `apkanalyzer manifest print <apk> | grep debuggable`.

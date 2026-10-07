# Gesture Probe — статическая проверка и сборка

Дата: 2026-10-07. Рабочая копия `test-gestures`, ветка `test/gestures`.
Исходный HEAD: `afd3c5a`. До изменений рабочая копия была чистой.
Другие рабочие копии и ветка main не изменялись. ADB не вызывался,
устройства не перечислялись, APK никуда не устанавливался и не запускался.

## Результат

Добавлен отдельный модуль `:gestureprobe`, пакет `com.mmwtl.gestureprobe`,
версия 0.1 (1), minSdk 26 / targetSdk 30 / compileSdk 35.
Зависимостей, системных разрешений, shared UID, сервисов и ресиверов нет.
Одна программная Activity с Register/Unregister, ограниченным хвостом лога,
синей рамкой реальной области и зеркалированием лога в `GestureProbe`.
Запросы службы выполняются на отдельном HandlerThread; каждое входное событие
возвращается непосредственно на Binder-потоке до форматирования и вывода лога.

Таймер запрашивает снятие через 60 с от нажатия/intent. Снятие также вызывается
в onDestroy; если оно не завершается за 2 с, завершается собственный процесс.
Ошибки callback/повторной отправки, null host и неудачное снятие также завершают
процесс. Таймер основан на uptime, глубокий сон продлевает время по настенным часам.
Повторный Register не продлевает срок; восстановление Activity не регистрирует заново.

## Проверенные источники и Parcel

EdgeEventAPI из предоставленной декомпиляции Gesture_HU:
`/private/tmp/claude-501/-Users-wital-dev-AtlasLauncher/6965125a-557d-413b-9533-8ce6e2ddc610/scratchpad/gesture/out/sources/com/ecarx/edgeeventapi/EdgeEventAPI.java`.
Он импортирует `android.content.IInputDispatcherService` из framework,
получает `EcarxInputDispatcherService` через ServiceManager, а HIGH имеет ordinal 2.
В каталоге APK нет самого IInputDispatcherService; его Stub/Proxy проверены по framework.

OEM `framework.jar` прочитан из указанного пользователем
`/Users/wital/dev/AtlasLauncher/reference-apks/framework/framework.jar`, без изменения.
SHA-256: `c9812e2a59dd9dd8c7eb92887a2ee9108532c42b9c93ba48a521ae3f6eea92dd`.
JADX запускался последовательно с `-j 1`, `-Xmx2g`, `--single-class` для
`android.content.IInputDispatcherService`, `android.view.IInputFilter`,
`android.view.IInputFilterHost`, `android.view.InputEvent`, `android.graphics.Rect`.
Результаты: `/private/tmp/gestureprobe-framework-20261007/`.
Ошибок декомпиляции этих классов не было. Из декомпиляции использованы только
данные протокола; исходники скрытых AIDL не включены в модуль.

| Дескриптор | Код | Данные после interface token | Флаги / ответ |
| --- | --- | --- | --- |
| `android.content.IInputDispatcherService` | 1 registerPointerEventListener | int priority=2; int Rect-present=1; left, top, right, bottom; strong Binder filter | 0; readException; int boolean |
| тот же | 2 unRegisterPointerEventListener | strong Binder того же filter | 0; readException; int boolean |
| `android.view.IInputFilter` | 1 install | strong Binder host | oneway |
| тот же | 2 uninstall | нет аргументов | oneway |
| тот же | 3 filterInputEvent | int event-present; InputEvent если non-null; int policyFlags | oneway |
| `android.view.IInputFilterHost` | 1 sendInputEvent | int event-present=1; исходный InputEvent; исходный int policyFlags | FLAG_ONEWAY; reply=null |

Rect записывается публичным `Rect.writeToParcel`. InputEvent декодируется публичным
`InputEvent.CREATOR` и записывается исходным `writeToParcel`; внутренний type token
(1 MotionEvent / 2 KeyEvent) сохранён и не смешан с внешним флагом присутствия.
Публичность CREATOR с API 9 дополнительно проверена по SDK `api-versions.xml`.
Константы и реализация находятся в
`gestureprobe/src/main/java/com/mmwtl/gestureprobe/GestureProbeActivity.java`.

Сервер проверен по предоставленному
`/private/tmp/claude-501/-Users-wital-dev-AtlasLauncher/4773789b-dc1d-4eca-be4b-74d11648f109/scratchpad/gest/out/eids/EcarxInputDispatcherService.java`:

- register устанавливает хост до регистрации в RemoteCallbackList; явной UID/permission-проверки нет.
- Entry.setDisplayState требует bottom > 1080 для дисплея 0 и right > 1440 для дисплея 2, кроме особого случая left=top=0. Поэтому выбран Rect(400,1200,600,1400), только дисплей 0.
- Регион проверяется в ACTION_DOWN, затем выбранная запись кэшируется до UP/CANCEL. После unregister этот кэш не очищается: пробник сохраняет host после uninstall для оставшихся событий.
- Исключение при вызове мёртвого filter даёт false/pass-through; RemoteCallbackList удаляет мёртвый callback. Это основание аварийного завершения, а не доказательство отсутствия потери уже отправленного oneway-события.
- При UP, POINTER_UP, BUTTON_RELEASE сервер сам возвращает false после callback. Возврат всех событий может дать дубли. До callback также фильтруются MOVE с интервалом <5 ms.

## Проверки сборки

JDK 17.0.20.1, Gradle Wrapper 8.13, AGP 8.10.1, установленный SDK 35.
GRADLE_USER_HOME изолирован в игнорируемой `.gradle-user` этой копии;
кэш скопирован с clone-on-write, без изменения общего кэша.
`local.properties` создан только здесь и игнорируется Git; локальные SDK-пути
и debug keystore в коммит не входят.

```sh
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew --offline --no-daemon :gestureprobe:assembleDebug :app:assembleDebug
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew --offline --no-daemon :gestureprobe:lintDebug
git diff --check
```

Обе assembleDebug успешны. APK пробника:
`gestureprobe/build/outputs/apk/debug/gestureprobe-debug.apk`, 18 344 байта.
SHA-256: `fca67c02e049cf64685d1f430a9d6ea2ef8d1820cbfe513b86b0a3d46d605a99`.
APK лаунчера: `app/build/outputs/apk/debug/1.2.0-test-gestures[19]AtlasLauncher-debug.apk`.
Версия и код основного приложения не изменены.

`aapt dump badging` подтверждает пакет, версию, min/target SDK и launchable Activity.
`aapt dump permissions` не показывает uses-permission; итоговый manifest содержит
только MAIN/LAUNCHER Activity без sharedUserId. `apksigner verify --verbose` успешен,
v2, один debug signer. `unzip -t` успешен. `git diff --check` успешен.

Lint: единственная ошибка `ExpiredTargetSdkVersion`, ожидаемая при требуемом
targetSdk 30, не подавлена. Четыре предупреждения: `PrivateApi`,
`DiscouragedPrivateApi` (проверяемый скрытый ServiceManager),
`LockedOrientationActivity`, `DiscouragedApi` (портретное автомобильное ГУ).
Ошибок NewApi и Parcel/потоков нет. Сборщик дополнительно сообщает о недоступных
Android analytics и FSEvents в sandbox; обе сборки проходят.

## Граница доказательств

Никаких UI/Binder runtime-тестов и снимков экрана нет: пользователь запретил
подключаться к любым adb-устройствам. Unit-тестами без Android Binder runtime
нельзя доказать доставку OEM-событий; новых тестовых библиотек не добавлено.
Сборка и анализ Parcel подтверждают форму APK и реализацию, а не доступ на ГУ.

Неизвестны фактический SELinux-домен обычного APK, разрешение ServiceManager lookup,
hidden API policy, доступ callback/host и окончательная доставка в приложение.
`transact=true` на oneway означает принятие отправки без подтверждения обработки.
Успешная регистрация без событий может быть следствием конкуренции равного/большего
приоритета, геометрии/настроек другой прошивки, а не запрета UID.
Гарантия «никогда не глотать» несовместима с асинхронной поглощающей семантикой
исследованного сервера. Пробник возвращает всё и аварийно освобождает Binder;
нулевая потеря/дублирование событий не заявлены. Порядок будущей ручной проверки
и откат описаны в `gestureprobe/README.md`.

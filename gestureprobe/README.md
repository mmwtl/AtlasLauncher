# Gesture Probe

Отдельный диагностический APK `com.mmwtl.gestureprobe` (0.1): проверяет, доступны ли
обычному приложению глобальные касания через `EcarxInputDispatcherService` на OneOS
Android 11. Java и Android SDK, без библиотек, разрешений, shared UID, автозапуска,
Accessibility, HOME и глобальных overlay. Жесты и действия не реализованы.
Фактический SELinux-контекст процесса нужно проверить на ГУ: обычная установка
сама по себе не доказывает именно `untrusted_app` на конкретной OEM-прошивке.

## Сборка и запуск

Из корня репозитория, JDK 17 и SDK 35:

```sh
ANDROID_USER_HOME="$PWD/.android-user" ./gradlew :gestureprobe:assembleDebug
```

APK: `gestureprobe/build/outputs/apk/debug/gestureprobe-debug.apk`.
Команды ниже предназначены для последующей ручной проверки; при разработке
ни одно adb-устройство не использовалось. Замените `SERIAL` реальным номером.

```sh
adb -s SERIAL install -r gestureprobe/build/outputs/apk/debug/gestureprobe-debug.apk
adb -s SERIAL shell am start -n com.mmwtl.gestureprobe/.GestureProbeActivity
# Или сразу запросить регистрацию:
adb -s SERIAL shell am start -n com.mmwtl.gestureprobe/.GestureProbeActivity --ez register true
adb -s SERIAL logcat -v threadtime GestureProbe:V EcarxInputDispatcher:V '*:S'
# До регистрации: нет ли уже фильтров (pid, область, приоритет) и виден ли сервис
adb -s SERIAL shell service list | grep -i ecarx
adb -s SERIAL shell dumpsys EcarxInputDispatcherService
# Отказы SELinux
adb -s SERIAL logcat -d | grep -i avc
# Проверка обычного UID / SELinux-контекста запущенного процесса:
adb -s SERIAL shell ps -AZ | grep com.mmwtl.gestureprobe
# Аварийный откат независимо от UI и callback:
adb -s SERIAL shell am force-stop com.mmwtl.gestureprobe
```

При несовпадении подписи не очищайте данные и не удаляйте чужие пакеты:
используйте APK с той же подписью. Пробник устанавливается отдельно от AtlasLauncher.

## Проверка и безопасность

Нажмите **Register**. Приоритет `HIGH=2`, область дисплея 0 —
`Rect(400,1200,600,1400)` в физических px, правая/нижняя границы исключены.
Синяя рамка в окне показывает эту область с учётом положения окна на экране.
Регистрация запрещена на другом дисплее или если область не помещается с отступом
50 px справа/снизу. Геометрия рассчитана на портретное ГУ 1440×1920.
В исследованном сервере `Entry.setDisplayState` включает дисплей 0 при
`bottom > 1080`, дисплей 2 при `right > 1440`; обычный небольшой Rect выше y=1080
не выберет ни один дисплей. Касания у краёв раньше перехватывает OEM Back.

Начните касание **внутри** области, затем проверьте один/несколько пальцев
и обычную реакцию приложения. Чтобы подтвердить глобальность, перейдите в другое
приложение и начните новое касание в тех же координатах, наблюдая logcat.
Выход с Home не снимает регистрацию; Back/закрытие Activity снимает её в `onDestroy`.
**Unregister** снимает её вручную. Через 60 с от запроса регистрации запускается
автоснятие; повторный Register во время проверки не продлевает срок.
Если снятие зависнет, через дополнительные 2 с завершается собственный процесс.
Таймеры Handler работают по uptime: глубокий сон устройства отодвигает срок.
Восстановление Activity само не регистрирует фильтр заново.

Каждый полученный InputEvent немедленно возвращается исходному хосту с теми же
`policyFlags`, до логирования, прямо на Binder-потоке. Фильтр намеренно ничего
не поглощает. Хост сохраняется после `uninstall`, чтобы вернуть остаток касания:
сервер кэширует выбранную запись до UP/CANCEL даже после снятия регистрации.
При отсутствующем хосте, ошибке Parcel/отправки, `transact=false` или неудачном
снятии приложение завершает свой процесс, освобождая callback Binder.

**Абсолютное «никогда не глотать» этот OEM API гарантировать не позволяет.**
Сервер поглощает большинство событий после асинхронного вызова фильтра;
ошибка внутри oneway callback не возвращается серверу. Уже отправленное событие
может потеряться при сбое, а успех oneway не подтверждает доставку в приложение.
Для UP, POINTER_UP и BUTTON_RELEASE сервер дополнительно возвращает `false`,
поэтому возврат каждого события может дать дубли. Сервер также отбрасывает часть
частых MOVE до вызова фильтра. Force-stop/смерть Binder убирает регистрацию;
кэш текущего касания использует мёртвый Binder и серверный путь исключения.
Начните новое касание после отката. В области действует один фильтр с наибольшим
приоритетом: пробник временно вытесняет OEM-фильтры меньшего приоритета.
При равном или более высоком приоритете другого фильтра события могут не прийти:
успешная регистрация и пустой лог не доказывают запрет доступа.

## Лог `GestureProbe`

Прокручиваемый экран хранит ограниченный хвост лога; полный поток доступен в logcat.
Префикс строк — elapsedRealtime в ms.

| Строка | Значение |
| --- | --- |
| `START uid=… pid=…`, `DISPLAY …` | Идентичность процесса, экран и фактический размер. |
| `REFLECTION OK; binder=null/non-null` | Вызов скрытого ServiceManager удался; найден ли Binder. Null сам по себе не различает отсутствие службы и отказ доступа. |
| `REFLECTION blocked/failed: …` | Точное исключение поиска/вызова; может быть блокировка hidden API. Обхода нет. |
| `SERVICE descriptor=…` | Дескриптор службы; несовпадение запрещает регистрацию. |
| `DISPATCHER code=… transact=…` | Принята ли синхронная Binder-транзакция. |
| `REGISTER result=true/false`, `REGISTER exception: …` | Ответ службы либо исключение; true ещё не доказывает приход событий. |
| `INSTALL host=non-null` | Пришёл callback install с Binder хоста. Null приводит к FAIL_STOP. |
| `EVENT ACTION_… pointers=… id=… x=… y=… policyFlags=… SEND transact=true (oneway)` | Событие получено и отправлено хосту; координаты каждого пальца исходные. Это не подтверждение конечной доставки. |
| `APP ACTION_… pointers=… id=… x=… y=…` | Событие дошло до окна Activity (координаты в окне). Сравнение с `EVENT` показывает потери, дубли и порядок. |
| `UNREGISTER …`, `UNINSTALL …` | Причина/результат снятия и callback; возможны остаточные события текущего касания. |
| `FAIL_STOP …` | Ошибка возврата/снятия или зависание; причина и завершение своего процесса (только logcat). |

## Точный Binder-протокол

Константы и Parcel находятся в `src/main/java/com/mmwtl/gestureprobe/GestureProbeActivity.java`.
Проверены JADX по предоставленным EdgeEventAPI и OEM `framework.jar`:

| Дескриптор | Код | Данные после interface token | Режим / ответ |
| --- | --- | --- | --- |
| `android.content.IInputDispatcherService` | 1 registerPointerEventListener | int priority=2, int Rect-present=1, Rect.writeToParcel (left, top, right, bottom), strong Binder filter | flags=0; readException, int boolean |
| тот же | 2 unRegisterPointerEventListener | strong Binder того же filter | flags=0; readException, int boolean |
| `android.view.IInputFilter` | 1 install | strong Binder host | oneway |
| тот же | 2 uninstall | нет аргументов | oneway |
| тот же | 3 filterInputEvent | int event-present, InputEvent.writeToParcel если non-null, int policyFlags | oneway |
| `android.view.IInputFilterHost` | 1 sendInputEvent | int event-present=1, исходный InputEvent.writeToParcel, исходный int policyFlags | FLAG_ONEWAY, reply=null |

InputEvent декодируется публичным `InputEvent.CREATOR`: внутри присутствует ещё
отдельный type token (1 MotionEvent / 2 KeyEvent), его не следует путать с
event-present. Скрытые IInputFilter/IInputFilterHost не импортируются и не копируются.

Отсутствие проверки UID в Java сервере не доказывает доступность для обычного APK:
hidden API, ServiceManager/SELinux и обратные Binder-вызовы зависят от прошивки.
Сборка и статический анализ не заменяют проверку на ГУ.

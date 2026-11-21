# Управление сиденьями (VHAL, PSIS, VFC)

Разбор `ecarx.adaptapi.jar`, `ecarx.car.jar`, `ECarXCarService.apk`, нативного Android Vehicle HAL (`vendor.ecarx.automotive.vehicle@1.0-service`), QNX `psis_server` / `libpsis.so` / `psis_client` и стороннего приложения `com.geely.seatsettings2` на G636 `1.0.20250623G(312)` (комплектация без памяти сидений — оба кресла регулируются только физическими клавишами), 2026-11-21.

## Итог

На комплектации G636 без памяти сидений управлять пассажирским (и водительским) креслом с ГУ нельзя:
- программно ECarX API блокирует команды ещё в Android до отправки в QNX, потому что VHAL выставляет статус `notavailable` (`3`) из-за `CFG_SMP / CC1174 = 1` (код требует `2`);
- при прямом обходе этой проверки через VHAL команда проходит всю цепочку Android VHAL → FDBus/VFC → QNX, но кресло не реагирует — на шине нет CAN-модулей управления креслами (все обратные сигналы обоих кресел в HAL в статусе `ERROR`).

## Цепочка команды спинки пассажира (из кода)

1. Приложение вызывает `ICarFunction.setFunctionValue(SETTING_FUNC_SEAT_BACKREST = 755171840, zone = 4, value)` (`755171841` вперёд, `755171842` назад/откинуть, `0` стоп).
2. `VehicleHelper.setFunctionValue` перед отправкой проверяет доступность `isSupport(module, function, zone)`:
   - спинка пассажира (`zone = 4`, `Seat.java`): `ManagerId_papassseatswtinclstsallowd` (`561023795`, `0x21708b33`);
   - продольная регулировка (`SETTING_FUNC_SEAT_LENGTH = 755106048`): `ManagerId_papassseatextadjallowd` (`561023783`, пассажир) и `ManagerId_padrvrseatextadjallowd` (`561023781`, водитель);
   - спинка водителя (`zone = 1`): `ManagerId_padrvrseatswtinclstsallowd` (`561023793`).
   Если первый байт ответа равен `3` (`notavailable`), `setFunctionValue` сразу возвращает `false` без вызова нижнего уровня.
3. При доступной функции `Seat.java` вызывает `ECarXCarSeatctrlManager.CB_BackRestAdjmtRowFirstPass(val)` (`557876819`, `0x21408653`) со значениями `SAFwdBack2`: `0` (`Idle`), `1` (`FwdShortpres`), `2` (`BackwShortpres`), `3` (`FwdLongpres`), `4` (`BackwLongpres`). На удержание `Seat.java` отправляет `3` / `4`, на отпускание — `0`.
4. `ECarXCarPropertyManagerBase.setIntProperty` → `CarSignalService` → `SignalHalService` → нативный Android VHAL (`vendor.ecarx.automotive.vehicle@1.0-service`).
   Важная особенность API: `setIntProperty` возвращает `ApiResult.SUCCEED`, если вызов службы не бросил исключение; ошибки нижнего уровня `SignalHalService` только логирует. Поэтому `true` из adaptapi само по себе не подтверждает движение кресла.
5. В нативном VHAL `AutolinkVehicleHal::set` → `AutolinkVehicleHalKotei::set` обрабатывает `557876819` (`CB_BackRestAdjmtRowFirstPass`) **без проверки** `CFG_SMP`: перемапливает в `HU_BACKRESTADJMTROWFIRSTPASS` (`557854823`, `0x21403067`) со значениями `3 → 1`, `4 → 2`, `0 → 0`, обновляет статус `PA_PassSeatSwtInclSts` (`561023796`) и передаёт в `FDBusClient::setProperty`.
6. `FDBusClient::setProperty` сопоставляет свойство `557854823` с VFC-каналом `lcId = 55` (`vfcId = 25`), при необходимости вызывает `activeVFCProperty(lcID = 55, activeStatus = 1)` и отправляет сообщение по FDBus в QNX (`vehicleservice`).

## Откуда берётся `notavailable` (`3`) и `CFG_SMP`

- **В нативном Android VHAL (из кода и проверено чтением на ГУ):**
  `AutolinkVehicleHalKotei` (для спинки `561023795`) и `CarUsgModePowerSeat` (для продольной регулировки `561023783`) проверяют два параметра комплектации до проверки режима автомобиля (`UsageMode`):

| Параметр VHAL | Android-пропс | Условие в коде | На G636 без памяти |
| --- | --- | ---: | ---: |
| `CC471` (`557871936`) | `persist.vendor.ecarx.cfg.cc.backrest.motors.1st.row` | `2` | `2` |
| `CC1174` (`557872258`) | `persist.vendor.ecarx.cfg_cc.smp` (`smp`, не `psmp`) | `2` | `1` |

  Если хотя бы один параметр отличен от `2`, VHAL выставляет `3` (`notavailable`). Android-пропсы `persist.vendor.ecarx.cfg.lc.pass.seat.*` и `drvr.seat.*` в этой проверке не участвуют.
- **Источник на QNX (из кода и проверено чтением на ГУ):**
  Android VHAL на старте (`PsisFDBusClient::init`) умеет подгружать значения из `persist.vendor.ecarx.cfg_*`, но затем принимает `EVT_PSIS_SERV_CAR_CFG_MAP` от QNX-сервиса `psis_server` (FDBus `com.bosch.cm.qnx.notification-center`). У `psis_server` открыты разделы UFS `psis_back`, `psis_default_a`, `variant`. В локальной SQLite-базе Android `vhal.db` ключей `1174` / `557872258` нет. Чтение на QNX командой `psis_client -r 0 car_cfg CFG_SMP` дало `1`, `CFG_BACKREST_MOTORS_1ST_ROW` — `2`.

## Что подтверждено на машине (2026-11-21)

- **Пропсы Android:** `persist.vendor.ecarx.cfg.lc.pass.seat.backr.adjmt.swt` и `persist.vendor.ecarx.cfg_cc.smp` изменяются через `setprop` без root. Однако:
  - без перезагрузки установка `cfg_cc.smp = 2` не меняет `CC1174` в VHAL (`1`), статусы остаются `3` (`notavailable`), приложение получает `false`;
  - после перезагрузки ГУ `cfg_cc.smp` и `CC1174` в VHAL восстанавливаются обратно в `1`.
- **Обратная связь по креслам в VHAL:**
  - разрешения `561023793`, `561023795`, `561023783`, `561023781` — все `3` (`notavailable`);
  - сигналы датчиков и клавиш пассажира (`557873527` `PassSeatSwtInclSts`, `557873324` `PassSeatBtnPsd`, `557873351` `SeatBackAngleRowFirstPass`) и водителя (`557873448` `DrvrSeatSwtInclSts`, `557873450` `DrvrSeatSwtSldSts`, `557873276` `DrvrSeatBtnPsd`, `559970592` `DrvrSeatPosPercSld`, `557873350` `SeatBackAngleRowFirstDrvr`) возвращают статус `ERROR`.
- **Прямая отправка через VHAL в обход `notavailable`:**
  - `lshal debug android.hardware.automotive.vehicle@2.0::IVehicle/default --set 557876819 i 4` (импульс ~1 с) и следом `i 0` (`Idle`) проходят без ошибок (`AVAILABLE`).
  - При холодном VFC-канале `FDBusClient` отправляет активацию `lcId = 55` и команду `HU_BACKRESTADJMTROWFIRSTPASS` в одну миллисекунду, а подтверждение `VEH_NOTIFY_VFC` (`558931968`: `144` → `16528`, включился бит 14) приходит от QNX/MCU через ~70 мс (позже возвращается в `144` по таймауту). Это объясняет, почему стороннее приложение `com.geely.seatsettings2` повторяет команду через 400 мс при отсутствии эха.
  - Повторный тест по **уже активному VFC-каналу** (предварительный прогрев `i 0`, подтверждение `VEH_NOTIFY_VFC = 16528`, затем `i 4` → `HU_BACKRESTADJMTROWFIRSTPASS = 2` с записью `this vfc has been actived. vfcId: 25, lcId: 55`, через 1 с `i 0`) также не вызвал никакого движения или звука мотора пассажирского кресла.

## Заметка по диагностике VHAL

Из-за глобального `persist.log.tag=ERROR` на G636 `I`/`D`-логи VHAL по умолчанию не попадают в `logcat`. Временное включение без перезапуска сервиса:

```sh
setprop log.tag.VehicleHAL.AutolinkVehicleHal V
setprop log.tag.VehicleHAL.AutolinkVehicleHalKotei V
setprop log.tag.VehicleHAL.AutolinkVehicleHalVFC V
setprop log.tag.VehicleHAL.FDBusClient V
```

После снятия трассы вернуть все четыре тега в `E`.

#!/bin/sh
# Stops the stand-in dispatcher; the touchscreen goes back to the system at once.
# pkill does not reach app_process on the emulator, so it is killed by PID.
A="adb -s ${ANDROID_SERIAL:-emulator-5554}"
for p in $($A shell ps -A -o PID,NAME | awk '$2=="app_process"{print $1}' | tr -d '\r'); do
    $A shell kill -9 "$p"
done

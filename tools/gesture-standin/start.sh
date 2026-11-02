#!/bin/sh
# Builds the stand-in EcarxInputDispatcherService, starts it on the emulator and restarts Atlas so it connects.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
SERIAL=${ANDROID_SERIAL:-emulator-5554}
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
A="adb -s $SERIAL"

# It grabs the touchscreen and takes the OneOS service name: never on a head unit.
if [ "$($A shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then
    echo "$SERIAL is not an emulator" >&2
    exit 1
fi

OUT="$DIR/build"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
javac -nowarn -source 8 -target 8 -cp "$SDK/platforms/android-35/android.jar" -d "$OUT/classes" "$DIR/FakeDispatcher.java" 2>&1 | grep -v 'warning: \[options\]' || true
"$BUILD_TOOLS/d8" --release --min-api 30 --lib "$SDK/platforms/android-35/android.jar" --output "$OUT" "$OUT"/classes/*.class

sh "$DIR/stop.sh"
$A root >/dev/null
$A wait-for-device
$A shell setenforce 0
$A push "$OUT/classes.dex" /data/local/tmp/fake.dex >/dev/null
$A shell 'CLASSPATH=/data/local/tmp/fake.dex nohup app_process / FakeDispatcher > /data/local/tmp/fd.log 2>&1 &'
sleep 2
$A shell cat /data/local/tmp/fd.log
# Atlas looks the service up when the gesture service starts; without it the service stops.
$A shell am force-stop com.geely.atlaslauncher
$A shell am start -n com.geely.atlaslauncher/com.mmwtl.atlaslauncher.HomeActivity >/dev/null
echo "Ready: press 2, 3, 4 or 5 in the emulator window, then drag with the mouse."

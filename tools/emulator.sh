#!/bin/sh
# Nomad-shaped Android emulator for functional and pixel checks.
#
#   tools/emulator.sh up [port]     boot a headless instance (default port 5560), print its adb serial
#   tools/emulator.sh down [port]   shut that instance down
#   tools/emulator.sh list          show running instances
#
# Each instance is `emulator-<port>`; use it as `adb -s emulator-<port> ...`. Ports must be even,
# 5554..5682. Instances are started read-only, so several can run at once from the one AVD (one per
# agent) and each boots clean; app data still survives force-stop within a run.
#
# The AVD ("Dither_Nomad", 1404x1872 @ 300 dpi, landscape override 1872x1404) is created on first use
# from the installed system image. It is NOT e-ink: use it for layout, input, persistence and 1-bit
# purity; judge refresh, ghosting and weight at arm's length on the real device.
set -eu

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
NAME=Dither_Nomad
CMD="${1:-list}"
PORT="${2:-5560}"
SERIAL="emulator-$PORT"

create_avd() {
  [ -d "$AVD_HOME/$NAME.avd" ] && return 0
  IMG=$(cd "$SDK" && ls -d system-images/*/*/* 2>/dev/null | tail -1)
  [ -n "$IMG" ] || { echo "no system image under $SDK/system-images" >&2; exit 1; }
  TARGET=$(echo "$IMG" | cut -d/ -f2); TAG=$(echo "$IMG" | cut -d/ -f3); ABI=$(echo "$IMG" | cut -d/ -f4)
  mkdir -p "$AVD_HOME/$NAME.avd"
  cat > "$AVD_HOME/$NAME.avd/config.ini" <<EOF
AvdId=$NAME
avd.ini.displayname=Dither Nomad 1404x1872
avd.ini.encoding=UTF-8
abi.type=$ABI
hw.cpu.arch=$( [ "$ABI" = "arm64-v8a" ] && echo arm64 || echo x86_64 )
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1404
hw.lcd.height=1872
hw.lcd.density=300
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.initialOrientation=portrait
disk.dataPartition.size=2G
image.sysdir.1=$IMG/
tag.id=$TAG
tag.ids=$TAG
target=$TARGET
showDeviceFrame=no
skin.dynamic=yes
skin.name=1404x1872
skin.path=1404x1872
EOF
  printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/%s.avd\ntarget=%s\n' \
    "$AVD_HOME/$NAME.avd" "$NAME" "$TARGET" > "$AVD_HOME/$NAME.ini"
}

case "$CMD" in
  up)
    create_avd
    if ! "$ADB" devices | grep -q "^$SERIAL"; then
      nohup "$EMU" -avd "$NAME" -port "$PORT" -read-only -no-window -no-audio -no-boot-anim \
        -no-snapshot -gpu swiftshader_indirect >/dev/null 2>&1 &
    fi
    i=0
    until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
      i=$((i + 1)); [ "$i" -gt 100 ] && { echo "emulator on $PORT did not boot" >&2; exit 1; }
      sleep 3
    done
    # No first-run "Viewing full screen" sheet; landscape at the Nomad's size.
    "$ADB" -s "$SERIAL" shell settings put secure immersive_mode_confirmations confirmed
    "$ADB" -s "$SERIAL" shell wm size 1872x1404
    echo "$SERIAL"
    ;;
  down)
    "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    ;;
  list)
    "$ADB" devices | grep "^emulator-" || echo "no emulator instances running"
    ;;
  *)
    echo "usage: tools/emulator.sh up|down|list [port]" >&2; exit 2
    ;;
esac

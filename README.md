# Dither Tracker

A 1-bit life tracker for Magic: The Gathering Commander, built for E-ink Android devices
(primary target: Supernote Nomad). Pure black and white, integer-scaled dither patterns, no
animations. See `docs/SPEC.md` for the rules and `docs/BACKLOG.md` for the task list.

Modules:

- `:core` — pure Kotlin/JVM (game state, rules). JUnit tests.
- `:app` — Android app (single `Activity`, custom `View`s drawing on `Canvas`). `minSdk 26`.

## Requirements

- JDK 21 (`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`)
- Android SDK at `~/Library/Android/sdk` with platform `android-37.0` and build-tools `36.0.0`
- `local.properties` containing `sdk.dir=/Users/<you>/Library/Android/sdk` (gitignored)

Gradle comes from the checked-in wrapper; no system `gradle` needed.

## Build and test

```sh
./gradlew :core:test                 # unit tests
./gradlew :app:assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test :app:assembleDebug
```

## Install and run

```sh
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB devices
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n au.com.josephmark.dithertracker/.MainActivity
```

Or `./gradlew :app:installDebug` with a single device/emulator attached.

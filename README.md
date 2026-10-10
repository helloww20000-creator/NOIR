# NOIR — NoiseFit companion prototype

Native Android Kotlin starter for the NoiseFit Twist Go Elite Edition 5 (reported model NSW-309).

## Included
- Native dark UI with Home, Device Lab, Face Studio, Health and Settings.
- Bluetooth LE discovery with runtime permissions.
- Watch-face canvas preview with analog/digital/hybrid modes and palettes.
- GitHub Actions workflow to build a debug APK.

## Important limitations
This is a foundation build, not a completed NoiseFit replacement. Health sync, GATT service inspection, notifications, alarms, reminders, weather, media/camera controls, find-watch, face packaging/upload and exact-model compatibility still require implementation and testing. Do not assume a BLE protocol from another NoiseFit model works here. No unknown commands or firmware flashing are used.

## Build
Push to GitHub, open Actions, run `Build NOIR Android APK`, then download the `NOIR-debug-apk` artifact.

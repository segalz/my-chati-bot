# Working with the Connected Android Phone

[Index](INDEX.md)

## Purpose and current status

Codex and Claude Code develop on the Windows computer, compile the Android app there, and use USB ADB to install, run, inspect, and test it on the physical phone. They do not need to run their coding tools inside Android. A physical old phone is the primary acceptance target; an emulator supplements lifecycle and compatibility tests. scrcpy is optional for viewing/control.

**This document is a procedure, not a device connection report. No ADB discovery or phone test was performed during this planning task.** Exact model, serial, RAM, OS, installed app, SDK availability, and USB authorization are unknown.

## 1. Prepare and authorize the connection

1. Use a USB cable that supports data. Unlock the phone.
2. On the phone, enable Developer options and USB debugging. Menu labels vary by manufacturer/OS. The owner must approve the computer's RSA debugging prompt on the phone; an agent cannot manufacture this consent.
3. Install/use Android SDK Platform-Tools on Windows and locate the actual `adb.exe`. If needed, use the manufacturer's Windows USB driver. Check Android Studio/SDK/Java/NDK/CMake availability separately before choosing versions.
4. Run discovery. Choose the intended physical serial; never assume the first listed device is the target.

```powershell
# If adb is not on PATH, use the verified full path to adb.exe.
adb version
adb devices -l
$phoneSerial = 'REPLACE_WITH_CONFIRMED_PHYSICAL_DEVICE_SERIAL'
adb -s $phoneSerial get-state
```

`device` means ADB is authorized, not that app features work. `unauthorized` requires the owner's on-phone approval. `offline` or no device requires checking cable/port, phone unlock/USB mode, driver, and platform-tools state. Do not repeatedly change global ADB configuration or restart its server while other sessions may depend on it.

If multiple devices/emulators exist, use `-s $phoneSerial` on every device command. Keep serials private in shared evidence.

## 2. Inventory before choosing the stack or model

The following are read-only examples, to be run after selecting the phone:

```powershell
adb -s $phoneSerial shell getprop ro.product.manufacturer
adb -s $phoneSerial shell getprop ro.product.model
adb -s $phoneSerial shell getprop ro.build.version.release
adb -s $phoneSerial shell getprop ro.build.version.sdk
adb -s $phoneSerial shell getprop ro.product.cpu.abilist
adb -s $phoneSerial shell getprop ro.product.cpu.abi
adb -s $phoneSerial shell cat /proc/meminfo
adb -s $phoneSerial shell df -h /data
adb -s $phoneSerial shell wm size
adb -s $phoneSerial shell wm density
adb -s $phoneSerial shell dumpsys battery
```

Older devices may not support every command/output field. Record missing information rather than guessing. `MemAvailable` is more useful than assuming all installed RAM is available. A later small diagnostic app can inventory cameras, refresh rate, graphics, audio routes, and actual permissions more reliably than static properties. Hardware identifiers alone do not prove ML backend support.

Save a sanitized inventory with date, selected device, supported ABI, API level, available memory/storage, and capability gaps. Do not collect unrelated phone content, accounts, messages, media, or credentials.

## 3. Build on Windows — future implementation workflow

These commands assume an Android project has later been created. They do not apply to this planning-only directory. First inspect project instructions through the required code-intelligence route. Determine the real project root, module, tasks, package ID, launch activity, build variant, signing identity, and output path. Do not assume the examples are the final project configuration.

```powershell
# Run from the verified Android project root, not from plan/.
.\gradlew.bat --version
.\gradlew.bat tasks
# Example only: confirm the module/tasks first.
.\gradlew.bat :app:assembleDebug
```

Pin toolchain versions and build the native library for the phone's actual ABI. Keep signing keys and API credentials out of logs. A successful build is only the first check.

## 4. Install and launch the exact artifact

Verify target package/signing and inspect whether an existing installation contains valuable data. Replacing an app requires compatible signing/package identity; do not uninstall or clear data to bypass an installation error without owner authorization.

```powershell
$apkPath = 'REPLACE_WITH_VERIFIED_ABSOLUTE_APK_PATH'
$packageId = 'REPLACE_WITH_CONFIRMED_APPLICATION_ID'
$launchComponent = 'REPLACE_WITH_CONFIRMED_PACKAGE_AND_ACTIVITY'
Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
adb -s $phoneSerial install -r $apkPath
adb -s $phoneSerial shell am start -W -n $launchComponent
```

Installation/replacement is a future mutating step to perform within the then-authorized development scope, not part of this documentation task. Use normal permission dialogs; do not silently grant microphone/camera permissions to make acceptance tests pass.

## 5. Observe, test, and iterate

Inspect the phone directly or through optional scrcpy. Verify that the launched build is the intended artifact, then perform a real user action and independently observe the result. For example: trigger a local expression and observe the eyes; mute and verify microphone capture stops; deny camera permission and verify no capture.

```powershell
# Obtain the current PID; rerun after app restarts.
adb -s $phoneSerial shell pidof $packageId
# Replace the placeholder with the observed PID; stop collection when done.
adb -s $phoneSerial logcat --pid=REPLACE_WITH_OBSERVED_PID
adb -s $phoneSerial shell dumpsys meminfo $packageId
adb -s $phoneSerial shell dumpsys gfxinfo $packageId
# Optional, if scrcpy is installed:
scrcpy -s $phoneSerial
```

Some graphics paths will need additional profiling beyond `gfxinfo`. Redact logs and avoid collecting ambient audio/video. Screenshots/recordings require a suitable test scene and explicit test scope, especially if camera preview or private notifications may be visible.

Use a short loop: build -> verify artifact -> install -> launch -> act -> observe -> gather targeted evidence -> diagnose through the required route -> edit -> retest affected behavior. Report observed behavior, not only command exit codes.

## 6. Optional LAN/backend checks

`localhost` on Android refers to the phone, not the Windows machine. Use a deliberately configured LAN address or a temporary ADB reverse mapping when testing a host service. Confirm port, authentication, and transport before use. Remove temporary mappings afterwards. Do not open public firewall access or expose Ollama without authorization.

## Agent boundaries and cleanup

- Codex owns implementation decisions and verification. Claude delegation uses the configured `claude-code` MCP route when the owner asks for it; provide concise English scope and explicit working directory under `C:\Develop`.
- Existing code discovery follows the owner's Cline-first policy; agents must not use this guide to bypass it.
- Do not factory-reset, root, flash, unlock the bootloader, uninstall unrelated apps, clear app data, disable security, or change device-wide settings without explicit authority.
- Stop scrcpy/log sessions and remove temporary forwarding when finished. Do not revoke unrelated debugging authorizations.
- Report target device, artifact hash/version, checks run, observations, failures, and untested features. Discovery is not end-to-end capability proof.

Official references: [ADB](https://developer.android.com/tools/adb), [run on a hardware device](https://developer.android.com/studio/run/device), [scrcpy](https://github.com/Genymobile/scrcpy).

# radio-lyric

Android DAB+ radio + live-lyrics app for the **Mekede DUDU7** head unit (Android 13)
paired with the **Mekede DAB+ USB dongle** (VID `0x16C0` / PID `0x05DC`).

Defaults to **Heart UK** (D1 ensemble, Block 11D / 222.064 MHz, SId `0xCFD1`).

> Status: in active development (MVP). No release yet.

## Hardware

| Item | Value |
| --- | --- |
| Head unit | Mekede DUDU7 (Android 13, MediaTek-class SoC) |
| DAB receiver | Mekede DAB+ USB dongle (CDC-ACM class) |
| USB IDs | VID `0x16C0`, PID `0x05DC` |
| Audio | HE-AAC v2 → S16_LE PCM @ 48 kHz stereo |

## Build

Requires JDK 17+, Android SDK with `compileSdk=35`, NDK `27.0.12077973`.

```bash
./gradlew :app:assembleDebug                                  # debug APK (FakeRadioSource)
./gradlew :app:assembleRelease                                # release APK (real omri-usb backend)
./gradlew :app:testDebugUnitTest                              # unit tests
./gradlew -Pradio.arm64Only=true :app:assembleRelease         # internal-only, halves APK size on DUDU7 (arm64 only)
./gradlew -Pradio.includeFolkloreAccActions=false :app:assembleDebug   # drop unverified OEM ACC_ON broadcasts
```

## Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Developer commands

A top-level `Makefile` consolidates the common adb / Gradle commands used
during on-device iteration. Run `make help` for the full list. The device IP
is configurable via `DEVICE_IP` (default `192.168.1.54`):

```bash
make help                              # list all targets
make connect DEVICE_IP=192.168.1.54    # adb connect to head unit
make install                           # build + install debug APK (real radio)
make install-fake                      # debug APK with FakeRadioSource
make logs                              # filtered logcat tail
make stop-dabz                         # release USB dongle from DAB-Z
```

## Autostart setup

The app registers a [BootReceiver](app/src/main/kotlin/com/example/radiolyric/autostart/BootReceiver.kt)
listening for AOSP boot signals plus a long list of OEM `ACC_ON` broadcasts emitted by
Mekede / FYT / Microntek / MediaTek firmwares. On most builds no further setup is needed.

If the head-unit ships an "auto-start manager" UI, also enable **radio-lyric**
explicitly there.

## ADB hardening (recommended)

```bash
adb shell appops set com.example.radiolyric RUN_IN_BACKGROUND allow
adb shell cmd appops set com.example.radiolyric RUN_ANY_IN_BACKGROUND allow
adb shell dumpsys deviceidle whitelist +com.example.radiolyric
```

## Verify it's running

```bash
adb shell dumpsys media_session | grep -i radio-lyric
adb shell dumpsys usb | grep -i 16c0:05dc
adb logcat -s PlaybackService OmriUsbRadioSource AudioPump UsbPermissionGateway
```

## Documentation

- [docs/project-status.md](docs/project-status.md) — verified build state, what works on-device, what's blocked on hardware, prioritised backlog.
- [docs/dab-driver-bringup.md](docs/dab-driver-bringup.md) — how the wRadio C100 (yonghx 16C0:05DC) dongle was made to lock under our `omri-usb` fork.
- [docs/target-device-facts.md](docs/target-device-facts.md) — authoritative live-captured facts about the Mekede DUDU7 head unit.

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| No audio after auto-start | USB permission not granted | Open the app once; tap **Allow** in the USB dialog. |
| App doesn't start on power-on | OEM auto-start manager blocking | Enable in head-unit settings. |
| Lyrics empty | LRCLib API timeout (3 s) or no match | Check `adb logcat -s LyricsRepository`; try a popular track. |
| Tuner stuck on "Initializing" | Tuner did not reach `INITIALIZED` within 10 s | Re-plug dongle; check `adb logcat -s OmriUsbRadioSource`. |

## License

Application code: MIT (see `LICENSE`). Vendored `omri-usb` library: LGPL-2.1.

# Target Device Facts — Mekede DUDU7 Head Unit

Captured live via `adb` on **2026-05-05** while connected to the unit at
`192.168.1.54:5555`. Everything below is verbatim output from `adb shell` /
`adb shell dumpsys` queries — no inference. Use this as the authoritative
reference for what the app can and cannot rely on offline.

## 1. Build identity

| Property                                    | Value                                                                                        |
| ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `ro.build.version.release`                  | **13** (Android 13)                                                                          |
| `ro.build.version.sdk`                      | **33**                                                                                       |
| `ro.build.version.security_patch`           | 2023-03-05                                                                                   |
| `ro.build.fingerprint`                      | `UNISOC/uis7870sc_2h10_nosec/uis7870sc_2h10:13/TP1A.220624.014/ls08211712:user/release-keys` |
| `ro.build.display.id`                       | `TP1A.220624.014 release-keys`                                                               |
| `ro.build.date`                             | 2025-08-21 17:17:30                                                                          |
| `ro.build.version.min_supported_target_sdk` | 23                                                                                           |
| `ro.product.brand`                          | UNISOC                                                                                       |
| `ro.product.model`                          | `uis7870sc_2h10`                                                                             |
| `ro.product.device`                         | `uis7870sc_2h10`                                                                             |
| `ro.hardware`                               | `uis7870sc_2h10`                                                                             |
| `ro.board.platform`                         | `ums9620`                                                                                    |

**Implications**

- `targetSdk = 34` is fine; `minSdk = 24` is unnecessarily generous (could be
  raised to 33 for this single-target deployment, but no need).
- Foreground-service-type rules from API 34 are **enforced** — keep
  `mediaPlayback`.
- `POST_NOTIFICATIONS` is a runtime permission on Android 13 and is **denied
  by default** (see §6).

## 2. CPU / ABI

| Property                       | Value                           |
| ------------------------------ | ------------------------------- |
| `ro.product.cpu.abi`           | `arm64-v8a`                     |
| `ro.product.cpu.abilist64`     | `arm64-v8a`                     |
| `ro.product.cpu.abilist32`     | `armeabi-v7a,armeabi`           |
| `ro.product.cpu.abilist`       | `arm64-v8a,armeabi-v7a,armeabi` |
| App `primaryCpuAbi` (verified) | `arm64-v8a`                     |

**Implications**

- The `omri-usb` AAR's `arm64-v8a` JNI lib is the one actually loaded; the
  `armeabi-v7a` slice is dead weight on this hardware. Keep both for now in
  case a future variant ships a 32-bit-only SoC, but consider
  `abiFilters = listOf("arm64-v8a")` for an internal release to halve APK
  size.

## 3. Display & memory

| Property              | Value                   |
| --------------------- | ----------------------- |
| Physical size         | 2000 × 1200 px          |
| Density               | 240 dpi (`hdpi` bucket) |
| `MemTotal`            | 8 388 608 kB (≈ 8 GB)   |
| `MemAvailable` (live) | 3 590 836 kB (≈ 3.4 GB) |
| `/data` free          | 98 GB of 108 GB         |
| `low_ram` flag        | not set                 |

**Implications**

- 2000×1200 @ hdpi is unusually wide; landscape-first layout is mandatory.
- `LazyColumn` for lyrics has no memory-pressure concerns.
- 240 dpi + 56 dp touch targets ≈ 134 px — already comfortable for in-car use.

## 4. Locale & time

| Property               | Value                                    |
| ---------------------- | ---------------------------------------- |
| `persist.sys.locale`   | `en-UK`                                  |
| `persist.sys.timezone` | `Europe/London`                          |
| Device clock           | reported correctly (NTP / canbus-synced) |

**Implications**

- LRCLIB queries can use the device locale unmodified.
- No need to override `Locale.ROOT` for our regex / formatting work.

## 5. USB host & DAB dongle (live)

`dumpsys usb` while the dongle was attached:

```
host_manager.devices[0]
  name=/dev/bus/usb/001/003
  vendor_id=5824           (0x16C0)
  product_id=1500          (0x05DC)
  class=255 subclass=0 protocol=0
  manufacturer_name=yonghx
  product_name=DAB USB Dongle
  version=0.01
  serial_number=01234567890
  configurations[0]
    max_power=100
    interfaces[0]
      class=255 subclass=255 protocol=255
      endpoints=[
        { number=2, direction=IN  (0x80), address=130, type=BULK, max_packet=64 }
        { number=2, direction=OUT (0x00), address=2,   type=BULK, max_packet=64 }
      ]
```

`port_manager` reports the device-mode USB-C ports as `not-supported` for
contaminant detection; the **dongle is on the dedicated USB host bus**, not on
USB-C, hence `host_connected=false` in `device_manager` (that flag tracks the
device-mode port only — do **not** treat it as "no USB host").

### 5a. Sysfs / kernel view of the dongle (captured 2026-05-07)

Probe target: `/sys/bus/usb/devices/1-1.1/` (`busnum=1`, `devnum=3`,
`devpath=1.1`).

| sysfs node                                    | Value                                                                                    |
| --------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `idVendor` / `idProduct`                      | `16c0` / `05dc`                                                                          |
| `bcdDevice`                                   | `0001`                                                                                   |
| `version`                                     | `2.00` (USB protocol version reported by device)                                         |
| `speed`                                       | **`12`** Mb/s — Full-Speed only; device is _not_ High-Speed despite advertising USB 2.00 |
| `bMaxPower`                                   | `100mA` (= 200 mA after USB doubling)                                                    |
| `bmAttributes`                                | `0x80` (bus-powered, no remote wakeup)                                                   |
| `bNumConfigurations` / `bNumInterfaces`       | `1` / `1`                                                                                |
| `manufacturer` / `product` / `serial`         | `yonghx` / `DAB USB Dongle` / `01234567890`                                              |
| Interface `1-1.1:1.0` class/subclass/protocol | `ff` / `ff` / `ff`                                                                       |
| Endpoints                                     | `ep_02` (OUT BULK) and `ep_82` (IN BULK), 64 B packets                                   |
| Bound kernel driver                           | **`usbfs`** — i.e. userspace ownership only, no in-kernel DAB driver competing           |

### 5b. Bring-up history (driver behaviour over time)

Tracks the evolution of the `omri-usb` failure mode on this device. Kept here
because the kernel-log evidence is only available with a USB cable to the
DUDU7, which we do not always have.

#### 2026-05-06 — initial misdiagnosis (RETRACTED)

An earlier session captured a flood of:

```
usb 1-1.1: usbfs: process N (Thread-X) did not claim interface 0 before use
```

in `dmesg` and attributed it to our driver missing a
`UsbDeviceConnection.claimInterface(iface, true)` call. **This was wrong**:

- The vendored code already calls `claimInterface(..., true)` at
  [omri-usb/omriusb/src/main/cpp/platformspecific/android/jusbdevice.cpp#L127](../omri-usb/omriusb/src/main/cpp/platformspecific/android/jusbdevice.cpp#L127),
  via the JNI method ID resolved on line 44 of the same file.
- The `did not claim` lines came from a separate userspace process
  enumerating sysfs (likely an `lsof` / shell `Thread-X` probe running
  concurrently with the diagnostic), not from our `bulkTransfer` path.
- Post-fix verification logs in
  [.copilot-tracking/logs/2026-05-06/](../.copilot-tracking/logs/2026-05-06/)
  contain **zero** `did not claim` or `usbfs` matches while still showing
  `Found Siano device!`, `PowerUp`, and `TUNER_STATUS` transitions firing
  normally.

Retained as a cautionary note: do not re-plan a `claimInterface` fix without
first re-confirming the dmesg pattern under the **app's own UID** while no
other shell tooling is enumerating `/sys/bus/usb`.

#### 2026-05-06/07 — power-up acknowledge byte fix (LANDED)

Submodule commit `2470c04` *"Fix wRadio C100 power-up by draining setRegister
acknowledge byte"* on branch `radio-lyric` of the `omri-usb` fork is what
actually unblocked the bring-up past `TUNER_STATUS_INITIALIZED`. After this
commit the captured signal counts across the three logs in
[.copilot-tracking/logs/2026-05-06/](../.copilot-tracking/logs/2026-05-06/)
are:

| Log | `Found Siano` | `TUNER_STATUS` | `PowerUp` | `usbfs` |
| --- | --- | --- | --- | --- |
| `pre-WI05-fg-timeout.log` | 1 | 1 | 1 | 0 |
| `post-WI05-with-dabz-conflict.log` | 1 | 3 | 1 | 0 |
| `post-WI05-no-dabz.log` | 1 | 2 | 1 | 0 |

Driver reaches INITIALIZED. No `bulkTransfer` payload-level activity captured
yet — the next stall site is somewhere between INITIALIZED and audio frames
appearing on the IN endpoint (suspect: scan / lock state machine or the audio
render path on our side, not the USB layer).

**Static USB facts (still valid)**

- `res/xml/mekede_dab_filter.xml` (vendor 5824 / product 1500) is **correct**
  and already matches the live device.
- Manufacturer string is `yonghx` (not `Mekede`) — useful for log assertions.
- Vendor-specific class (`255`) means `UsbDeviceConnection.bulkTransfer` is
  the only path; no kernel CDC driver in the way (`usbfs` confirms).
- Bulk endpoints, 64-byte packets, **Full-Speed (12 Mb/s)** — matches
  `omri-usb`'s expectations and confirms research-doc bandwidth analysis
  (rules out raw I/Q, leaves PCM/AAC frames as the only feasible payload).
- `max_power=100` (= 200 mA) → fine on a head unit but flag it if we ever
  port to a battery-powered tablet.
- The dongle is **not held by any process** at idle (`lsof /dev/bus/usb/001/003`
  empty), so other apps are not racing us for the device — the failure is
  purely inside our `bulkTransfer` flow.

## 6. Our app on this device

| Property                      | Value                    |
| ----------------------------- | ------------------------ |
| Package                       | `com.example.radiolyric` |
| `versionCode` / `versionName` | 1 / `0.1.0`              |
| `minSdk` / `targetSdk`        | 24 / 34                  |
| `primaryCpuAbi`               | `arm64-v8a`              |
| `firstInstallTime`            | 2026-05-05 21:08:16      |
| `lastUpdateTime`              | 2026-05-05 21:20:42      |

### Granted permissions (live)

```
INTERNET                          granted=true
ACCESS_NETWORK_STATE              granted=true
FOREGROUND_SERVICE                granted=true
WAKE_LOCK                         granted=true
RECEIVE_BOOT_COMPLETED            granted=true
POST_NOTIFICATIONS                granted=false   ← runtime denied (API 33+)
DYNAMIC_RECEIVER_NOT_EXPORTED_…   granted=true
```

### App-ops (live)

```
POST_NOTIFICATION    ignore
SYSTEM_ALERT_WINDOW  allow
START_FOREGROUND     allow (running)
```

### Notification importance

```
AppSettings: com.example.radiolyric (uid 10160) importance=NONE userSet=false
```

**Implications — high priority**

- Notifications are silently suppressed (`importance=NONE`). The Media3
  foreground notification _is_ posted (service runs), but the user sees
  nothing. We must:
  1. Request `POST_NOTIFICATIONS` at first launch on Android 13+
     (currently missing from the runtime permission flow).
  2. Either ask the user to bump the channel importance, or accept silent
     foreground (acceptable for a `mediaPlayback` channel where the lock
     screen / steering wheel is the surface anyway).
- `START_FOREGROUND=allow` confirms `PlaybackService` is allowed to run.

### Doze / battery whitelist

```
deviceidle whitelist     (no entry for com.example.radiolyric)
deviceidle.mState        ACTIVE
deviceidle.mLightState   ACTIVE
mIsPowered               true (head unit always charging on ACC)
```

**Implications**

- We don't need a doze whitelist — the unit is always on mains while ACC is
  on. Doze never engages (`mState=ACTIVE`).
- The README's "ADB hardening" `dumpsys deviceidle whitelist +…` step is
  **optional** on this hardware, not required.

## 6a. Other DAB-related apps installed (live)

```
com.zoulou.dab    versionName=2.0.239 versionCode=239 targetSdk=34
                  primaryCpuAbi=arm64-v8a firstInstallTime=2025-08-29
                  apk=/data/app/.../com.zoulou.dab-*/base.apk
com.syu.carradio  (factory FM/AM head-unit radio app — analog only)
com.syu.radio     (factory radio launcher tile)
com.syu.music     (local-media player)
```

**Implications**

- `com.zoulou.dab` is the package the team has been calling "DAB-Z". Confirms
  the research note that DAB-Z is closed-source and shipped pre-installed by
  the OEM. It is **not** a `com.syu.*` package — distinguish it clearly in
  prose.
- DAB-Z is a 64-bit-only APK (`primaryCpuAbi=arm64-v8a`), matching our
  decision to ship arm64-only artefacts.
- `com.syu.carradio` is the analog FM/AM tuner; it does **not** drive the
  USB DAB dongle. Safe to ignore for DAB bring-up.

## 7. Network

| Property               | Value                                                      |
| ---------------------- | ---------------------------------------------------------- |
| Active default network | WiFi (`wlan0`) — VALIDATED, NOT_METERED                    |
| SSID                   | `HedgehogGarage` (current AP — pairing-of-convenience)     |
| IPv4                   | 192.168.1.54/24, gateway 192.168.1.254                     |
| IPv6                   | `2a00:23c8:3edb:2501::/64` (dual-stack works)              |
| DNS                    | `192.168.1.101`, `2a00:23c8:3edb:2501:150d:b977:61b3:5e73` |

**Implications**

- LRCLIB calls work fine when paired to a known WiFi (e.g. driveway / garage).
- Once on the road there is **no cellular**; lyrics layer must continue to
  treat all network failures as silent fallbacks (already enforced in
  `LyricsRepository`).
- IPv6 is enabled — OkHttp's "happy eyeballs" will work; no extra config.

## 8. Audio routing (relevant findings)

`dumpsys audio` (`STREAM_MUSIC`):

- No Bluetooth headset bound (`mBluetoothName=null`).
- Volume is set repeatedly by `com.syu.ms` (the SYU "main service") — every
  ~10 s it issues `setStreamVolume(STREAM_MUSIC, index=36)`. This is the
  **canbus volume sync** loop.

**Implications**

- `com.syu.ms` will overwrite our `STREAM_MUSIC` volume changes — never call
  `AudioManager.setStreamVolume` from our code; rely on the system / steering
  wheel.
- A `MediaSession` with `setPlaybackState(STATE_PLAYING)` lets the canbus
  layer route the steering-wheel `MEDIA_PLAY_PAUSE` to us cleanly.

## 9. OEM software stack on this unit

The unit is a **DUDU7** built on a UNISOC UMS9620 (UIS7870 family) board with
the **SYU** ("Sinyou") canbus + system-services suite and a **DUDU AutoUI**
launcher skinned by **WOW / FYT 7862**.

### Launcher

```
Launcher: com.dudu.autoui/.ui.activity.launcher.LauncherActivity
```

This is the **DUDU AutoUI** launcher. The autostart-picker (Scenario A in our
research) lives in:

- `com.dudu.autoui` → `Settings → Canbus → Startup Settings`

### SYU canbus / system services packages (verbatim list)

```
com.syu.av             com.syu.bt             com.syu.calibration
com.syu.canbus         com.syu.carlink        com.syu.carmark
com.syu.carradio       com.syu.carui          com.syu.cs
com.syu.doublecamera   com.syu.eq             com.syu.filemanager
com.syu.fourcamera2    com.syu.mcukey         com.syu.ms (main service)
com.syu.music          com.syu.protocolupdate com.syu.ps  (power service)
com.syu.radio          com.syu.rightcamera    com.syu.screensaver
com.syu.settings       com.syu.sha            com.syu.ss
com.syu.us             com.syu.window
```

### DUDU + FYT packages

```
com.dudu.autoui                              (launcher)
com.dudu.autoui.theme.{chaoyue1,cyb,hwbgb,hwbgh}
com.dudu.btmusic
com.dudu.wiki
com.duduos.ota_update
com.wow.fyt7862.base
com.wow.fyt7862.boothelper                   (boot orchestration)
com.wow.fyt7862.duduos_carui
com.wow.fyt7862.duduos_market
com.wow.fyt7862.osoperation                  (action: duduos.operation.cmd)
com.sprd.powersavemodelauncher
com.unisoc.silent.reboot
```

### ACC / boot intents the OEM stack actually exposes

`dumpsys package` mining of every OEM package shows that **none of the
"folklore" `ACC_ON` intents from research are actually broadcast by this
firmware**. The signals we can rely on are:

| Signal                                                           | Source on this device            | Verified live?              |
| ---------------------------------------------------------------- | -------------------------------- | --------------------------- |
| `android.intent.action.BOOT_COMPLETED`                           | AOSP — observed in broadcast log | **Yes**                     |
| `android.intent.action.SCREEN_ON` / `SCREEN_OFF`                 | AOSP — observed in broadcast log | **Yes** (every ACC cycle)   |
| `android.intent.action.ACTION_POWER_CONNECTED` / `_DISCONNECTED` | DUDU AutoUI listens              | **Yes**                     |
| `android.intent.action.QUICKBOOT_POWERON`                        | DUDU AutoUI listens              | declared (not seen live)    |
| `android.intent.action.USER_PRESENT`                             | DUDU AutoUI listens              | declared                    |
| `com.dudu.os.aidl.navbar.{load,rload}`                           | DUDU IPC                         | declared                    |
| `duduos.operation.cmd`                                           | `com.wow.fyt7862.osoperation`    | OEM CLI hook (interesting!) |
| `com.nwd.ACTION_AFTER_BOOT_COMPLETED`                            | DUDU AutoUI listens (NWD vendor) | declared                    |
| `com.nwd.action.ACTION_MCU_STATE_CHANGE`                         | DUDU AutoUI listens              | declared (likely true ACC)  |

**Negative findings** — the following intents declared in our `BootReceiver`
filter are **NOT** sent or listened to by anything on this firmware:

- `com.fyt.boot.ACCON`
- `com.glsx.boot.ACCON`
- `com.cayboy.action.ACC_ON`
- `com.carboy.action.ACC_ON`
- `com.microntek.startApp`
- `android.intent.action.ACC_ON`
- `com.android.action.ACC_ON`
- `com.xy.power.ACC_ON`
- `com.mtcd.action.ACC_ON`, `com.mtce.action.ACC_ON`, `com.wits.action.ACC_ON`

They cause no harm but are dead weight on this hardware.

**Implications — actionable**

- `com.nwd.action.ACTION_MCU_STATE_CHANGE` is the closest thing to a real
  ignition signal on this unit. Add it to the `BootReceiver` filter.
- `ACTION_POWER_CONNECTED` fires whenever ACC is restored on ACC-power-managed
  units — also worth adding.
- `SCREEN_ON` is the most reliable signal that the user has just woken the
  head unit (every ignition cycle ends with a `SCREEN_ON` broadcast). We
  already register this at runtime in `PlaybackService` — keep doing that.
- The 12 s deep-sleep USB re-poll (Step 9.2) is justified: USB host bus is
  briefly unpowered between ACC cycles.

### Other media apps already installed (potential audio-focus competitors)

```
com.syu.carradio        (existing AM/FM/DAB radio app — vendor)
com.syu.radio           (vendor RDS service)
com.syu.music           (vendor music)
com.syu.av              (AUX/AV input)
com.dudu.btmusic        (Bluetooth A2DP)
```

**Implications**

- We will compete for `AUDIOFOCUS_GAIN` against `com.syu.carradio` if the
  user starts both. Media3 already requests focus; just verify in Phase 10.2
  that swapping between us and `carradio` works.

## 10. Platform extras

| Property            | Value                                                                      |
| ------------------- | -------------------------------------------------------------------------- |
| GMS (Play Services) | **Installed** (`com.google.android.gms`, Maps, YouTube, Vending, Gearhead) |
| WebView provider    | `com.google.android.webview` 147.0.7727.111                                |
| Play Store          | `com.android.vending` present                                              |
| Google Assistant    | `com.google.android.googlequicksearchbox` present                          |
| Android Auto stub   | `com.google.android.projection.gearhead` present                           |

**Implications**

- We could ship Coil + OkHttp without the worries that haunt Chinese head
  units lacking GMS — but we don't depend on GMS today, so no change.
- Android Auto projection is available; the existing `MediaSession` will be
  visible in Gearhead with no extra work (worth a manual smoke test).

## 11. Power state at capture time

```
mWakefulness=Awake
mIsPowered=true
deviceidle.mState=ACTIVE
deviceidle.mLightState=ACTIVE
```

Confirms the head unit was on ACC and screen-on while we ran the queries.

---

## Summary of repo-level changes this should drive

(Tracked, not implemented in this commit.)

1. **Add runtime `POST_NOTIFICATIONS` request** in `MainActivity` for
   Android 13+ — currently missing (granted=false on the live device).
2. **Add `com.nwd.action.ACTION_MCU_STATE_CHANGE` and
   `android.intent.action.ACTION_POWER_CONNECTED`** to
   `app/src/main/AndroidManifest.xml` `BootReceiver` filter.
3. **Consider trimming the 11 dead OEM ACC actions** from `BootReceiver`
   (no harm, but they pollute logs and expand the receiver registration).
4. **Notification channel importance**: keep `IMPORTANCE_LOW`; document that
   silent foreground is expected on DUDU.
5. **Volume management**: add a comment near `PlaybackService` explicitly
   forbidding `AudioManager.setStreamVolume(STREAM_MUSIC, …)` because
   `com.syu.ms` overwrites it every ~10 s.
6. **Optional ABI trim**: an internal-only build with `abiFilters =
listOf("arm64-v8a")` would halve APK size with no loss on this hardware.

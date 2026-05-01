# Head Unit Autostart Research (Duduauto / MTC / Joying / Mekede class)

## Research Topics / Questions

1. Standard Android autostart on these head units (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON, MY_PACKAGE_REPLACED), reliability, FGS rules.
2. Vendor-specific ACC_ON / power broadcasts (XY/Joying, MTCD/MTCE, Wits, Topway, RK PX5/PX6, Allwinner T8, Duduauto). Cold boot vs deep-sleep behavior.
3. Built-in autostart picker in head-unit Settings (Power-on app / Boot app / factory codes / MCU menus).
4. Launcher replacement (HOME category) approach and trade-offs.
5. USB device race on boot — async attach, polling deviceList, attach broadcast.
6. Concrete code patterns — manifest, BroadcastReceiver, foreground service, FGS types/perms.
7. Gotchas — battery optimisation, SD-storage receivers, stopped state, doze.
8. ADB verification commands.

Status: Complete

---

## TL;DR — Recommended Layered Approach

For a Duduauto DUDU7 (and all the rebadge cousins), the layered defense is:

1. **First-line (no code, works today on Duduauto)**: tell the user to open `Settings → Canbus → Startup Settings → Select app to auto start on bootup` and pick our DAB+/lyrics app. If our app does not appear in that picker, fall back to `Settings → More features → Tasks` and create a task with trigger = "Vehicle Ignition", delay = 2–3 s, action = "Open the app". Both paths are documented by DUDU staff (`MihaiFlorin` and `DUDU-Meng`) in [forum.dudu-auto.com/d/482](https://forum.dudu-auto.com/d/482-dudu7-start-android-auto-be-default).
2. **Manifest-declared receiver covering every plausible broadcast** — `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `QUICKBOOT_POWERON` (both AOSP and HTC namespaces), `ACTION_MY_PACKAGE_REPLACED`, plus the OEM ACC intents (`com.fyt.boot.ACCON`, `com.glsx.boot.ACCON`, `com.cayboy.action.ACC_ON`, `com.carboy.action.ACC_ON`, `com.microntek.startApp`, `android.intent.action.BOOT_IPO`). Source: HeadUnit Revived investigation gist by andrecuellar — [gist](https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38).
3. **Foreground service `mediaPlayback` started immediately** from the receiver, so we satisfy the Android 12+ FGS-from-boot rules (BOOT_COMPLETED is on the allow-list of contexts permitted to start an FGS).
4. **USB attach handling**: do NOT rely on `USB_DEVICE_ATTACHED` firing for an already-plugged dongle on boot — poll `UsbManager.deviceList` from the service, then also register a runtime receiver for live attach/detach. The Duduauto sleep cycle cuts USB power and the dongle takes 5–10 s to re-enumerate after wake (confirmed by `DUDU-Meng` in [forum.dudu-auto.com/d/2626](https://forum.dudu-auto.com/d/2626-ignition-off-head-unit-status-setting-possible-with-screen-off)).
5. **Whitelist / kill-protection**: ask the user to disable battery optimisation for our package and (if the unit kills it during shutdown) add it to the firmware whitelist appropriate to its chipset (FYT `pwctl_config.xml`, MTCD `MTCManager.apk`, Reglink `/system/etc/*_whitelist.txt`).

The single most important discovery for THIS user is that the DUDU7 already has a first-class boot-app picker; we should make sure our app is launchable from a cold start (declares a launcher Activity, has been opened at least once) and document the picker path in our user manual.

---

## 1. Standard Android Autostart Mechanism

### Permissions & intent filters

* `RECEIVE_BOOT_COMPLETED` is required in the manifest for `android.intent.action.BOOT_COMPLETED` to be delivered.
* `BOOT_COMPLETED` is exempt from the Android 8+ implicit-broadcast restrictions and may still be declared in the manifest; same exemption for `LOCKED_BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` (Android docs — [developer.android.com/develop/background-work/background-tasks/broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts)).
* `LOCKED_BOOT_COMPLETED` is delivered before user unlock and only useful for Direct-Boot-aware apps; head units have no lockscreen so it usually fires near-simultaneously with `BOOT_COMPLETED`. See [developer.android.com/reference/android/content/Intent#ACTION_LOCKED_BOOT_COMPLETED](https://developer.android.com/reference/android/content/Intent#ACTION_LOCKED_BOOT_COMPLETED).
* `QUICKBOOT_POWERON` exists in two namespaces:
  * `android.intent.action.QUICKBOOT_POWERON` — used by MediaTek and many OEMs that ship a "fast boot" mode.
  * `com.htc.intent.action.QUICKBOOT_POWERON` — historical HTC Sense; widely copy-pasted by AOSP forks and present on some Chinese MTK firmwares.
  Both are unofficial but commonly listed alongside `BOOT_COMPLETED` in receiver filters; HeadUnit Revived added `QUICKBOOT_POWERON` and `BOOT_IPO` (MediaTek IPO/In-Place-On hibernate) explicitly because BOOT_COMPLETED was missed on Quick Boot wake.
* `ACTION_MY_PACKAGE_REPLACED` should be filtered on its own receiver to relaunch after our own self-update.

### Reliability on Duduauto / MTC firmware

* On a true cold boot (battery wire pulled or full power-off) `BOOT_COMPLETED` is reliably delivered. On a "Quick Boot" / hibernate wake, behavior depends on the firmware:
  * MediaTek/Reglink Quick Boot: `BOOT_COMPLETED` IS sent, but the firmware sets `stopped=true` on third-party apps during the shutdown countdown (~20 s after ACC off), and Android since 3.1 blocks ALL implicit broadcasts to a stopped app — so the app receives nothing on the next wake. Documented in the HeadUnit Revived case study (Phases 2–3 of [andrecuellar gist](https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38)).
  * Allwinner T3/T5/T7/T8: true suspend-to-RAM, no `BOOT_COMPLETED` on resume at all — must use `SCREEN_ON` or a Tasker-style trigger (same gist, Allwinner section).
  * Duduauto DUDU7 (Unisoc 7870 family): defaults to a 5-minute timed sleep after ACC-off; below 5 min it's a wake (no boot broadcast); after the deep sleep starts, USB power is cut and a real reboot or true wake follows. Confirmed by `DUDU-Meng` ("After 5 minutes of power loss, the ACC enters sleep mode, at which point the USB power supply is disconnected") at [forum.dudu-auto.com/d/2626](https://forum.dudu-auto.com/d/2626-ignition-off-head-unit-status-setting-possible-with-screen-off).

### Foreground-service rules from Android 12+

* From Android 12 (API 31) apps generally cannot start a foreground service from the background. `BOOT_COMPLETED` (and `LOCKED_BOOT_COMPLETED`) are on the documented allow-list of contexts that ARE permitted to start an FGS — see [developer.android.com/develop/background-work/services/fgs/launch#allowed-fgs-launches](https://developer.android.com/develop/background-work/services/fgs/launch).
* From Android 14 (API 34) the FGS must declare a `foregroundServiceType` and request the matching permission. For our DAB+/lyrics app the right type is `mediaPlayback` with permission `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` — see [developer.android.com/about/versions/14/changes/fgs-types-required#media](https://developer.android.com/about/versions/14/changes/fgs-types-required#media).
* The FGS must call `startForeground(...)` within ~5–10 s or the system kills it with `ForegroundServiceDidNotStartInTimeException`; the BroadcastReceiver should hand off via `Context.startForegroundService(...)` and the service should call `startForeground(...)` in the very first `onStartCommand`.

---

## 2. Vendor-Specific Power / ACC Broadcasts

The Android-headunit world has no standard ACC intent. Each firmware vendor invented its own. Below is the consolidated list with provenance.

| Action | Vendor / chipset family | Source / evidence |
|---|---|---|
| `android.intent.action.QUICKBOOT_POWERON` | MediaTek / generic AOSP fork | HeadUnit Revived gist Phase 1 |
| `android.intent.action.BOOT_IPO` | MediaTek IPO (In-Place-On hibernate) | HeadUnit Revived gist Phase 1 |
| `com.htc.intent.action.QUICKBOOT_POWERON` | HTC Sense, copy-pasted by many MTK forks | Stack Overflow [#11502819](https://stackoverflow.com/questions/11502819) and widespread sample manifests |
| `com.fyt.boot.ACCON` | FYT (Joying / KLD / FunRover / Witson / iDoing / Xtrons UIS7862 / UIS8581 / UIS7870) | HeadUnit Revived gist, "FYT: ACC Broadcasts" — *only fires on Android 7 and below; on Android 8+ blocked by implicit-broadcast restriction* |
| `com.glsx.boot.ACCON` | GLSX-branded firmware | HeadUnit Revived gist Phase 1 |
| `com.cayboy.action.ACC_ON` | Microntek / MTCD on Rockchip PX3 | HeadUnit Revived gist, "MTCD: Useful Broadcasts" |
| `com.carboy.action.ACC_ON` | Microntek variant | Same source |
| `com.microntek.startApp` | Microntek wake-from-sleep | Same source |
| `com.xy.power.ACC_ON` | Often quoted for "XY Auto" / Joying older Sofia | Cited in many copy-pasted XDA snippets but no primary source confirmed in this research; treat as folklore — include in receiver filter, do not depend on it |
| `com.mtcd.action.ACC_ON` / `com.mtce.action.ACC_ON` | Folklore, frequently quoted; no primary source surfaced | GitHub code search returned 0 hits for either string. Include defensively but expect MTCD units to actually use `com.cayboy.action.ACC_ON` or `com.microntek.startApp`. |
| `com.wits.ACC_ON` / `com.wits.action.ACC_ON` | Wits firmware (BMW NBT-style units) | GitHub search returned 0 hits; folklore — include defensively |
| Duduauto-specific ACC broadcast | DUDU OS | No public ACC broadcast string was discoverable. Duduauto exposes the equivalent functionality via the user-facing `Tasks` system (trigger = "Vehicle Ignition") rather than an intent. Source: [forum.dudu-auto.com/d/482](https://forum.dudu-auto.com/d/482-dudu7-start-android-auto-be-default) post #3. |

### Cold boot vs deep sleep

* These units almost never do a cold power-off; the typical cycle is ACC off → screen off → 20–30 s shutdown countdown → either (a) suspend-to-RAM (Allwinner) or (b) reboot with the screen kept dark (MediaTek "IPO" / Quick Boot) or (c) timed sleep then deep sleep / shutdown (Duduauto default = 5 min) — confirmed in [forum.dudu-auto.com/d/2626](https://forum.dudu-auto.com/d/2626-ignition-off-head-unit-status-setting-possible-with-screen-off) and [forum.dudu-auto.com/d/787](https://forum.dudu-auto.com/d/787-power-on-without-4g) (where Nfilipe97 reports the `Dudu7 9.5" 7870` losing 4G specifically because hibernate vs cold boot behave differently).
* Practical implication: BOOT_COMPLETED rarely fires per key-cycle. The only universally reliable single trigger is `SCREEN_ON` (works everywhere because the firmware powers the panel as soon as ACC returns) — recommended fallback by HeadUnit Revived ("Start on screen on" feature) and Falcon_S on RealDash forum ([forum.realdash.net/t/joying-android-10-autostart/5021/9](https://forum.realdash.net/t/joying-android-10-autostart/5021/9)).

---

## 3. Built-in Settings Pickers ("Power-on app" / Factory menus)

### Duduauto (DUDU7 / DUDU OS) — confirmed

Two first-class user-facing options exist and are documented by Duduauto staff:

1. **`Settings → Canbus → Startup Settings → Select app to auto start on bootup`** — picks any installed app to launch automatically when the unit powers on. Source: `MihaiFlorin` post #3 in [forum.dudu-auto.com/d/482](https://forum.dudu-auto.com/d/482-dudu7-start-android-auto-be-default). Caveat from `dogsfoot` post #4: some apps (e.g. Carlink 2.0) do not appear in the picker — likely the picker only shows apps with a launcher intent filter.
2. **`Settings → More features → Tasks`**: trigger = "Vehicle ignition", delay = 2–3 s, action = "Open the app", select target. Source: same thread, post #3 and #5. This works for any installed app and supports a startup delay (essential for letting USB enumerate).
3. There is also `Settings → Vehicle → Startup Settings → Ignition-Off Head Unit Status` (the ACC-off delay 0–N minutes / sleep vs shutdown) — relevant to wake behavior. Source: [forum.dudu-auto.com/d/2626](https://forum.dudu-auto.com/d/2626-ignition-off-head-unit-status-setting-possible-with-screen-off).

### MTC / MediaTek factory engineer mode

* `*#*#3646633#*#*` from the dialer launches MediaTek `com.mediatek.engineermode` if installed. Confirmed at [android-headunits.com/com-mediatek-engineermode](https://android-headunits.com/com-mediatek-engineermode/). Will silently no-op on non-MTK units.

### Hidden factory / advanced-settings PINs

Reused codes across many Chinese head units (compiled from comments by users on [android-headunits.com/pin-codes-for-android-headunit](https://android-headunits.com/pin-codes-for-android-headunit/)):

* `8888` — most common factory password (Xtrons, Wondefoo, Alps Q92, many no-name units).
* `8878` — Xtrons "Factory Options Advanced".
* `3368` — Navifly, iDoing, iMars, Erisin "Factory Settings".
* `0000122` — Erisin "Debug Menu".
* `123456` — generic "extra options" / Awesafe factory.
* `1912` — Awesafe PX9.
* `1234` / `0000` / `000000` — generic.
* `888`, `5678`, `7749`, `110126` — boot-logo unlock variants.
* `8086` — launcher selection on some firmwares.
* Developer-options rolling code: `7890` + 2-digit current hour (so 18:00 → `789018`). Source: same page, "The code trick".
* Joying Sofia: `123456` for the "Fabric" page (per zmylna at [xdaforums.com/t/joying-isudar-annoying-application-autostart-resolved.4383323](https://xdaforums.com/t/joying-isudar-annoying-application-autostart-resolved.4383323/)).

### FYT-specific autostart-via-media-app trick

FYT firmware (Joying / Isudar / many UIS7862 units) re-launches the last-used media app on every wake. The list of apps it considers "media" lives in `assets/property/player_app.txt` inside `com.syu.ms.apk` and can be edited; alternatively setting `ro.build.go_lasttop=false` disables the last-app auto-resume entirely. Source: surfer63 and PieterD82 in the same XDA Joying/Isudar thread. **Implication for our DAB+/lyrics app**: if we declare ourselves with media-session metadata, on FYT firmware we will get auto-resume "for free" once the user has opened us once.

### Joying Android 12 (Snapdragon QCM6125) — third-party launcher unlock

Joying removed the "set default launcher" picker in Android 12. Sideload `CarSettings_jfw.apk` to expose `Settings → Display → Application → Theme` which then allows picking a third-party launcher. Source: [joyingauto.eu blog](https://www.joyingauto.eu/blog/post/tips-for-set-third-party-launcher-on-android-12-system/).

---

## 4. Launcher Replacement (HOME category)

Adding `<category android:name="android.intent.category.HOME" />` (plus `DEFAULT`) to our main Activity makes Android offer our app as a launcher candidate. On a unit where the user has not picked a default, the chooser appears every boot.

Trade-offs and pitfalls observed on head units:

* On Joying Android 12 / Snapdragon 6125 firmware the standard "set default launcher" flow is removed; Joying ships a separate `CarSettings_jfw.apk` you have to sideload to expose the launcher selector (link above).
* Vendor launchers (DUDU Launcher, Joying launcher, FYT launcher) own the dashboard widgets, MCU/CAN bridge, vehicle settings, and audio-zone state. Replacing them generally breaks reverse-camera triggers, steering-wheel-button mapping, and the volume-knob handlers. Falcon_S on RealDash forum recommends launcher replacement as the "total fix" but acknowledges it's a sledgehammer ([forum.realdash.net/t/joying-android-10-autostart/5021/9](https://forum.realdash.net/t/joying-android-10-autostart/5021/9)).
* RealDash workaround on Joying Android 10: register the app as the unit's "Navigation software" and turn on "boot navigation automatically". Bypasses the launcher debate entirely. Source: same RealDash thread.
* Pragmatic recommendation: **do NOT ship as a launcher by default**. Offer a manifest variant or a runtime opt-in for power users. Use the receiver + foreground service path first.

---

## 5. USB Device Race Condition on Boot

* `android.hardware.usb.action.USB_DEVICE_ATTACHED` does NOT fire for devices already enumerated when the listener is first registered — confirmed in Stack Overflow [#6163856](https://stackoverflow.com/questions/6163856/usb-device-attached-intent-not-firing). Manifest-declared receivers with `<usb-device>` filters do fire after boot once enumeration completes, but only for matched VID/PID, and not for HID-class devices.
* On Android 12+ initial USB-permission dialogs and `USB_DEVICE_ATTACHED` delivery have observed delays of ~20 s — see B4X thread on [b4x.com/.../144453](https://www.b4x.com/android/forum/threads/capturing-usb_device_attached-and-usb_device_detached-event.144453/).
* USB permissions are NOT guaranteed to persist across reboot — Google issuetracker [77658221](https://issuetracker.google.com/issues/77658221) — relevant for our DAB+ dongle: requesting permission with "always allow" generally persists, but plan for a re-prompt path.
* Duduauto specifically cuts USB power during deep sleep; the dongle takes 5–10 s to re-enumerate on wake (forum.dudu-auto.com/d/2626 above).
* **Recommended pattern**:

  ```kotlin
  // In the foreground service started from BootReceiver:
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      startForeground(NOTIF_ID, buildNotification()) // < 5 s, mediaPlayback
      // 1. Snapshot already-attached devices
      val um = getSystemService(USB_SERVICE) as UsbManager
      um.deviceList.values.firstOrNull(::isOurDab)?.let(::onDabAttached)
      // 2. Register runtime receiver for live attach/detach
      registerReceiver(usbReceiver, IntentFilter().apply {
          addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
          addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
      }, RECEIVER_NOT_EXPORTED)
      // 3. Schedule a delayed retry (10–15 s) for the Duduauto wake-from-sleep case
      handler.postDelayed(::pollDeviceList, 12_000)
      return START_STICKY
  }
  ```

---

## 6. Concrete Code Patterns

### `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<application ...>

    <!-- Boot / wake / power-on / ACC receivers -->
    <receiver
        android:name=".BootReceiver"
        android:enabled="true"
        android:exported="true"
        android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
        <intent-filter android:priority="1000">
            <!-- Standard AOSP -->
            <action android:name="android.intent.action.BOOT_COMPLETED" />
            <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
            <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            <!-- MediaTek / Quick Boot -->
            <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
            <action android:name="android.intent.action.BOOT_IPO" />
            <!-- OEM ACC / wake variants (defensive) -->
            <action android:name="com.fyt.boot.ACCON" />
            <action android:name="com.glsx.boot.ACCON" />
            <action android:name="com.cayboy.action.ACC_ON" />
            <action android:name="com.carboy.action.ACC_ON" />
            <action android:name="com.microntek.startApp" />
            <action android:name="com.xy.power.ACC_ON" />
            <action android:name="com.mtcd.action.ACC_ON" />
            <action android:name="com.mtce.action.ACC_ON" />
            <action android:name="com.wits.action.ACC_ON" />
            <action android:name="android.intent.action.ACC_ON" />
            <action android:name="com.android.action.ACC_ON" />
        </intent-filter>
    </receiver>

    <service
        android:name=".RadioForegroundService"
        android:exported="false"
        android:foregroundServiceType="mediaPlayback" />

</application>
```

### `BootReceiver.kt`

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootReceiver", "received ${intent.action}")
        val svc = Intent(context, RadioForegroundService::class.java).apply {
            putExtra("trigger", intent.action)
        }
        ContextCompat.startForegroundService(context, svc)
    }
}
```

### `RadioForegroundService.kt` skeleton

```kotlin
class RadioForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DAB+ radio")
            .setSmallIcon(R.drawable.ic_radio)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif) // MUST happen quickly
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // schedule USB poll + register live USB receiver here (see §5)
        // also register SCREEN_ON as belt-and-braces so we re-launch on wake
        return START_STICKY
    }
    override fun onBind(i: Intent?): IBinder? = null
}
```

`SCREEN_ON` cannot be declared in the manifest — it must be runtime-registered from the always-running foreground service. Once registered, every wake fires our handler, even if no boot or ACC broadcast arrives.

---

## 7. Known Gotchas

* **Stopped-state is the #1 killer**. After install or any force-stop the app is in `stopped=true` and receives ZERO implicit broadcasts (Android 3.1+). Mitigations: tell the user to open the app once after install; or use the firmware's autostart picker (DUDU `Tasks` / Startup Settings) which uses an explicit launcher intent and bypasses the stopped-state block. Source: HeadUnit Revived gist "Why doesn't auto-start work?" and Stack Overflow [#20441308](https://stackoverflow.com/questions/20441308/how-to-fix-boot-completed-not-working-android).
* **Apps installed to external/SD storage cannot receive `BOOT_COMPLETED`** — Android docs at [developer.android.com/guide/topics/data/install-location](https://developer.android.com/guide/topics/data/install-location). Set `android:installLocation="internalOnly"` (the default) on these head units that often have an SD card.
* **Battery optimisation on Chinese OEMs**. Recommended: prompt the user with `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and via ADB `dumpsys deviceidle whitelist +<pkg>`. General reference: [dontkillmyapp.com](https://dontkillmyapp.com/).
* **DuraSpeed (MediaTek)**. Aggressive background-process killer enabled by default on AC8227L / AC8257 / MT6765 head units. Disable via `Settings → Smart Assist → DuraSpeed` or `adb shell settings put global setting.duraspeed.enabled 0` (does not survive reboot — needs a MacroDroid task). Source: HeadUnit Revived gist Step 5.
* **Doze / app-standby**: head units run with constant 12 V → no battery state → Doze is effectively disabled, but App Standby Buckets can still demote the app. Whitelisting via `dumpsys deviceidle` covers both.
* **Firmware-level whitelists** (vendor-specific, root required):
  * Reglink (MT6765): `/system/etc/reglink_autorun_whitelist.txt`, `/system/etc/reglink_background_app_whitelist.txt`, `/system/etc/suspend_kill_whitelist.txt` — append package name to all three.
  * FYT/Joying UIS7862/UIS8581: `/oem/app/pwctl_config.xml` (only on firmware older than kernel 2023-12-26 for UIS7862 / 2023-12-08 for UIS8581). The Mario Dantas FYT Factory tool ([fytfactory.mariodantas.com](https://fytfactory.mariodantas.com/)) can edit it via UI.
  * MTCD/Microntek (Rockchip PX3/PX5/PX6, Android 6–9): hardcoded array in `MTCManager.apk` — requires apktool + smali edit. Detailed walkthrough at [xdaforums.com/t/3765437](https://xdaforums.com/t/how-to-whitelist-packages-on-almost-any-mtcd-head-unit.3765437/).
  * MTCD/HCT (Android 10+) and MTCH (Snapdragon QCM6125): hardcoded array in `HCTManagerService.apk` — replace an unused entry, do NOT grow the array.
  * All four documented step-by-step in the [andrecuellar gist](https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38).
* **FYT auto-resume of last media app**. If the unit is FYT-based and our app is in `player_app.txt`, the unit will re-open us on next wake whether we want it or not — useful in our case. Disabling: `setprop ro.build.go_lasttop false` (see XDA thread 4383323 by surfer63/PieterD82).
* **USB power cut on deep sleep** → DAB+ dongle is unmounted; on wake it takes 5–10 s to re-enumerate. Confirmed for Duduauto by `slizzap` and `DUDU-Meng` at [forum.dudu-auto.com/d/2626](https://forum.dudu-auto.com/d/2626-ignition-off-head-unit-status-setting-possible-with-screen-off).
* **CAN-bus serial spurious wake-up**: `Mikescotland`'s case at [forum.dudu-auto.com/d/1771](https://forum.dudu-auto.com/d/1771-carlink-starting-on-power-on-unnecessarily) shows that the CANBUS UART line going high 3.3 V on power-on causes the system to launch Carlink unexpectedly. Worth knowing because users may report our app being launched on every key-on even without our autostart code (the CAN bus may be triggering the firmware's "phone connected" pathway).

---

## 8. ADB Verification Commands

```bash
# Confirm package is not in stopped state (must say stopped=false)
adb shell dumpsys package <pkg> | grep -iE "stopped|flags|codePath|User 0"

# Simulate BOOT_COMPLETED to our app (works even without root if receiver is exported)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p <pkg>

# Simulate the OEM ACC variants
adb shell am broadcast -a com.fyt.boot.ACCON -p <pkg>
adb shell am broadcast -a com.cayboy.action.ACC_ON -p <pkg>
adb shell am broadcast -a android.intent.action.QUICKBOOT_POWERON -p <pkg>

# Watch the broadcast queue to see what the firmware actually sends during a real key cycle
adb shell dumpsys activity broadcasts | grep -iE "ACC|BOOT_COMPLETED|QUICKBOOT|power|wake|screen"

# Live logcat across all buffers during real ignition cycle
adb logcat -b all -v threadtime | grep -iE "ACC|BOOT_COMPLETED|QUICKBOOT|power|am_kill|force.stop"

# Doze / battery optimisation whitelist
adb shell dumpsys deviceidle whitelist
adb shell dumpsys deviceidle whitelist +<pkg>

# Find the vendor launcher and OEM service packages
adb shell pm list packages | grep -iE "launcher|microntek|fyt|reglink|joying|dudu|mtc|hct|wits"

# Identify chipset / firmware (for picking the right whitelist guide)
adb shell getprop | grep -iE "ro.board.platform|ro.product.model|ro.build.display.id|duraspeed|mediatek|allwinner|sun"

# Check what fired the kill (root or shell user 2000 needed)
adb shell logcat -b events -d | grep -iE "am_kill|am_proc_died" | tail -20

# Snapshot already-connected USB devices on a fresh boot (verifies the race)
adb shell dumpsys usb | grep -iE "device|host"

# Probe for vendor whitelist files (root required)
adb shell ls /system/etc/reglink_*.txt
adb shell ls /oem/app/pwctl_config.xml
adb shell ls /system/priv-app/MTCManager
adb shell ls /system/priv-app/HCTManagerService
```

---

## Recommended Layered Approach (full picture)

In priority order:

1. **User-facing first**: document for the user to set our app in `Settings → Canbus → Startup Settings` on Duduauto, or create a `Settings → More features → Tasks` task (Trigger = Vehicle Ignition, Delay = 3 s, Action = Open the app). Zero code changes.
2. **Manifest receivers**: declare every action listed in §6. Most defensive option.
3. **Foreground service `mediaPlayback`** with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission. `startForeground` < 5 s.
4. **USB**: poll `UsbManager.deviceList` snapshot + runtime attach receiver + 12 s delayed retry.
5. **SCREEN_ON runtime receiver** inside the FGS — the only universally reliable wake-time trigger across MTK/Allwinner/Unisoc.
6. **Battery / Doze whitelist**: prompt user with `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; document the ADB `deviceidle whitelist +pkg` fallback.
7. **Optional**: ship a `CarLauncher` / `HOME` flavor for users who want to fully replace the Duduauto launcher (advanced, breaks vehicle integration).
8. **Optional**: if running on FYT firmware, hint to power users that adding our package to `assets/property/player_app.txt` inside `com.syu.ms.apk` makes auto-resume bulletproof.
9. **Last-resort fallback**: Tasker / MacroDroid macro on the user's unit, trigger = Display On, action = Launch App.

---

## Recommended Next Research (not completed in this session)

- [ ] Decompile DUDU OS launcher / DUDU Tasks app to extract the actual broadcast or intent it uses for "Vehicle Ignition" trigger — would let us tap the trigger directly without going through the Tasks UI.
- [ ] Confirm presence and exact path of any DUDU autostart whitelist file under `/system/etc/` or `/oem/` (analogous to Reglink's `*_whitelist.txt`).
- [ ] Pull a real `dumpsys activity broadcasts` from a live DUDU7 across a key cycle to enumerate the ACTUAL OEM intents fired (the public folklore list above is incomplete).
- [ ] Verify whether `com.xy.power.ACC_ON`, `com.mtcd.action.ACC_ON`, `com.mtce.action.ACC_ON`, `com.wits.action.ACC_ON` strings are real (no primary source found in this session — GitHub code search returned 0 hits, suggesting they may be myth).
- [ ] Test on the user's actual unit whether `Settings → Canbus → Startup Settings → Select app to auto start on bootup` lists our app once it has a launcher Activity, or whether it filters by some other criterion (`dogsfoot`'s post #4 reports Carlink 2.0 is missing from the list).
- [ ] Investigate ATOTO and Mekede-specific autostart settings paths (Mekede manufactures the DUDU7 hardware and may expose vendor-internal options under different menu names on non-DUDU firmware images).
- [ ] Authoritative AOSP source citations for stopped-state semantics (`PackageManager.MATCH_DIRECT_BOOT_AWARE` and the `ActivityManagerService` stopped-state guard) — would be useful to back the claim that the firmware's force-stop step blocks our receivers.

---

## Clarifying Questions

1. Which exact firmware build is on the user's DUDU7? (`Settings → About → long-press top-left of the picture` produces the system info screenshot — protocol from [forum.dudu-auto.com/d/787](https://forum.dudu-auto.com/d/787-power-on-without-4g) post #3.) The firmware build determines whether the `Canbus → Startup Settings` picker, the `Tasks` system, or both are present.
2. Is the user comfortable with ADB access (USB or WiFi-ADB on port 5555 — many Chinese head units have it open by default)? Several diagnostics and the battery-optimisation whitelist depend on it.
3. Is rooting the unit acceptable to the user? It is required for the firmware-level whitelist edits (only relevant if the layered, no-root approach proves unreliable).
4. Does the user want our app to fully take over as the launcher (HOME), or to coexist with DUDU Launcher and just auto-launch on key-on?

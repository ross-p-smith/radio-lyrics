<!-- markdownlint-disable-file -->
# Task Research: Autostart Radio-Lyric App on Duduauto Head Unit Boot

Make the Android DAB+ radio + live lyrics app launch automatically every time the Duduauto 7 head unit powers on (key-turn / ACC-on / wake from sleep), so the user never has to tap the icon — matching the behavior the existing Mekede DAB-Z app fails to provide today.

## Task Implementation Requests

* Identify reliable mechanisms for launching an Android app at head-unit power-on.
* Determine which Duduauto-specific power / boot signals exist (Settings pickers, Tasks, broadcast intents).
* Recommend one resilient primary strategy plus layered fallbacks.
* Cover edge cases: stopped-state, USB DAB dongle not yet enumerated at wake, foreground-service rules on Android 12+, vendor "kill on shutdown" lists.
* Document Settings the end user must configure (or ADB commands they can run).

## Scope and Success Criteria

* Scope: Autostart of the Radio-Lyric Android app on a Duduauto 7 head unit. Covers built-in DUDU autostart pickers, vendor power/ACC intents, AOSP `BOOT_COMPLETED` family, foreground service rules, USB attach race, and battery-optimisation whitelisting.
* Out of scope: The radio + lyrics features themselves (covered in `.copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md`); rooting the unit; firmware replacement.
* Assumptions:
  * Duduauto 7 runs DUDU OS atop Android 10/12 on a Unisoc 7870-class SoC (Mekede-manufactured).
  * The unit exposes ADB (already used in the radio research).
  * The user has access to `Settings → Canbus` and `Settings → More features → Tasks`.
  * The DAB+ USB dongle remains plugged in across key cycles (USB power is cut during deep sleep — re-enumeration takes 5–10 s).
* Success Criteria:
  * One recommended primary autostart approach with rationale and primary-source evidence.
  * At least two fallback approaches with trade-offs.
  * Concrete `AndroidManifest.xml`, `BroadcastReceiver`, and foreground-service skeletons.
  * Mitigation for the USB-not-ready-at-wake race.
  * ADB test plan to verify autostart on the user's actual unit.

## Outline

1. The single most important fact: **DUDU OS already ships a first-class autostart picker** — `Settings → Canbus → Startup Settings → Select app to auto start on bootup`, plus `Settings → More features → Tasks` (Trigger = Vehicle Ignition, Delay = 2–3 s, Action = Open the app). Both are documented by Duduauto staff.
2. The autostart picker uses an explicit launcher intent, which **bypasses the Android stopped-state block** that breaks every implicit-broadcast-based scheme.
3. Backup mechanism: manifest-declared `BroadcastReceiver` for AOSP boot intents (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `QUICKBOOT_POWERON`, `BOOT_IPO`) plus the small set of OEM ACC intents with primary-source evidence (`com.fyt.boot.ACCON`, `com.glsx.boot.ACCON`, `com.cayboy.action.ACC_ON`, `com.carboy.action.ACC_ON`, `com.microntek.startApp`).
4. Backup-of-backup: a runtime-registered `SCREEN_ON` receiver inside the always-running foreground service — the only universally reliable wake trigger across MTK / Allwinner / Unisoc firmwares.
5. Foreground-service compliance (Android 12+): `foregroundServiceType="mediaPlayback"` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission. `BOOT_COMPLETED` is on the documented allow-list of contexts permitted to start an FGS from the background.
6. USB race: do not assume `USB_DEVICE_ATTACHED` fires at boot for an already-connected dongle. Snapshot `UsbManager.deviceList`, register a runtime attach receiver, schedule a 12 s delayed retry to cover the wake-from-deep-sleep re-enumeration delay.
7. Verification path: ADB commands to simulate each broadcast, watch the actual broadcast queue during a real key cycle, and confirm the app is not in `stopped` state.

## Potential Next Research

* Pull a real `dumpsys activity broadcasts` from the user's live DUDU7 across a key cycle to enumerate the ACTUAL OEM intents fired.
  * Reasoning: The folklore list (`com.xy.power.ACC_ON`, `com.mtcd.action.ACC_ON`, `com.wits.ACC_ON`) returned zero hits in primary-source search. We include them defensively but should confirm what DUDU OS truly broadcasts.
  * Reference: `.copilot-tracking/research/subagents/2026-05-01/headunit-autostart-research.md`, §2 and §8.
* Decompile the DUDU `Tasks` app to find the actual "Vehicle Ignition" trigger broadcast.
  * Reasoning: If we can listen for that intent directly, we can drop the manual Tasks-app configuration step.
* Confirm whether the `Canbus → Startup Settings → Select app to auto start on bootup` picker lists our app once it has a `MAIN`/`LAUNCHER` activity.
  * Reasoning: Forum user `dogsfoot` reports Carlink 2.0 is missing from the picker, suggesting a hidden filter (possibly category-based).
  * Reference: `forum.dudu-auto.com/d/482` post #4.
* Confirm DUDU7 firmware build on the user's unit (`Settings → About → long-press top-left of the picture` produces the system-info screenshot per `forum.dudu-auto.com/d/787`).
  * Reasoning: Determines whether `Canbus → Startup Settings`, `Tasks`, or both are present.

## Research Executed

### File Analysis

* `.copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md`
  * Confirms target hardware (Duduauto 7, Mekede-built, Unisoc 7870-class), Android 10/12, ADB available, and that a DAB+ foreground service is already in scope — the autostart layer plugs straight into that service.

### Code Search Results

_None — greenfield workspace._

### External Research

* Subagent (Researcher Subagent): head-unit autostart mechanisms
  * Output: `.copilot-tracking/research/subagents/2026-05-01/headunit-autostart-research.md`
  * Confirmed primary sources for the DUDU autostart pickers (`forum.dudu-auto.com/d/482`, `/d/2626`, `/d/787`), the OEM ACC intent table (HeadUnit Revived gist by `andrecuellar`), Android FGS rules (developer.android.com), USB attach behavior (Stack Overflow #6163856), and vendor whitelist file paths.

### Project Conventions

* Follow Android Kotlin + Compose + foreground-service conventions established by `.copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md`.
* Keep autostart code in a small, isolated module (a `BootReceiver` + reuse of the existing `RadioForegroundService`) so it can be disabled if the user prefers manual launch.

## Key Discoveries

### DUDU OS has a built-in autostart picker (the headline finding)

Documented on `forum.dudu-auto.com/d/482` ("DUDU7 start Android Auto by default") by Duduauto staff `MihaiFlorin` (post #3) and `DUDU-Meng` (post #5):

* **Path A — Canbus picker**: `Settings → Canbus → Startup Settings → Select app to auto start on bootup`. Pick the Radio-Lyric app, save, done.
* **Path B — Tasks**: `Settings → More features → Tasks → +` then Trigger = `Vehicle Ignition`, Delay = `2–3 s`, Action = `Open the app`, App = Radio-Lyric. Path B supports a startup delay, which we want anyway to let the USB dongle re-enumerate.
* **Why this is decisive**: Both paths fire an **explicit launcher intent**, which means they bypass the Android stopped-state block (Android 3.1+ refuses every implicit broadcast — including `BOOT_COMPLETED` — to apps in the stopped state, which Duduauto force-stops them into during the 20–30 s shutdown countdown). This is the single biggest reason DAB-Z and Carlink "won't auto-start" until the user opens them once per cycle.

Caveat: forum user `dogsfoot` (`forum.dudu-auto.com/d/482` post #4) reports that some apps (e.g. Carlink 2.0) do not appear in the Canbus picker — likely because the picker filters by some intent-filter criterion. Our app will declare a standard `MAIN`/`LAUNCHER` activity (per the radio research), so it should appear.

### Duduauto sleep behavior dictates the USB pattern

`DUDU-Meng` on `forum.dudu-auto.com/d/2626`: "After 5 minutes of power loss, the ACC enters sleep mode, at which point the USB power supply is disconnected." Combined with `slizzap`'s observation in the same thread, the Mekede DAB+ dongle takes **5–10 s** to re-enumerate after wake. Our autostart code MUST therefore:

1. Start the foreground service immediately (so we satisfy the FGS-from-boot allow-list).
2. Snapshot `UsbManager.deviceList` in `onStartCommand` for the case where enumeration already finished before the service started.
3. Register a runtime `USB_DEVICE_ATTACHED` receiver for the case where enumeration is still in flight.
4. Schedule a delayed (12 s) retry as a third safety net.

### Vendor ACC intents — what's real, what's folklore

From the `andrecuellar` HeadUnit Revived gist (the most thorough primary-source survey of head-unit firmwares on GitHub):

* **Confirmed real** (cited firmware sources): `com.fyt.boot.ACCON` (FYT / Joying / KLD / Xtrons UIS7862, Android 7 only), `com.glsx.boot.ACCON`, `com.cayboy.action.ACC_ON` (Microntek/MTCD on Rockchip PX3), `com.carboy.action.ACC_ON`, `com.microntek.startApp`, `android.intent.action.QUICKBOOT_POWERON` (MediaTek), `android.intent.action.BOOT_IPO` (MediaTek IPO hibernate), `com.htc.intent.action.QUICKBOOT_POWERON`.
* **Folklore** (no primary source surfaced; GitHub code search returned 0 hits): `com.xy.power.ACC_ON`, `com.mtcd.action.ACC_ON`, `com.mtce.action.ACC_ON`, `com.wits.ACC_ON`. Include defensively in the manifest filter; do not rely on them.
* **Duduauto-specific ACC broadcast**: not publicly documented. DUDU OS exposes the equivalent via the Tasks `Vehicle Ignition` trigger (a Tasks-internal broadcast we cannot listen to without decompiling the Tasks app).

### Android 12+ foreground-service rule that matters

Per `developer.android.com/develop/background-work/services/fgs/launch`, `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` are on the documented allow-list of contexts permitted to start a foreground service from the background. The pattern `BootReceiver → Context.startForegroundService → Service.startForeground(...)` is officially supported and survives every Android version through 14.

For our app: `foregroundServiceType="mediaPlayback"` + `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />`. `startForeground(...)` must be called within ~5 s of `onStartCommand` or the system kills the service with `ForegroundServiceDidNotStartInTimeException`.

### Stopped-state is the single biggest gotcha

After install — and after every Duduauto shutdown countdown that force-stops third-party apps — the app is in `stopped=true`. Android 3.1+ blocks **all** implicit broadcasts to stopped apps, so `BOOT_COMPLETED` and every OEM ACC intent silently never arrive. The two ways out:

1. The user opens the app once after install (clears stopped state for that boot cycle only).
2. Something fires an **explicit** intent at our app — which is exactly what the DUDU `Canbus Startup Settings` and `Tasks` pickers do.

This is why the Settings-based path is the recommended primary, and the receiver-based path is a backup.

### Implementation Patterns

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<application android:installLocation="internalOnly" ...>

    <receiver
        android:name=".autostart.BootReceiver"
        android:enabled="true"
        android:exported="true"
        android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
        <intent-filter android:priority="1000">
            <!-- AOSP -->
            <action android:name="android.intent.action.BOOT_COMPLETED" />
            <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
            <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            <!-- MediaTek / Quick Boot -->
            <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
            <action android:name="android.intent.action.BOOT_IPO" />
            <!-- OEM ACC variants (defensive — confirmed + folklore) -->
            <action android:name="com.fyt.boot.ACCON" />
            <action android:name="com.glsx.boot.ACCON" />
            <action android:name="com.cayboy.action.ACC_ON" />
            <action android:name="com.carboy.action.ACC_ON" />
            <action android:name="com.microntek.startApp" />
            <action android:name="android.intent.action.ACC_ON" />
            <action android:name="com.android.action.ACC_ON" />
            <action android:name="com.xy.power.ACC_ON" />
            <action android:name="com.mtcd.action.ACC_ON" />
            <action android:name="com.mtce.action.ACC_ON" />
            <action android:name="com.wits.action.ACC_ON" />
        </intent-filter>
    </receiver>

    <service
        android:name=".radio.RadioForegroundService"
        android:exported="false"
        android:foregroundServiceType="mediaPlayback" />

</application>
```

`BootReceiver.kt`:

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootReceiver", "received ${intent.action}")
        val svc = Intent(context, RadioForegroundService::class.java)
            .putExtra("trigger", intent.action)
        ContextCompat.startForegroundService(context, svc)
    }
}
```

`RadioForegroundService` autostart hook (extends the service from the radio research):

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIF_ID, buildNotification()) // < 5 s, mediaPlayback
    val um = getSystemService(USB_SERVICE) as UsbManager
    // 1. Snapshot devices already attached
    um.deviceList.values.firstOrNull(::isMekedeDab)?.let(::onDabAttached)
    // 2. Live attach receiver for the wake-from-sleep re-enumeration window
    registerReceiver(usbReceiver, IntentFilter().apply {
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    }, RECEIVER_NOT_EXPORTED)
    // 3. SCREEN_ON belt-and-braces (cannot be manifest-declared)
    registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    // 4. Delayed retry for the Duduauto USB-power-cut-on-deep-sleep case
    handler.postDelayed(::pollDeviceList, 12_000)
    return START_STICKY
}
```

### Complete Examples

User-facing setup steps (to ship in `README.md`):

1. Install the Radio-Lyric app and open it once (clears stopped state).
2. On the head unit: `Settings → Canbus → Startup Settings → Select app to auto start on bootup` → pick **Radio-Lyric**. _If the app is not listed_, use the Tasks fallback below.
3. Tasks fallback: `Settings → More features → Tasks → +` → Trigger `Vehicle Ignition`, Delay `3 s`, Action `Open the app`, App `Radio-Lyric`, Save.
4. Optional ADB hardening: `adb shell dumpsys deviceidle whitelist +com.example.radiolyric`.
5. Cycle ignition off and on. The app should appear within ~3 s of the screen coming up.

### API and Schema Documentation

* Android FGS-from-boot allow-list: <https://developer.android.com/develop/background-work/services/fgs/launch#allowed-fgs-launches>
* `foregroundServiceType="mediaPlayback"` permission requirement (Android 14): <https://developer.android.com/about/versions/14/changes/fgs-types-required#media>
* Implicit-broadcast restrictions and the BOOT_COMPLETED exemption: <https://developer.android.com/develop/background-work/background-tasks/broadcasts>
* `USB_DEVICE_ATTACHED` does not fire for already-enumerated devices: <https://stackoverflow.com/questions/6163856/usb-device-attached-intent-not-firing>
* DUDU autostart pickers (primary source): <https://forum.dudu-auto.com/d/482-dudu7-start-android-auto-be-default>
* DUDU sleep / USB-power-cut behavior (primary source): <https://forum.dudu-auto.com/d/2626-ignition-off-head-unit-status-setting-possible-with-screen-off>
* OEM ACC intent survey: <https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38>

### Configuration Examples

ADB verification commands the user can run on their unit:

```bash
# Confirm the app is not in the stopped state
adb shell dumpsys package com.example.radiolyric | grep -iE "stopped|flags"

# Simulate the AOSP and OEM broadcasts
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.radiolyric
adb shell am broadcast -a com.fyt.boot.ACCON                  -p com.example.radiolyric
adb shell am broadcast -a com.cayboy.action.ACC_ON            -p com.example.radiolyric
adb shell am broadcast -a android.intent.action.QUICKBOOT_POWERON -p com.example.radiolyric

# Watch what DUDU OS actually fires during a real key cycle
adb shell dumpsys activity broadcasts | grep -iE "ACC|BOOT_COMPLETED|QUICKBOOT|power|wake|screen"
adb logcat -b all -v threadtime | grep -iE "ACC|BOOT_COMPLETED|am_kill|force.stop"

# Battery / Doze whitelist (survives reboot)
adb shell dumpsys deviceidle whitelist +com.example.radiolyric

# Identify chipset / firmware (decides which whitelist guide applies if needed)
adb shell getprop | grep -iE "ro.board.platform|ro.product.model|ro.build.display.id"
```

## Technical Scenarios

### Scenario A — DUDU OS Settings autostart picker (PRIMARY)

The user picks Radio-Lyric in `Settings → Canbus → Startup Settings → Select app to auto start on bootup`, optionally backed by a `Settings → More features → Tasks` task with Trigger = Vehicle Ignition / Delay = 3 s.

**Requirements:**

* App must declare a standard `MAIN`/`LAUNCHER` activity (already required by the radio research).
* App opened once after install (clears stopped state).

**Preferred Approach:**

* This is the recommended primary mechanism. It uses an explicit launcher intent, which bypasses the Android stopped-state block that defeats `BOOT_COMPLETED`-based schemes on these head units. Documented and supported by Duduauto staff. Requires zero code beyond what the radio research already specifies.

```text
Radio-Lyric (no extra code beyond the radio research)
├── AndroidManifest.xml
│   └── activity MainActivity { intent-filter { action MAIN; category LAUNCHER } }
└── docs/install.md  ← user-facing 3-step setup
```

**Implementation Details:**

The radio app already needs a launcher activity for normal use. The autostart "implementation" is purely a documentation deliverable: the README walks the user through `Settings → Canbus → Startup Settings`, with the `Tasks` path as a same-page fallback for firmware builds where the Canbus picker is missing.

#### Considered Alternatives

* **Receiver + foreground service only (no Settings picker)**: defeated by stopped-state on every shutdown countdown. Rejected as primary; retained as Scenario B for cases where the picker is missing.
* **Launcher replacement (HOME category)**: replaces DUDU Launcher, breaks vehicle widgets, reverse camera, steering-wheel mapping. Rejected — too invasive for the benefit. Retained as Scenario C for power users.
* **Tasker / MacroDroid third-party automation**: requires an extra app the user has to buy/install/maintain. Rejected as primary — listed as a last-resort fallback in §9 of the subagent doc.

### Scenario B — Defensive manifest receivers + foreground service (BACKUP)

Manifest receiver listening for the full set of AOSP boot intents and confirmed OEM ACC intents, fanning out to a `mediaPlayback` foreground service that owns the USB DAB pipeline and a runtime `SCREEN_ON` receiver.

**Requirements:**

* `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` permissions.
* `android:installLocation="internalOnly"` (otherwise BOOT_COMPLETED is suppressed).
* App opened once after install.
* Battery-optimisation whitelist entry.

**Preferred Approach:**

* Ship this code regardless of Scenario A — it costs us almost nothing, provides redundancy on the cold-boot path, and gives us the always-on foreground service we need anyway for media playback.

**Implementation Details:**

See manifest, `BootReceiver.kt`, and `RadioForegroundService` snippets above. The runtime `SCREEN_ON` receiver inside the FGS is the only universally reliable wake trigger across MTK / Allwinner / Unisoc firmwares.

#### Considered Alternatives

* **Receiver fanning straight to an Activity (`startActivity` from receiver)**: works on older firmwares but is blocked by Android 10's background-activity-start restriction without `SYSTEM_ALERT_WINDOW`. Rejected — fan to the foreground service instead, which can call `startActivity` after `startForeground`.

### Scenario C — Replace DUDU Launcher (LAST-RESORT, opt-in)

Ship a `CarLauncher` build flavor that adds `<category android:name="android.intent.category.HOME" />` to the main activity, becoming the unit's launcher.

**Requirements:**

* Separate Gradle product flavor so the default build does not claim HOME.
* User flow to set us as default launcher (on Joying Android 12 this requires sideloading `CarSettings_jfw.apk` per <https://www.joyingauto.eu/blog/post/tips-for-set-third-party-launcher-on-android-12-system/>).

**Preferred Approach:**

* Ship as opt-in only. Most users want to keep DUDU Launcher (it owns vehicle widgets, the reverse-camera trigger, the volume knob, and the steering-wheel button mapping). Document but do not enable by default.

#### Considered Alternatives

* **Hijack the existing launcher slot at runtime**: not possible without root.

## Recommendation

**Ship Scenarios A and B together; reserve C for power users.**

* Scenario A is the primary mechanism, documented in the user-facing README, and requires zero autostart code in the app beyond what the radio research already specifies.
* Scenario B ships in the same APK as a transparent backup. The receiver and foreground service we'd build anyway for media playback double as the BOOT_COMPLETED handler, with a runtime `SCREEN_ON` receiver inside the service as a third-tier safety net.
* Both scenarios depend on the user opening the app once after install (to clear stopped state) and on the USB-attach handling pattern in §5 of the subagent doc to absorb the Duduauto USB-power-cut-on-deep-sleep window.

<!-- markdownlint-disable-file -->
# Debugging an Android DAB-Radio App Without Easy Hardware Access

Subagent research document — investigates how to develop and debug an Android app whose primary data source is a USB-attached DAB radio dongle that lives in a car (Duduauto 7 head unit).

Status: Complete

## Research Topics / Questions

1. Hardware-source abstraction patterns (sealed `RadioSource` with real and fake implementations exposing `Flow<NowPlaying>`).
2. ADB-over-WiFi/TCP setup on Android head units (Duduauto), persistence, scrcpy in the car.
3. Recording real DLS/DL+ streams once and replaying them on the dev machine (file format suggestion).
4. Android Studio emulator USB-host limits and concrete alternatives.
5. In-car iteration workflow: build → push APK over WiFi ADB → auto-launch via `am start`.
6. USB traffic logging for protocol RE (usbmon / Wireshark USBPcap) — pointer only.
7. Test doubles for `MediaSession`/foreground audio service so UI work proceeds without real audio.

---

## 1. Abstracting the hardware data source

Use a Kotlin `sealed interface` so the rest of the app depends on the abstraction, not on the USB stack. All implementations expose a cold `Flow<NowPlaying>` so subscribers get the latest scripted (or real) DLS/DL+ events.

```kotlin
// domain/RadioSource.kt
package com.example.radiolyric.domain

import kotlinx.coroutines.flow.Flow

data class DlPlusTag(val contentType: String, val text: String) // ETSI TS 102 980 §6
data class NowPlaying(
    val timestampMs: Long,
    val service: String,         // DAB service / station name
    val artist: String?,
    val title: String?,
    val dls: String?,            // raw DLS line
    val dlPlus: List<DlPlusTag> = emptyList()
)

sealed interface RadioSource {
    val events: Flow<NowPlaying>
    suspend fun start()
    suspend fun stop()
}
```

```kotlin
// data/RealUsbDabSource.kt
package com.example.radiolyric.data

import android.hardware.usb.UsbManager
import com.example.radiolyric.domain.NowPlaying
import com.example.radiolyric.domain.RadioSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class RealUsbDabSource(
    private val usbManager: UsbManager,
    private val protocol: DabProtocol     // your wire-format parser
) : RadioSource {

    override val events: Flow<NowPlaying> = callbackFlow {
        val handle = protocol.open(usbManager) { np -> trySend(np) }
        awaitClose { handle.close() }
    }

    override suspend fun start() = protocol.startStreaming()
    override suspend fun stop()  = protocol.stopStreaming()
}
```

```kotlin
// data/FakeDabSource.kt
package com.example.radiolyric.data

import com.example.radiolyric.domain.DlPlusTag
import com.example.radiolyric.domain.NowPlaying
import com.example.radiolyric.domain.RadioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

/** Emits a scripted sequence of NowPlaying events. Use on emulator / phone. */
class FakeDabSource(
    private val script: List<Pair<Long, NowPlaying>> = defaultScript()
) : RadioSource {

    override val events: Flow<NowPlaying> = flow {
        var last = 0L
        for ((delayMs, np) in script) {
            delay(delayMs - last); last = delayMs
            emit(np)
        }
    }

    override suspend fun start() = Unit
    override suspend fun stop()  = Unit

    companion object {
        fun defaultScript(): List<Pair<Long, NowPlaying>> {
            val t0 = System.currentTimeMillis()
            return listOf(
                0L     to NowPlaying(t0,        "BBC Radio 6 Music", null, null, "Welcome to 6 Music"),
                3_000L to NowPlaying(t0+3_000,  "BBC Radio 6 Music", "Radiohead", "Idioteque",
                    "Now playing: Radiohead - Idioteque",
                    listOf(DlPlusTag("ITEM.ARTIST","Radiohead"), DlPlusTag("ITEM.TITLE","Idioteque"))),
                30_000L to NowPlaying(t0+30_000,"BBC Radio 6 Music", "Aphex Twin", "Xtal",
                    "Now playing: Aphex Twin - Xtal",
                    listOf(DlPlusTag("ITEM.ARTIST","Aphex Twin"), DlPlusTag("ITEM.TITLE","Xtal")))
            )
        }
    }
}
```

Inject via Hilt/Koin; use a Gradle build flavor or a `BuildConfig.USE_FAKE_RADIO` flag to swap implementations:

```kotlin
@Provides @Singleton
fun provideRadioSource(usb: UsbManager, proto: DabProtocol): RadioSource =
    if (BuildConfig.USE_FAKE_RADIO) FakeDabSource() else RealUsbDabSource(usb, proto)
```

DL Plus content-type names follow ETSI TS 102 980 §6 (https://www.etsi.org/deliver/etsi_ts/102900_102999/102980/).

---

## 2. ADB-over-WiFi / TCP on the Duduauto head unit

Most aftermarket Android head units (Duduauto, Joying, Eonon, etc.) run AOSP-derived Android 10–13. The Pixel-style "Wireless debugging" pairing UI from Android 11+ is often missing or broken on these ROMs, so the **legacy `adb tcpip` flow is the reliable path**.

### 2.1 Enable Developer Options on Duduauto

1. Settings → System → About → tap **Build number** 7 times.
2. Settings → System → Developer options → enable **USB debugging** *and* **ADB over network** if present.
3. Most Duduauto firmwares also expose a hidden "Factory" / "CarSet" menu (e.g., type `0000` or `1217` in the radio app's settings) that contains an **"ADB"** toggle — turn it on; this often makes the tcpip mode persist across reboots.

### 2.2 Initial USB → switch to TCP

Plug the head unit into your laptop with a USB-A → USB-A or USB-C cable (the Duduauto's debug port is usually a small USB-A on the harness pigtail labelled **"PC"** or **"ADB"**).

```bash
adb devices                   # confirm USB device appears
adb tcpip 5555                # restart adbd in TCP mode on port 5555
adb shell ip route | awk '{print $9}'   # find the head unit's IP on the car's WiFi/AP
adb connect 192.168.1.42:5555 # connect over WiFi
adb devices                   # should show 192.168.1.42:5555  device
```

Reference: Android Debug Bridge docs, *Connect wirelessly with a device after an initial USB connection* (https://developer.android.com/tools/adb#wireless).

### 2.3 Persistence across reboots

`adb tcpip 5555` is reset every boot on stock Android. Workarounds, in order of preference:

1. **Built-in "ADB over network" toggle** in Developer Options or the OEM Factory menu — if Duduauto exposes it, this is by far the cleanest; the OEM init script re-applies it.
2. **Persistent system property** (requires the head unit to honour it; many car ROMs do):
   ```bash
   adb shell "su -c 'setprop persist.adb.tcp.port 5555 && stop adbd && start adbd'"
   ```
   `persist.*` properties survive reboots. After this, `adbd` listens on 5555 from boot. Root may or may not be available on Duduauto; check with `adb shell su -c id`.
3. **Companion app**: install [WiFiADB](https://f-droid.org/packages/com.ttxapps.wifiadb/) or similar — they run a foreground service that re-issues `setprop` after each boot.

### 2.4 Phone-as-hotspot for the car

The car's head unit usually has WiFi client mode. Tether your laptop **and** the head unit to your phone's hotspot so they share the same subnet; then `adb connect` works without messing with the car's WiFi config.

### 2.5 scrcpy for screen mirroring inside the car

`scrcpy` works over the same TCP/IP transport — no extra installation on the device (https://github.com/Genymobile/scrcpy, *TCP/IP (wireless)* section).

```bash
# After adb connect 192.168.1.42:5555:
scrcpy --tcpip=192.168.1.42:5555 --max-size=1280 --max-fps=30 --no-audio
# Or just:
scrcpy -m1280 --max-fps=30 --no-audio
```

Tips for the car:

* `--max-size=1280 --max-fps=30` keeps latency usable over WiFi.
* `--no-audio` avoids fighting the car's audio routing.
* `--turn-screen-off -K` (physical-keyboard HID) lets you type while the head-unit display stays dark.
* `scrcpy --otg` is **not** useful here — that mode bypasses the device entirely and uses the laptop's USB stack.

---

## 3. Record real DLS/DL+ once, replay on the desk

Build a tiny **logger Activity** (or a flag in the real app) that runs once in the car with the real dongle attached and dumps every `NowPlaying` event the parser emits. JSONL is ideal: one self-describing event per line, easy to `tail`, easy to diff, easy to feed back into `FakeDabSource`.

Suggested file (`dls-capture.jsonl`):

```jsonl
{"t":1714492801123,"service":"BBC R6 Music","artist":"Radiohead","title":"Idioteque","dls":"Now playing: Radiohead - Idioteque","dl_plus":[{"type":"ITEM.ARTIST","text":"Radiohead"},{"type":"ITEM.TITLE","text":"Idioteque"}]}
{"t":1714492804001,"service":"BBC R6 Music","artist":"Radiohead","title":"Idioteque","dls":"www.bbc.co.uk/6music","dl_plus":[{"type":"STATIONNAME.LONG","text":"BBC Radio 6 Music"}]}
{"t":1714492831550,"service":"BBC R6 Music","artist":"Aphex Twin","title":"Xtal","dls":"Now playing: Aphex Twin - Xtal","dl_plus":[{"type":"ITEM.ARTIST","text":"Aphex Twin"},{"type":"ITEM.TITLE","text":"Xtal"}]}
```

Logger sketch:

```kotlin
class JsonlDlsLogger(file: File) {
    private val out = file.bufferedWriter()
    private val json = Json { encodeDefaults = false }
    fun log(np: NowPlaying) {
        out.appendLine(json.encodeToString(np)); out.flush()
    }
}
```

Pull it back with `adb pull /sdcard/Android/data/<pkg>/files/dls-capture.jsonl ./fixtures/`.

Replay loader for `FakeDabSource`:

```kotlin
class JsonlScriptedSource(file: File) : RadioSource {
    private val events0 = file.useLines { it.map { line -> Json.decodeFromString<NowPlaying>(line) }.toList() }
    override val events = flow {
        val base = events0.first().timestampMs
        val wallStart = System.currentTimeMillis()
        for (e in events0) {
            val due = wallStart + (e.timestampMs - base)
            delay((due - System.currentTimeMillis()).coerceAtLeast(0))
            emit(e)
        }
    }
    override suspend fun start() = Unit
    override suspend fun stop()  = Unit
}
```

Bundle a few captures under `app/src/main/assets/fixtures/` (or `androidTest/assets/`) so unit tests and the emulator both reuse them.

---

## 4. Emulator USB-host limits (this matters)

**The Android Emulator does not pass host USB through to the guest.** There is no `-usb` passthrough flag and no `UsbHostController` in QEMU's Android image. The emulator's `UsbManager` will report an empty device list. This is a permanent limitation, not a bug to work around — confirmed by absence of any USB option in the emulator command-line reference (https://developer.android.com/studio/run/emulator-commandline) and by long-standing issuetracker requests (e.g., issuetracker.google.com/issues/37115787).

Practical alternatives, ranked:

| Option | Best for | Friction |
|---|---|---|
| **Emulator + `FakeDabSource`** | UI work, ViewModel/Flow tests, theming, layout iteration | None — instant, no hardware |
| **Phone + USB-OTG dongle on the desk** | Real protocol work without going to the car (assuming you can spare a second dongle or carry it inside) | Need an OTG cable + a phone that supplies enough bus power; many DAB dongles draw ≥ 200 mA |
| **WiFi ADB to the head unit in the car** | Final integration, real RF, real audio routing | Cold/hot weather, parking, WiFi reach |
| **Android Studio Device Streaming / cloud devices** | Not useful here — no USB passthrough either | — |

Recommendation: do 90% of the work on the emulator with `FakeDabSource` driven by a recorded JSONL fixture. Only deploy to the car for protocol- or audio-routing work.

---

## 5. In-car iteration workflow

Pre-flight (done once at the laptop, indoors, paired with phone hotspot):

```bash
HEADUNIT=192.168.1.42:5555
adb connect "$HEADUNIT"
```

Then a small `dev-deploy.sh` in the repo root:

```bash
#!/usr/bin/env bash
set -euo pipefail

HEADUNIT="${HEADUNIT:-192.168.1.42:5555}"
PKG="com.example.radiolyric"
ACT="$PKG/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

./gradlew :app:assembleDebug

adb -s "$HEADUNIT" install -r -t -d "$APK"
adb -s "$HEADUNIT" shell am force-stop "$PKG"
adb -s "$HEADUNIT" shell am start -n "$ACT" \
    --ez fromDevDeploy true
adb -s "$HEADUNIT" logcat -v time -s "RadioLyric:V" "AndroidRuntime:E"
```

Notes:

* `-r` reinstall keeping data, `-t` allows test APKs, `-d` allows downgrade — see `adb` *Install an app* / `pm install` flags (https://developer.android.com/tools/adb#pm).
* `am force-stop` followed by `am start -n pkg/.Activity` gives a clean restart (https://developer.android.com/tools/adb#am).
* Keep `adb logcat` streaming so you see crashes from the passenger seat.
* Optional Gradle wrapper task:
  ```kotlin
  // app/build.gradle.kts
  tasks.register<Exec>("deployCar") {
      dependsOn("assembleDebug")
      commandLine("bash", "../dev-deploy.sh")
  }
  ```

---

## 6. USB traffic logging when you do have hardware on the desk (pointer only)

When you can plug the dongle into a Linux laptop or Windows machine:

* **Linux — usbmon + Wireshark**:
  ```bash
  sudo modprobe usbmon
  sudo setfacl -m u:$USER:r /dev/usbmon*
  wireshark -k -i usbmon1   # pick the bus the dongle is on (lsusb -t)
  ```
  See kernel docs https://www.kernel.org/doc/Documentation/usb/usbmon.txt and Wireshark *USB capture setup* https://wiki.wireshark.org/CaptureSetup/USB.
* **Windows — USBPcap + Wireshark**: install [USBPcap](https://desowin.org/usbpcap/), then choose the USBPcap interface inside Wireshark.
* Filter for the dongle's VID:PID first (`lsusb`), then look at bulk/interrupt endpoints carrying DLS strings.
* For deeper RE, `usbreplay` (libusb) or `wireshark` → "Export PDUs" → custom dissector.

---

## 7. Test doubles for MediaSession / foreground audio service

Goal: let the UI/ViewModel layer run on the emulator (no real audio, no DAB) but still exercise the same code paths the production foreground service uses.

### 7.1 Hide `MediaSessionCompat` behind an interface

```kotlin
interface NowPlayingPublisher {
    fun publish(np: NowPlaying)
    fun release()
}

class MediaSessionPublisher(context: Context) : NowPlayingPublisher {
    private val session = MediaSessionCompat(context, "RadioLyric")
    override fun publish(np: NowPlaying) {
        session.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, np.artist.orEmpty())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  np.title.orEmpty())
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, np.dls.orEmpty())
            .build())
    }
    override fun release() = session.release()
}

class FakeNowPlayingPublisher : NowPlayingPublisher {
    val published = mutableListOf<NowPlaying>()
    override fun publish(np: NowPlaying) { published += np }
    override fun release() = Unit
}
```

### 7.2 Make the foreground service a thin shell

```kotlin
class RadioForegroundService : Service() {
    @Inject lateinit var source: RadioSource
    @Inject lateinit var publisher: NowPlayingPublisher
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startForeground(NOTI_ID, buildNotification())
        scope.launch { source.events.collect { publisher.publish(it) } }
        return START_STICKY
    }
    override fun onDestroy() { scope.cancel(); publisher.release() }
    override fun onBind(intent: Intent?) = null
}
```

In emulator/dev builds Hilt provides `FakeDabSource` + `FakeNowPlayingPublisher`; in unit tests you assert against `FakeNowPlayingPublisher.published`. For instrumentation tests of the service itself, prefer `androidx.test.core.app.ServiceTestRule` or Robolectric's `ShadowMediaSession` so you never need real audio focus.

### 7.3 Audio output

Keep `AudioTrack`/`ExoPlayer` behind another interface (`AudioRenderer`) with a `NoOpAudioRenderer` for emulator runs; otherwise the emulator will burn CPU on a silent codec or fail audio-focus requests.

---

## Recommended workflow summary

1. Day-to-day: emulator + `FakeDabSource` fed by JSONL fixtures — no car required.
2. Once: drive to the car, plug in, run the bundled JSONL logger for a few stations, `adb pull` the captures into `app/src/main/assets/fixtures/`. These are now your regression corpus.
3. Set up WiFi ADB to the head unit while you have the cable connected: `adb tcpip 5555` and (if possible) flip the OEM "ADB over network" toggle so it persists across reboots.
4. From the driveway, iterate via `dev-deploy.sh` (build → `adb install -r` → `am force-stop`/`am start` → tailing `logcat`). Use `scrcpy --tcpip=…` for screen mirroring without leaving the driver's seat.
5. For protocol RE, bring the dongle indoors once and capture with `usbmon`/USBPcap.

---

## Recommended next research (not covered here)

- [ ] Identify the exact USB VID:PID and chipset of the user's specific Duduauto-paired DAB dongle (often Si468x or Keystone T4B-based) before writing `DabProtocol`.
- [ ] Confirm whether Duduauto's firmware exposes root (`adb shell su`) — determines whether `persist.adb.tcp.port` is viable.
- [ ] Decide on a DI framework (Hilt vs Koin) for the source-swapping mechanism.
- [ ] Investigate Android Auto / `CarAppService` rules if the app is ever to be published — head-unit ROMs are sometimes Automotive OS rather than plain AOSP.

## Clarifying questions

1. Is the head unit actually Android **Automotive** OS, or stock-AOSP-with-car-launcher? It changes the service / media-session APIs you're allowed to use.
2. Do you already know the dongle's USB VID:PID and protocol family (RTL-SDR-style, Si468x, Keystone, proprietary)? If unknown, the first capture session needs to be a `lsusb -v` + a usbmon dump rather than a DLS logger.
3. Is rooting the Duduauto on the table? Persistence of `adb tcpip` and reading kernel logs are much easier with root.
4. Does the user have a spare DAB dongle (or a tolerant phone with USB-OTG + power) to run on the desk, or is the in-car unit the only one?

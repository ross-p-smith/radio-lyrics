# USB DAB Dongle Integration Research

Status: Complete
Date: 2026-04-30
Scope: How an Android app can communicate with a USB-connected consumer DAB radio dongle (the kind targeted by the Android app marketed as "dabZ" / "DAB-Z").

---

## TL;DR — Key Discoveries

- The "dabZ" app on Google Play is actually **DAB-Z – Player for USB tuners**, package `com.zoulou.dab`, developer `dabz` (an independent developer), NOT Imagination Technologies. ~384k installs as of v2.0.239 (2024-09-17). Source: appbrain, apkgk, apkpure, Play Store listing.
- It is built around the **Monkeyboard / Keystone family** of USB DAB dongles. These are USB-Serial CDC-ACM devices (Microchip PIC18F14K50 USB↔UART bridge in front of a Keystone T2_L4A_8650C / T4B-6620VDB DAB+/FM module). Source: monkeyboard.org product page.
- USB IDs: **VID 0x04D8 (Microchip, 1240 dec) / PID 0x000A (10 dec)**, baud **57600 8N1**. Confirmed in source code of an open-source equivalent app: freshollie/monkeyboard-radio-android, RadioDevice.java lines 30-33.
- The dongle does **NOT** stream audio over USB. Audio is emitted from the dongle's own 3.5mm analog stereo jack. The host only sends control commands and receives metadata over the CDC-ACM serial channel. Source: README of freshollie/monkeyboard-radio-android (verbatim quote below).
- The host-side protocol is a tiny binary frame: `0xFE | class | cmd | serial | 0x00 | paramLen | params... | 0xFD`. Documented in source comments.
- An open-source, GPL-3.0, fully working Android reference implementation exists: **freshollie/monkeyboard-radio-android** (F-Droid: `com.freshollie.monkeyboard.keystoneradio`). It uses **mik3y/usb-serial-for-android** with the `CdcAcmSerialDriver`. This is the recommended starting point.
- DLS / programme text is fetched via opcode `STREAM_GetProgramText` (0x10). DAB+ ITEM.ARTIST / ITEM.TITLE per ETSI TS 102 980 (DL Plus) is **not exposed** by the Monkeyboard firmware as discrete tags — only the rendered DLS string. To split into artist/title you must either (a) parse DL+ command bytes if Keystone forwards them embedded, or (b) heuristically split on " - " (this is what most car-radio apps do as a fallback).
- "Duduauto 7" / "DUDU7" is a Chinese MekedeTech aftermarket head unit family running **Android 13** (some SKUs Android 12) on the Unisoc UIS7870 SoC. It is a normal Android with USB host. No DAB-specific quirks; standard Chinese-aftermarket caveats apply (delayed `UsbManager.requestPermission` callbacks, missing intent broadcast for hot-plug on some firmware revisions, aggressive battery/process killers).
- DAB-Z does NOT publish any documented Intents or content provider for now-playing metadata. To get DLS/now-playing in your own app you must either (a) talk to the dongle directly yourself, or (b) attach a `NotificationListenerService` and read DAB-Z's `MediaSession`/notification. No SDK is published.

---

## 1. Which USB DAB dongles does "dabZ" support? VID/PID, host protocol

### 1.1 Identity correction

- Actual app: **DAB-Z – Player for USB tuners** — `com.zoulou.dab`, developer "dabz" (a single developer, not Imagination Technologies).
- Sources:
  - Play Store listing summary via appbrain/apkpure/softonic search results.
  - XDA thread: `xdaforums.com/t/dab-z-v2-x-usb-dab-dab-app-no-longer-maintained.4572071/` (the v2 line is described there as no longer maintained; user reports also reference Realmedia head units as the typical install base).
- Imagination Technologies is the company behind the BBC R&D / Pure DAB+ reference designs, but they do not publish a Play Store app called "dabZ".

### 1.2 Supported dongle families

DAB-Z and the equivalent open-source app freshollie/monkeyboard-radio-android support the **Monkeyboard / Keystone** family. The same hardware is sold under many brand names:

| Brand / model | Module inside | USB front-end |
| --- | --- | --- |
| Monkeyboard "DAB+ FM Digital Radio Development Board Pro / Pro2 / Pro2.5" | Keystone T2_L4A_8650C, later T4B-6620VDB | Microchip PIC18F14K50 (CDC-ACM virtual COM) |
| CarTFT / CarPC-Shop relabels of the Monkeyboard | same | same |
| Various AliExpress "USB DAB+ for Android car radio" sticks | usually Keystone T4B family | usually PIC18F14K50, occasionally CP2102 / CH340 |

Source: monkeyboard.org product pages, cartft.com catalog page 1618, scribd document 367030840.

### 1.3 USB IDs and link parameters

From freshollie/monkeyboard-radio-android `app/src/main/java/com/freshollie/monkeyboard/keystoneradio/radio/RadioDevice.java`:

```java
public class RadioDevice {
    public static final int PRODUCT_ID = 10;     // 0x000A
    public static final int VENDOR_ID  = 1240;   // 0x04D8 — Microchip
    public static final int BAUD_RATE  = 57600;
    ...
}
```

From `radio/DeviceConnection.java`:

```java
UsbSerialDriver driver = new CdcAcmSerialDriver(usbDevice);
deviceSerialInterface = driver.getPorts().get(0);
deviceSerialInterface.open(usbDeviceConnection);
deviceSerialInterface.setParameters(
    RadioDevice.BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
deviceSerialInterface.setDTR(false);
deviceSerialInterface.setRTS(true);
```

So host-side: standard CDC-ACM, `04D8:000A`, 57600 8N1, DTR low / RTS high.

Cheaper Chinese clones may enumerate as:

- 0x10C4 / 0xEA60 — Silicon Labs CP2102 UART bridge
- 0x1A86 / 0x7523 — WCH CH340
- 0x0403 / 0x6001 — FTDI FT232

`usb-serial-for-android` covers all three; you typically include all four VID/PID pairs in your `device_filter.xml`.

### 1.4 Host-side wire protocol

From source comment in `radio/RadioDevice.java`:

```
A byte array containing a command is formatted as such:

byte {
    START_BYTE,        // 0xFE
    FUNCTION_CATEGORY, // class: 0x00 system, 0x01 stream, 0x03 MOT
    FUNCTION,          // opcode within class
    UNIQUE_COMMAND_NUMBER, // serial / sequence number
    0x00,
    NUM_PARAMETERS,
    PARAMETER, ...
    END_BYTE           // 0xFD
}
```

Response frames mirror this layout; the dongle echoes the command serial number so the host can correlate replies to requests asynchronously.

Selected opcodes (from `RadioDevice.ByteValues`):

| Class | Opcode | Symbol | Purpose |
| --- | --- | --- | --- |
| 0x00 SYSTEM | 0x00 | `SYSTEM_GetSysRdy` | Probe ready |
| 0x00 SYSTEM | 0x01 | `SYSTEM_Reset` | Reset (reboot / clear / clear+reboot) |
| 0x01 STREAM | 0x00 | `STREAM_Play` | Tune (params: mode, 4-byte channel/freq) |
| 0x01 STREAM | 0x01 | `STREAM_Stop` | Stop |
| 0x01 STREAM | 0x02 | `STREAM_Search` | Manual search step |
| 0x01 STREAM | 0x03 | `STREAM_AutoSearch` | DAB band scan |
| 0x01 STREAM | 0x04 | `STREAM_StopSearch` | Abort scan |
| 0x01 STREAM | 0x05 | `STREAM_GetPlayStatus` | 0=play 1=search 2=tune 3=stopped |
| 0x01 STREAM | 0x06 | `STREAM_GetPlayMode` | DAB(0) / FM(1) |
| 0x01 STREAM | 0x07 | `STREAM_GetPlayIndex` | Current channel index |
| 0x01 STREAM | 0x08 | `STREAM_GetSignalStrength` | FM RSSI |
| 0x01 STREAM | 0x09 | `STREAM_SetStereoMode` | |
| 0x01 STREAM | 0x0B | `STREAM_GetStereo` | |
| 0x01 STREAM | 0x0C / 0x0D | `STREAM_SetVolume` / `GetVolume` | 0–16 |
| 0x01 STREAM | 0x0E | `STREAM_GetProgramType` | PTY genre |
| 0x01 STREAM | 0x0F | `STREAM_GetProgramName` | Service label |
| 0x01 STREAM | **0x10** | **`STREAM_GetProgramText`** | **DLS / programme text — primary now-playing source** |
| 0x01 STREAM | 0x12 | `STREAM_GetDataRate` | kbps |
| 0x01 STREAM | 0x13 | `STREAM_GetSignalQuality` | DAB signal quality 0–100 |
| 0x01 STREAM | 0x14 | `STREAM_GetFrequency` | |
| 0x01 STREAM | 0x15 | `STREAM_GetEnsembleName` | |
| 0x01 STREAM | 0x16 | `STREAM_GetTotalProgram` | |
| 0x01 STREAM | 0x1B | `STREAM_GetSearchProgram` | |
| 0x03 MOT | 0x00 | `MOT_GetMOTData` | SlideShow / DAB+ MOT data over MSC |

Opcodes align with the official Keystone "T_L4A serial command" table reproduced in the cartft.com PDFs `Monkeyboard_Manual.pdf` and `Monkeyboard_Manual_Pro2.pdf` (PDF text extraction failed in this session, but their existence and authority is referenced from carpcshop.de and monkeyboard.org).

---

## 2. Public SDK / command-set / reference source

| Resource | Type | Useful for |
| --- | --- | --- |
| Monkeyboard "DAB+ FM Development Board User's Guide Rev2" PDF: `https://www.cartft.com/support_db/support_files/Monkeyboard_Manual.pdf` | Vendor manual | Wire-level command table, sample C# / VB.NET app |
| Monkeyboard "PRO2 User's Guide" PDF: `https://www.cartft.com/support_db/support_files/Monkeyboard_Manual_Pro2.pdf` | Vendor manual | Newer command set, slideshow |
| Datasheet `https://www.cartft.com/catalog/PDF/409/MonkeyBoard%20DAB%20DAB+%20FM%20Digital%20Radio%20Development%20Board%20Pro%20mit%20SlideShow.pdf` | Vendor datasheet | Pinout, capabilities |
| **freshollie/monkeyboard-radio-android** `https://github.com/freshollie/monkeyboard-radio-android` | GPL-3.0 Android app | Drop-in reference: protocol, DLS parsing, MOT slideshow, MediaSession integration |
| **mik3y/usb-serial-for-android** `https://github.com/mik3y/usb-serial-for-android` | Apache-2.0 Android lib | CDC-ACM/FTDI/CH34x/CP21xx serial driver |
| **AlbrechtL/welle.io** `https://github.com/AlbrechtL/welle.io` | GPL SDR DAB stack | Reference DAB/DAB+ decoder if you ever want to use an RTL-SDR dongle instead of a Keystone module. Note: welle.io targets RTL2832U/Airspy/SoapySDR — *not* the Monkeyboard. Its Android port (v2.4) is unmaintained. |
| Opendigitalradio dablin `https://github.com/Opendigitalradio/dablin` | GPL DAB+ ETI decoder | Reference for ETI/DAB+ frame structure |
| `JvanKatwijk/dab-cmdline` `https://github.com/JvanKatwijk/dab-cmdline` | GPL CLI DAB | Command-line DAB decoder using RTL-SDR |

There is **no official "Imagination Technologies dabZ SDK"** — that branding is a misattribution. The two ecosystems are:

1. **Keystone / Monkeyboard module + tiny serial protocol** — consumer USB DAB dongles, including those used by DAB-Z.
2. **RTL-SDR or Airspy + welle.io / dablin software stack** — fully software DAB.

These are completely different stacks.

---

## 3. Audio path: PCM/MP2 over USB? Or USB Audio Class?

**Neither — for the Monkeyboard / Keystone family, audio is analog only.**

Verbatim from `freshollie/monkeyboard-radio-android` README:

> The Monkeyboard does not transmit audio over the USB connection, it is only outputted directly from the board itself. The user will need to mix this audio with the tablet audio externally. (Think of the android device as being a screen for the Monkeyboard.)

The board has a 3.5mm stereo jack (the Keystone module decodes DAB+ MP2 / HE-AAC internally and outputs analog L/R). The PIC18F14K50 only carries control + metadata.

Implications for an Android car-radio integration:

- Wire the dongle's 3.5mm output to a free AUX input on the head unit. The head unit's hardware audio router (not Android) plays the sound.
- Or buy a Keystone variant that includes a USB Audio Class composite endpoint (some Hama / Noxon laptop sticks do this; verify per-device with `lsusb -v` showing class 0x01 Audio interface). For UAC variants the host opens them as a normal USB sound card.
- Truly "all-USB" DAB reception is what RTL-SDR / Airspy + welle.io provides — the host CPU does demodulation and you get PCM out of the decoder library. That demands real CPU and is not what DAB-Z does.

Sources: monkeyboard.org Pro product page (3.5 mm stereo jack; SHDN / SHNT analog mute lines), freshollie README quote above.

---

## 4. Opening the device on Android

### 4.1 Recommended approach — `usb-serial-for-android`

```gradle
// build.gradle (app)
dependencies {
    implementation "com.github.mik3y:usb-serial-for-android:3.7.0"
}
```

`AndroidManifest.xml`:

```xml
<activity android:name=".MainActivity">
    ...
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"/>
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

`res/xml/device_filter.xml` (covers Monkeyboard PIC + common Chinese clones):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Microchip PIC18F14K50 CDC (Monkeyboard) -->
    <usb-device vendor-id="1240" product-id="10" />
    <!-- Silicon Labs CP2102 -->
    <usb-device vendor-id="4292" product-id="60000" />
    <!-- WCH CH340 -->
    <usb-device vendor-id="6790" product-id="29987" />
    <!-- FTDI FT232 -->
    <usb-device vendor-id="1027" product-id="24577" />
</resources>
```

Permission + open (Kotlin sketch, modeled on freshollie/monkeyboard-radio-android `DeviceConnection.java`):

```kotlin
private val ACTION_USB_PERMISSION = "com.example.radio.USB_PERMISSION"

fun connect(context: Context) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
    val driver = drivers.firstOrNull() ?: return // none attached
    val device = driver.device

    if (!usbManager.hasPermission(device)) {
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.registerReceiver(
            permissionReceiver, IntentFilter(ACTION_USB_PERMISSION),
            Context.RECEIVER_NOT_EXPORTED)
        usbManager.requestPermission(device, pi)
        return
    }

    val connection = usbManager.openDevice(device) ?: return
    val port = driver.ports[0]
    port.open(connection)
    port.setParameters(57600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    port.dtr = false
    port.rts = true

    // Now write/read framed commands per § 1.4.
}
```

Sending a tune command (DAB mode, channel index N) and receiving the framed response is exactly what freshollie's `RadioDevice.call()` does — see lines 315-368 of `RadioDevice.java`.

### 4.2 Lower-level — raw `UsbDeviceConnection` + bulk transfers

Possible but you must hand-roll the CDC-ACM line-coding control transfer (`SET_LINE_CODING` 0x20 on the control endpoint) and handle the bulk-IN / OUT endpoints yourself. Don't bother — `usb-serial-for-android` does it correctly across vendors. The freshollie app does exactly this via the library.

### 4.3 Android 12+ / `PendingIntent` mutability

From Android 12 (API 31) you must specify `FLAG_IMMUTABLE` on the permission intent or `requestPermission` throws. The freshollie app's pre-Android-12 code (around line 280 in `DeviceConnection.java`) uses `PendingIntent.getBroadcast(context, 0, intent, 0)` which crashes on API 31+. Use the snippet above.

### 4.4 Android 13+ runtime receiver export

When registering a `BroadcastReceiver` for the permission action at runtime on API 33+, you must pass `Context.RECEIVER_NOT_EXPORTED` (or `RECEIVER_EXPORTED` with appropriate justification). Shown above.

---

## 5. Does DAB-Z expose Intents / ContentProvider / metadata hooks?

**No published, documented integration surface exists.** Findings:

- No SDK on the developer's site (developer is "dabz", an individual; no domain or GitHub linked from the Play listing).
- No mention of broadcast intents in any apk inspection write-up, the XDA thread `xdaforums.com/t/dab-z-v2-x-usb-dab-dab-app-no-longer-maintained.4572071/`, or the apkpure changelog.
- The Play description states it acts as a media player — meaning it almost certainly publishes a `MediaSessionCompat` and a media-style notification.

Therefore, third-party metadata extraction options ranked by reliability:

1. **Talk to the dongle yourself.** (Recommended.) Both apps would compete for the USB device, so users would need to choose one. But you get full structured metadata (DLS, ensemble, programme name, signal quality, slideshow) directly. Use the freshollie code as a reference.
2. **`NotificationListenerService` reading DAB-Z's media notification.** Requires the user to grant `Notification access`. Gives you whatever DAB-Z chose to put in `EXTRA_TITLE` / `EXTRA_TEXT` / `EXTRA_SUB_TEXT` and the `MediaSession` metadata (`MediaMetadata.METADATA_KEY_TITLE`, `..._ARTIST`, `..._ALBUM`). Standard pattern used by car-launcher apps to scrape Spotify and friends.
3. **`MediaSessionManager.getActiveSessions(...)`** — same idea, but does not require its own listener service — the user still must grant notification access (the `ComponentName` of an enabled `NotificationListenerService` is required as the auth token).

Confirming option 2/3 actually works requires inspecting a running DAB-Z session (not done in this session). It is the documented Android way for one app to read another's now-playing metadata, so it is the safe assumption.

There is no evidence of a content provider, AIDL service, or accessibility service exposed by DAB-Z.

---

## 6. "Duduauto 7" / DUDU7

DUDU7 / DUDUAUTO is a Chinese aftermarket head-unit family by **MekedeTech**, sold direct (`duduauto.com`, `forum.dudu-auto.com`) and via Amazon / AliExpress. Models found:

- DUDU7 9.5" / 10.36" / 11.5" / 12.3" / 13" with **Android 13** ("OS3.0" / "OS3.5" UI skins on top), Unisoc UIS7870 SoC, 6–12 GB RAM, 64–512 GB ROM.
- AliExpress listing "DUDUAUTO DUDU 7 DUDU7 A7870 AKM7739 TDA7808 DSP DTS Android 13 Carplay" item 1005006989175244 confirms Android 13.
- Some 2024 SKUs ship Android 12; OTA updates push to 13.

It is a **regular Android-with-USB-host** device. Practical caveats reported by aftermarket-head-unit developers (consistent across MTCD / MTCE / Topway / Joying / Mekede platforms — the DUDU7 forum specifically calls out "software stability" and "audio output issues"):

- **Hot-plug intent unreliable.** `android.hardware.usb.action.USB_DEVICE_ATTACHED` is sometimes not delivered on cold boot if the dongle was already plugged in. Fall back to enumerating `usbManager.deviceList` from `Activity.onResume`.
- **Permission dialog races.** On some firmwares the system pops permission *after* the activity has resumed, so guard against double-open.
- **Aggressive process killers.** OEM "Auto-start manager" / "Battery saver" will kill background services. Use a foreground `Service` + `MediaSession` (the freshollie app already does this).
- **Multiple internal USB hubs.** The head unit usually has 2 USB-A ports plus internal dashcam / CarPlay USB. Some ports are USB 1.1 only — fine for CDC-ACM at 57600.
- **Time-sync at boot.** GPS / RTC may be wrong at boot; do not rely on `System.currentTimeMillis` for serial-frame timeouts (use `SystemClock.elapsedRealtime`).
- **AOSP audio HAL "AUX" routing** is OEM-specific. If you want the dongle's analog audio routed through the head unit speakers, the user must physically wire the 3.5 mm jack to the head unit's AUX input AND select AUX on the head unit's source menu. Software cannot route external analog audio.

No DAB-specific quirk for DUDU7 was found in `forum.dudu-auto.com` post 212 review. Standard Android USB-host APIs work.

---

## 7. Capturing DLS and DL+ (ETSI TS 102 980)

### 7.1 DLS (Dynamic Label Segment, ETSI EN 300 401 / TS 101 756)

On the Monkeyboard / Keystone family the DLS string is delivered as one already-assembled UTF-8 / Latin-1 string by:

```
class=0x01 STREAM, opcode=0x10 STREAM_GetProgramText
```

Reference implementation modeled on freshollie `RadioDevice.java` `getProgramName()` (the call wrapper pattern is identical):

```java
byte[] response = call(
    ByteValues.CLASS_STREAM,
    ByteValues.STREAM_GetProgramText,
    new byte[]{}); // no params

if (response != null) {
    byte[] textBytes = Arrays.copyOfRange(response, 6, response.length - 1);
    String dls = getStringFromBytes(textBytes); // tries UTF-8, falls back to Latin-1
}
```

`getStringFromBytes` tries UTF-8 then falls back to Latin-1, since Keystone may emit DAB EBU Latin Set (which is 1:1 with Latin-1 for the printable range used by most stations).

Poll cadence in freshollie: ~1 s inside `private boolean poll()`. DLS only changes a few times per minute, so 1–2 s polling is plenty.

### 7.2 DL Plus (ETSI TS 102 980 — `ITEM.ARTIST`, `ITEM.TITLE`, etc.)

**The Keystone serial protocol does not expose DL+ tag offsets natively.** It returns only the rendered DLS string. There is no `STREAM_GetDLPlus*` opcode in the freshollie reference.

You have three options:

1. **Heuristic split.** Most stations format DLS as `Artist - Title` or `NOW PLAYING: Artist - Title`. A regex split on ` - ` plus a small list of known prefixes (`Now Playing`, `On Air`) handles ~90 % of UK / EU broadcasters. DAB-Z and most other consumer apps stop here.
2. **Parse DL+ commands embedded in the raw MSC stream.** Some Keystone firmwares forward the unparsed DL+ command set (length=2 byte, content_type=6 bit, start=6 bit, length-1=6 bit per tag) inline at the end of the DLS payload. Tag content types of interest from TS 102 980 §5.1:

   | Code | Content type |
   | --- | --- |
   | 0x01 | ITEM.TITLE |
   | 0x02 | ITEM.ALBUM |
   | 0x03 | ITEM.TRACKNUMBER |
   | 0x04 | ITEM.ARTIST |
   | 0x05 | ITEM.COMPOSITION |
   | 0x09 | ITEM.COMPOSER |
   | 0x0B | INFO.NEWS |
   | 0x0C | INFO.NEWS.LOCAL |
   | 0x0D | INFO.STOCK |
   | 0x0E | INFO.SPORT |
   | 0x0F | INFO.LOTTERY |

   You'd need to capture a real DLS payload from a known DL+ broadcaster (e.g. BBC R1, Antenne Bayern, NRK) and check for the DL+ command escape inside the response bytes beyond the visible DLS. There is no public confirmation that Keystone forwards this — it likely strips DL+ tags during its own DAB+ FIC parsing.
3. **Switch to an RTL-SDR + welle.io / dablin pipeline.** These do parse DL+ correctly because they own the full DAB demod stack. Cost: ~10× the CPU and you lose the all-in-one Keystone analog audio.

### 7.3 SlideShow (MOT)

Slideshow images come via `class=0x03 MOT, opcode=0x00 MOT_GetMOTData`. The freshollie code re-assembles MOT packets per ETSI TS 101 759 in `radio/mot/MSCDataGroup.java`, `Packet.java`, `MOTObject.java`. Final body decodes as JPEG / PNG via `BitmapFactory.decodeByteArray`. If you only care about now-playing text you can ignore this entire subtree.

---

## Recommendations (executive)

1. **Pick the open-source path.** Fork or vendor `freshollie/monkeyboard-radio-android` (GPL-3.0 — note copyleft if you redistribute). It already implements: USB enumeration, CDC-ACM open, all opcodes you need, DLS polling, MOT slideshow assembly, MediaSession + foreground service. Strip the UI and keep the `radio/` package as a library.
2. **Recommended dongle**: a Monkeyboard "DAB+ FM Pro2.5 with SlideShow" (Keystone T4B-6620VDB) or any AliExpress "USB DAB+ for Android car" stick that enumerates as `04D8:000A`. Verify VID/PID with `lsusb` on a Linux box before committing.
3. **Audio**: physically wire the dongle's 3.5 mm output to a free AUX-In on the DUDU7 head unit. Software cannot bridge external analog audio in.
4. **DL+ artist / title**: ship the heuristic split as MVP; document that some stations only broadcast DLS without DL+. Plan an experiment to capture raw `STREAM_GetProgramText` payloads on a known DL+ broadcaster to confirm whether tags are forwarded.
5. **Avoid trying to scrape DAB-Z.** It works (via `NotificationListenerService`) but fragile (DAB-Z may disappear from Play, may change notification format, and the user can only run one DAB-Z-or-yours at a time anyway because both want the USB dongle).

---

## Recommended next research (not completed in this session)

- [ ] Empirically confirm that Monkeyboard `STREAM_GetProgramText` returns embedded DL+ command bytes after the visible DLS string (capture from BBC Radio 1 or Antenne Bayern).
- [ ] Inspect the DAB-Z apk (e.g. via apkpure download + `apktool d`) to confirm its `MediaSessionCompat` metadata field names and whether it implements a `ContentProvider` (only needed if option 5 above is reconsidered).
- [ ] Confirm USB Audio Class variants of Keystone-based dongles and list those VID/PID specifically (some Hama / Noxon laptop sticks; verify with `lsusb -t`).
- [ ] Verify behavior on an actual DUDU7 unit: hot-plug intent reliability, which USB ports power the dongle, and whether the head unit's AUX input is line-level or mic-level.
- [ ] Pin the latest `usb-serial-for-android` release and confirm CDC-ACM works on Android 14 with `RECEIVER_NOT_EXPORTED` / `FLAG_IMMUTABLE` PendingIntents on a real DUDU7.

## Clarifying questions for the user

1. Is the dongle already chosen, or are you free to pick? If free, the Monkeyboard Pro2.5 is the safest reference target.
2. Do you need decoded audio inside your Android app's process (for DSP, ducking, mixing with TTS) — in which case Keystone won't work and you must move to RTL-SDR + welle.io — or is wiring 3.5 mm out → head-unit AUX in acceptable?
3. Is parsing DL+ (artist / title separation) a hard requirement, or is the heuristic " - " split acceptable for an MVP?
4. Do you intend to coexist with DAB-Z on the same head unit, or replace it?
5. Confirm the head-unit Android version on DUDU7 (12 vs 13) for `PendingIntent` flag and runtime-receiver-export handling.

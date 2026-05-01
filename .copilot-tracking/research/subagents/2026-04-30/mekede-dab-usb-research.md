# Mekede DAB+ USB Dongle Research

## Status

Complete (high confidence on chipset family, protocol, app ecosystem; medium confidence on the exact silicon part number behind the "wRadio C100" / Raon Tech branding — see clarifying questions).

## Research Topics

1. Hardware identity (chipset, USB VID/PID, transport)
2. Bundled software (origin of the "DAB+" / "MTC DAB" APK)
3. dabZ / DAB-Z compatibility
4. Public reverse-engineering of the protocol
5. Audio path (USB Audio Class vs encoded-frames-over-control-channel)
6. Android USB-host implications on head units
7. DLS / DL+ metadata exposure

## Executive Summary

The Mekede "DAB+ USB Adapter" sold for Android car head units (Mekede, Joying, Erisin, Hizpo, Atoto, Junsun, Dudu7, Eonon, etc. all rebrand the same hardware family) is a **custom-class USB device, not a USB-serial bridge and not a USB Audio Class device**. It enumerates with **VID `0x16C0` / PID `0x05DC`** (a shared OBDEV/V-USB vendor/product ID range — the same one used by USBasp programmers — which the Chinese DAB-stick OEMs reuse instead of paying for a real USB-IF assignment). The radio silicon is most commonly referred to in the Android-app community as the **"wRadio C100"** / Raon Tech RTV-family DAB/T-DMB tuner-demodulator. Communication is via two USB bulk endpoints (`OUT 0x02`, `IN 0x82`) using a small register-read/register-write command protocol with paged registers; the host pulls **raw FIC and MSC sub-channel bytes** from the chip, then demultiplexes the DAB ensemble, decodes DAB / DAB+ (MP2 or AAC-LC) and DLS/DL+ in **software on the Android side**. There is no USB Audio Class endpoint and no PCM stream from the dongle.

Because the protocol is a register-poke interface with no CDC-ACM layer, you talk to it directly with `android.hardware.usb.UsbManager` + `UsbDeviceConnection.bulkTransfer(...)`. There is a fully open-source reference implementation: **`hradio/omri-usb`** on GitHub (LGPL-2.1, by IRT GmbH / HRADIO). DAB-Z (`com.zoulou.dab`) ships this same OMRI library and is **confirmed working with the Mekede stick** — XDA user `Lacki12` reports running DAB-Z 2.0.x on a "mekede m700 s" head unit successfully (one minor 12D-channel tuning bug). DABdream (`com.thf.dabplayer`) by `TorstenH` is a second compatible app and explicitly lists "wRadio C100 chip — VID 16c0 / PID 05dc" as supported.

The "DAB+" / "DAB-Z" / "MTC DAB" APK pre-installed by Mekede / Joying / Erisin / Hizpo is the *legacy chipset-vendor-supplied* player that wraps a closed proprietary middleware. The fktg-media.de/IRT writeup explicitly states: *"USB DAB receivers only come with DAB player software provided by the chipset manufacturer. These implementations are not being developed further and lack support for many of the DAB+ features and services."* The same APK gets re-skinned across all the Chinese head-unit brands.

**Recommended integration approach for the radio-lyric custom app:** depend on (or vendor) the open-source `hradio/omri-usb` AAR. It already covers USB enumeration, the register protocol, FIC/MSC parsing, DAB/DAB+ audio decoding, and DLS/DL+ extraction. Listen to the OMRI `RadioServiceListener` callbacks for DLS / DL+ tag events (`ITEM.ARTIST`, `ITEM.TITLE`) and feed those into the lyric-lookup pipeline. Avoid trying to reverse the proprietary Mekede APK — you would be reimplementing exactly what `omri-usb` already provides under a permissive license.

## Detailed Findings

### 1. Hardware identity

- **USB enumeration:** `Vendor ID = 0x16C0`, `Product ID = 0x05DC`. This is OBDEV/V-USB shared space (the AVR/OBDEV "free" PID pool); also used by USBasp. Confirmed two ways:
  - `omri-usb` source: `omriusb/src/main/java/org/omri/radio/impl/RadioImpl.java` line ~175 hard-codes `Pair.create(0x16C0, 0x05DC)` and constructs a `RaonTunerInput` for matching devices. `omriusb/src/main/cpp/platformspecific/android/native-lib.cpp` line ~141 contains `if(vendId == 0x16C0 && prodId == 0x05DC) { m_dabInputs.push_back(... new RaonTunerInput(...)); }`.
  - DABdream's XDA support thread: *"Adapters based on the wRadio C100 chip — The devices occur with the following IDs on Android: Vendor ID: 16c0 Product ID: 05dc"* — <https://xdaforums.com/t/dabdream-dab-dab-player-for-usb-adapters.4638309/>.
- **Chipset family:** Marketed as **wRadio C100** in the Android DAB-app community; the omri-usb code names it `RaonTunerInput` and references RTV-prefixed registers (`rtvRFInitilize`, `RTV_DAB_OFDM_LOCK_MASK`, etc.), strongly indicating a **Raon Tech RTV-series DAB/T-DMB SoC** (Raon Tech is a Korean fabless vendor whose RTV12xx / RTV13xx chips were widely used in early DMB phones; "wRadio C100" looks like a re-badge for the auto-OEM market). Best-guess silicon: Raon Tech RTV-series single-chip DAB/T-DMB receiver with integrated baseband demodulator. **Confidence: medium-high** that it is Raon-derived; **medium** on the exact die.
- **Other VID/PID combos that the same library also recognizes** (so other head-unit OEMs may ship them; helpful if user's box does not enumerate as 16C0/05DC):
  - `0x1D19 / 0x110D` — Dexatek DK DAB stick (mass-market consumer DAB stick)
  - `0x0FD9 / 0x004C` — Elgato-branded DAB stick
  - `0x0416 / 0xB003` — WinChipHead-style device (interface 1)
  - From `omri-usb/.../jusbdevice.cpp` line ~73.
- **Transport:** **NOT** USB-CDC-ACM, **NOT** CH340/FT232/CP210x. It is a custom-class device with bulk endpoints `OUT 0x02` and `IN 0x82` (`raontunerinput.h`). Commands are short byte arrays like `{0x21, 0x00, 0x00, 0x02, reg, val}` (write) and `{0x22, 0x00, 0x01, 0x00, reg}` (read), with paged register banks (`REGISTER_PAGE_RF`, `REGISTER_PAGE_FIC`, `REGISTER_PAGE_MSC0`, etc.). FIC and MSC sub-channel raw bytes are then bulk-pulled from the chip; everything above that (DAB ensemble parsing, AAC/MP2 decode, PAD/DLS) is on the host.

### 2. Bundled software ("DAB+" / "DAB-Z" / "MTC DAB" APK)

- The APK that Mekede / Joying / Erisin / Hizpo / Eonon / Atoto / Junsun pre-install is the **chipset-vendor reference player** wrapping a proprietary closed middleware. Same APK (or near-identical re-skins) appears across all of these brands — confirmed by XDA user `HMT06` who notes the bundled "dab+" app on Realmedia Germany head units looks exactly like the DAB-Z 2.0 UI and behaves like the Mekede one.
- No published vendor SDK is available externally. The chipset vendor (Raon Tech / wRadio) does not publish a public Android SDK; the APKs are distributed only through the head-unit OEMs' firmware bundles.
- Mekede's official product page: `https://www.mekede.com/bc12/1198.html`. The product is also resold on AliExpress (e.g. listings 1005004832133665, 1005007061470380) and forum.dudu-auto.com calls it the "official Mekede USB adapter".

### 3. dabZ / DAB-Z compatibility

- **Yes — confirmed compatible.** DAB-Z (`com.zoulou.dab` by realzoulou, on Google Play) statically links the `omri-usb` AAR which targets exactly this 16C0/05DC chipset. Quote from DAB-Z's lead developer published on fktg-media.de (IRT, May 2020): *"Using OMRI improved the audio quality, signal reception and stability... It took only hours to create a sample app based on OMRI until the first audio was audible on the loudspeakers."*
- Direct user confirmation on the Mekede device: XDA thread `dab-z-v2-x-usb-dab-dab-app`, post #12 — user `Lacki12` writes *"Hi, I have mekede m700 s, I'm trying the dab z version, version 2.0 209..."* (functional, with one channel-12D tuning bug at the time).
- Protocol DAB-Z speaks to the device: the Raon register protocol described in section 1 above.

### 4. Public reverse-engineering / OSS implementations

- **`hradio/omri-usb`** (GitHub, IRT GmbH, LGPL-2.1) — the canonical open implementation. <https://github.com/hradio/omri-usb>. Contains:
  - `RaonTunerInput` C++ class — the reverse-engineered Raon RTV register protocol (over 1300 lines: power-up, RF init, frequency tables for DAB Band III, FIC reader, MSC reader, signal-quality reporting).
  - Full DAB stack: FIC parser, MSC sub-channel demux, DAB / DAB+ audio decoders, DLS / DL+ decoder, enhanced SLS (slideshow) decoder.
  - Java / Android JNI wrapper exposing the OMRI API (`org.omri.radio.*`).
- **`hradio/OMRI`** — the API definition (ETSI TS 103 632 V1.1.1 inspired).
- DABdream (closed-source but free, by `TorstenH`, package `com.thf.dabplayer`) is the most actively maintained alternative app and supports a wider device list including the wRadio C100; it is *not* OSS but is good as a reference for "what works".
- DAB-Z source is closed but its middleware is `omri-usb` (since v1.7.78 onwards, default since Jan 2020).
- Adjacent OSS projects (do **not** target this chipset, but useful for DAB stack reference): `welle.io` (PC, RTL-SDR), `dablin` (Linux EDI/ETI player), `monkeyboard-dab-pi` / `node-dab` / `dab-cmdline` (Monkeyboard / Keystone T3/T4 modules — those use a *serial* protocol and are unrelated to the Mekede chipset).

### 5. Audio path

- **No USB Audio Class endpoint, no PCM from the dongle.** The dongle delivers raw DAB MSC sub-channel bytes over bulk-IN, and the host application is responsible for software AAC-LC + SBR + PS (DAB+) or MP2 (legacy DAB) decoding. The omri-usb C++ stack contains the AAC/MP2 decoders.
- Implication: CPU cost on the head unit is non-zero (HE-AAC v2 software decode), but well within reach of any modern Android automotive SoC.

### 6. Android USB-host implications

- App needs:
  - `<uses-feature android:name="android.hardware.usb.host" />` in `AndroidManifest.xml`.
  - A `<receiver>` (or `<activity>` with `android.hardware.usb.action.USB_DEVICE_ATTACHED`) intent-filter pointing at an XML resource that declares `<usb-device vendor-id="5824" product-id="1500" />` (`0x16C0` = 5824 decimal, `0x05DC` = 1500 decimal). This auto-grants the permission prompt when the dongle is plugged in.
  - At runtime: `UsbManager.requestPermission(...)` for the device, then `openDevice()` → `claimInterface(0)` → `bulkTransfer()`.
- Known head-unit firmware quirks (from XDA / dudu-auto.com / android-hilfe.de threads):
  - Some Chinese head-unit firmwares (notably some AC8259-CPU YT5760-family units, some UIS7870 units) fail Android CTS and break USB-host permission persistence; the user has to re-grant USB permission on every boot. DAB-Z release notes explicitly call this out: *"Fix: On some devices app gets stuck waiting for USB permission"*.
  - On Android ≥ 12 some head units block USB-host access for non-system apps; workaround is sideloading as system app or using the OEM's pre-shipped `DAB+` APK signature.
  - LED interior lights and cheap USB chargers cause severe RF interference (multiple dudu-auto.com threads) — not a software issue but worth flagging in the app's UX/troubleshooter.

### 7. DLS / DL+ metadata exposure

- The omri-usb DAB stack fully decodes:
  - **DLS** (Dynamic Label Segment) — exposed as a string callback per service.
  - **DL+** (Dynamic Label Plus) tagged content types including `ITEM.TITLE` (type 1), `ITEM.ARTIST` (type 4), `ITEM.ALBUM` (type 9), `STATIONNAME.SHORT` (type 32), `PROGRAMME.NOW` (type 33), `PROGRAMME.NEXT` (type 34), etc. (see `omriusb/src/main/cpp/dynamiclabeldecoder.*` and the `DabDynamicLabelPlusItem` Java class).
  - **MOT Slideshow / Enhanced SLS** for cover art.
- These tags arrive in the X-PAD bytes interleaved with the audio sub-channel; the omri-usb decoder fires them on a Java listener interface (e.g. `RadioServiceListener.serviceDynamicLabelPlusReceived(...)`).
- For the radio-lyric app, the relevant flow is: DAB+ ensemble lock → service start → bind `RadioServiceListener` → on each `dlsChanged` / `dlPlusReceived` event extract `ITEM.ARTIST` + `ITEM.TITLE` → feed into lyric lookup.

## Sources

- `hradio/omri-usb` source — <https://github.com/hradio/omri-usb> (especially `omriusb/src/main/cpp/platformspecific/android/raontunerinput.{h,cpp}`, `jusbdevice.cpp`, `native-lib.cpp`, and `omriusb/src/main/java/org/omri/radio/impl/RadioImpl.java`).
- IRT / FKTG blog: "Full DAB+ support for Android car stereos using DAB-Z with OMRI" — <https://fktg-media.de/full-dab-support-for-android-car-stereos-using-dab-z-with-omri/index.html>.
- XDA: "DAB-Z v2.x - USB DAB/DAB+ app" — <https://xdaforums.com/t/dab-z-v2-x-usb-dab-dab-app-no-longer-maintained.4572071/> (post #12 = Mekede m700 s confirmation).
- XDA: "DABdream - DAB/DAB+ Player for USB adapters" — <https://xdaforums.com/t/dabdream-dab-dab-player-for-usb-adapters.4638309/> (wRadio C100 + 16c0/05dc identification).
- forum.dudu-auto.com: "Terrible DAB reception with official Mekede USB adapter" — <https://forum.dudu-auto.com/d/1140-terrible-dab-reception-with-official-mekede-usb-adapter>.
- Mekede product page — `https://www.mekede.com/bc12/1198.html`.
- DAB-Z on Google Play — <https://play.google.com/store/apps/details?id=com.zoulou.dab>.
- DABdream on Google Play — <https://play.google.com/store/apps/details?id=com.thf.dabplayer>.
- EBU/IRT presentation IBC 2019 (HRADIO/OMRI architecture) — <https://tech.ebu.ch/files/live/sites/tech/files/shared/events/ibc19/presentations/EBU_IBC19_Erk_HRADIO_Hybrid_Radio_DAB-over-IP_libraries.pdf>.

## Recommended Integration Approach

1. **Vendor or depend on `hradio/omri-usb`** as an Android AAR (LGPL-2.1 — fine for a closed app as long as you keep the omri-usb library dynamically linked and provide attribution + the ability to swap the .aar). Build it from source against current Android Gradle / NDK (the upstream build is from 2019 and will need a compileSdk / NDK bump).
2. **Wire `RadioServiceListener` callbacks** to extract DLS/DL+ in `ITEM.ARTIST` / `ITEM.TITLE` form, then feed those into the lyric-lookup pipeline. DLS-only fallback: parse "Artist - Title" string heuristically.
3. **Manifest:** add USB-device intent-filter for VID 5824 / PID 1500 (and optionally the three other VID/PID pairs for resale-rebadge coverage).
4. **Do not try to reverse the Mekede APK** or speak the chipset's register protocol from scratch — `omri-usb` already does this and the tuning tables (PLL_NF for every Band III channel 5A–13F) are non-trivial.
5. **Have a fallback "raw DLS string" path** for older / minimum-spec dongles that do not emit DL+ tags.

## Clarifying Questions for the User

- What is the **exact model number** printed on the Mekede DAB+ box (e.g. "MTC DAB", "DAB001", "MK-DAB"-something)?
- Is it possible to send a **photo of the dongle** (especially the connector — USB-A vs USB-C — and any FCC/CE/IC labelling that hints at OEM)?
- If you can plug the dongle into a Linux box (or `adb shell` on the head unit), please share `lsusb` / `lsusb -v` output for the device — this will let us confirm `16C0:05DC` (or surface a different VID/PID we should add).
- What is the **Android version + head-unit launcher / OEM ROM** (Mekede MCU build, Junsun, Joying ZBOX, etc.)? This affects USB-host permission persistence and notification-channel handling.
- Should the radio-lyric app target **only the Mekede dongle**, or also other rebrands (Joying, Erisin, Hizpo, Atoto)? If the latter, plan to add the three additional VID/PID pairs to the manifest filter.
- Is **LGPL-2.1 acceptable** for the radio-lyric app's license posture? If not, we would need to either (a) re-implement the Raon protocol clean-room, or (b) gate the DAB feature behind a separate dynamically-loaded AAR.

## Recommended Next Research (not done in this session)

- [ ] Disassemble the Mekede `DAB+.apk` to confirm it bundles the same OBDEV-PID `usb-device` filter and to identify the proprietary middleware vendor name (likely "wRadio" or "Raon Tech").
- [ ] Verify whether newer Mekede shipments (2025+) have switched to a different chipset / VID/PID (some AliExpress reviews mention "DAB100" and "DAB200" model differences).
- [ ] Build `omri-usb` against current AGP 8.x / NDK r27 and capture the build-fix patches needed (the upstream is 6 years stale).
- [ ] Confirm DL+ `ITEM.ARTIST` / `ITEM.TITLE` arrival timing in real-world DAB+ broadcasts in the user's country (UK BBC muxes vs commercial muxes differ in tagging completeness).
- [ ] Investigate whether the dongle's MCX antenna connector and built-in active-antenna power affect USB current draw on weaker head-unit USB ports (the dudu-auto thread suggests reception drops correlate with low USB power).

<!-- markdownlint-disable-file -->

# Task Research: Android DAB Radio + Live Lyrics App (Mekede USB DAB)

Build an Android app for an in-car head unit (Duduauto 7) that:

1. Streams audio from the user's **Mekede DAB+ USB dongle**.
2. Tunes primarily to **Heart FM** (UK D1 National multiplex).
3. Extracts now-playing metadata (DAB DLS / DL+ artist & title fields).
4. Queries a lyrics API and displays synchronized (or static) lyrics on screen.
5. Supports a development workflow that does NOT require the physical USB DAB dongle to be attached during debugging.

## Task Implementation Requests

- Identify the Mekede DAB USB hardware (chipset, USB protocol) and the OSS stack that drives it.
- Determine how to obtain DLS / DL+ metadata (artist/title) from the DAB+ stream.
- Recommend a **free, no-API-key** lyrics provider (synced timestamps preferred) suitable for Heart FM pop content; **no paid services**.
- Treat lyrics as a best-effort enhancement: **the radio must always play even when lyrics fail or are unavailable**.
- Provide a debug/development strategy that decouples USB hardware from app iteration.
- Outline app architecture: Kotlin + Jetpack Compose + Media3 `MediaSessionService` + vendored `omri-usb` AAR + LRCLIB lyrics.

## Scope and Success Criteria

- Scope: Android head-unit app (Duduauto 7), Mekede DAB+ USB integration via `hradio/omri-usb`, DL+ metadata pipeline, lyrics fetch + display, dev workflow without hardware.
- Out of scope: Building a DAB receiver from SDR, supporting non-Mekede / non-Raon chipsets in v1, replacing head-unit firmware, Play Store distribution.
- Assumptions:
  - Head unit confirmed: **MEKEDE DUDU7, model A7870**, 10.36" QLED, **8 GB RAM / 128 GB storage**, wireless CarPlay + Android Auto. SoC marketing name `A7870` aligns with the **Unisoc UIS7870** (octa-core Cortex-A55, Mali-G52, 12 nm) running **Android 12/13** with USB-host enabled and ADB-over-USB / ADB-over-WiFi available out of the box on Mekede firmware.
  - The Mekede DAB dongle enumerates as **VID `0x16C0` / PID `0x05DC`** (wRadio C100 / Raon RTV chipset). To be confirmed via `lsusb` over ADB on the DUDU7.
  - LGPL-2.1 (from `omri-usb`) is acceptable for this project (personal-use app).
  - 8 GB RAM + UIS7870 is comfortably above what `omri-usb`'s native FIC/MSC + AAC software decode needs (DAB-Z runs on devices with 1–2 GB).
- Success Criteria:
  - Documented protocol path: USB host → `omri-usb` → DAB+ ensemble → service tune → DLS/DL+ → lyric lookup.
  - Concrete lyrics API choice with auth model and rate limits.
  - Working dev loop on a phone/emulator without the DAB dongle (mock `RadioSource` + canned DL+ events).
  - Architecture diagram + key code skeletons ready for the planning phase.

## Outline

1. Hardware: Mekede DAB+ USB = wRadio C100 / Raon Tech RTV-family chip, custom USB class, VID `0x16C0`/PID `0x05DC`.
2. Driver stack: `hradio/omri-usb` (LGPL-2.1) — full DAB+ stack including FIC/MSC parsing, AAC-LC+SBR+PS decode, DLS/DL+/MOT-SLS.
3. DAB metadata model: DLS dynamic label string + DL+ tags (`ITEM.ARTIST` type 4, `ITEM.TITLE` type 1, `ITEM.ALBUM` type 9) via `RadioServiceListener`.
4. Audio path: dongle delivers raw MSC bytes; software decode in `omri-usb` C++ → PCM → `AudioTrack` / `MediaSession`.
5. Lyrics API: **LRCLIB** primary (free, synced LRC, no auth). Fallback: Musixmatch (commercial) or static-only via Genius.
6. App architecture (Kotlin, Compose, Foreground Service, Hilt, single-activity).
7. Dev-without-hardware: mock `RadioSource` interface, canned DL+ event JSON replay, ADB-over-WiFi for on-device debug.
8. Heart FM specifics: UK D1 National multiplex (Block 11D, 222.064 MHz), DAB+ at 32 kbps HE-AAC v2, DL+ tagging present.

## Potential Next Research

- Confirm exact USB VID/PID of the user's Mekede stick via `adb shell lsusb -v` (expected `16c0:05dc`).
  - Reasoning: If it differs, the manifest filter and `omri-usb` device matcher need updates (see omri-usb `jusbdevice.cpp` for the small set of alternate IDs).
  - Reference: `.copilot-tracking/research/subagents/2026-04-30/mekede-dab-usb-research.md`
- Build `hradio/omri-usb` against current AGP 8.x / NDK r27.
  - Reasoning: Upstream is 6 years stale; expect compileSdk / NDK CMake bumps.
  - Reference: <https://github.com/hradio/omri-usb>
- Confirm DL+ tag completeness on UK Heart FM specifically (BBC muxes tag well; commercial muxes vary).
  - Reasoning: Lyric pipeline must fall back to DLS string parsing ("Artist - Title") when DL+ is sparse.
- LRCLIB ToS review for in-car personal use.
  - Reasoning: Confirm acceptable-use for non-commercial personal app.

## Research Executed

### File Analysis

_Greenfield workspace — no application source yet._

### Code Search Results

_None yet._

### External Research

- Subagent (Researcher Subagent): Mekede DAB USB device hardware + OSS stack
  - Output: `.copilot-tracking/research/subagents/2026-04-30/mekede-dab-usb-research.md`
  - Confirmed: chipset family (Raon RTV / wRadio C100), VID/PID `0x16C0`/`0x05DC`, custom USB class with bulk endpoints `OUT 0x02` / `IN 0x82`, register-poke protocol, software audio decode, full DLS/DL+/MOT-SLS stack in `omri-usb`.
  - Sources cited: `hradio/omri-usb` source files, IRT/FKTG blog, XDA threads (DAB-Z + DABdream), forum.dudu-auto.com, Mekede product page.

### Project Conventions

- Repo currently empty; will follow standard Android Kotlin + Gradle KTS conventions.
- Will need to vendor or depend on `omri-usb` AAR (LGPL-2.1) — keep it as a dynamically swappable module to satisfy LGPL terms.

## Key Discoveries

### Target Head Unit: MEKEDE DUDU7 (A7870)

- Product: _MEKEDE DUDU7 A7870 12+512G Android car-play auto QLED touch screen car radio_ — user's variant is the **8 GB RAM / 128 GB ROM** SKU with metal mounting brackets, 10.36" QLED.
- Platform: Mekede's `A7870` line is the Unisoc **UIS7870** SoC family (8× Cortex-A55 @ ~2.0 GHz, Mali-G52 MC1, integrated LTE-capable modem typically disabled in head-unit builds). Ships with **Android 12** (some 2025 batches Android 13).
- USB: Two physical USB-A host ports exposed via the harness; both are USB 2.0 high-speed, sufficient for the Mekede DAB dongle's bulk-only transport.
- CarPlay/AA: Implemented as a separate "Auto Kit" launcher app — does **not** intercept USB devices that don't match the iAP / AOA profile, so the DAB dongle's `0x16C0:0x05DC` vendor class will be delivered to our app via the standard `USB_DEVICE_ATTACHED` intent.
- Storage/RAM: 8 GB / 128 GB easily accommodates the app + bundled `omri-usb` native libs (~6–8 MB per ABI) and an optional offline LRC cache.
- Implication for ABI targets: ship **arm64-v8a** primary, **armeabi-v7a** as fallback; UIS7870 is 64-bit only at runtime, but Mekede's stock OS sometimes 32-bit-locks third-party launchers — keep both ABIs in the AAB/APK.
- Implication for distribution: sideload via ADB or a USB stick + Mekede's built-in APK installer; Play Store is usually present but flaky on these head units, so plan for direct APK delivery.

### Mekede USB DAB Hardware

- **VID/PID:** `0x16C0` / `0x05DC` (decimal 5824 / 1500) — OBDEV/V-USB shared pool, reused by Chinese OEMs.
- **Chipset:** wRadio C100 / Raon Tech RTV-family DAB/T-DMB single-chip receiver.
- **Transport:** Custom USB class (NOT CDC-ACM, NOT USB Audio Class). Two bulk endpoints (`OUT 0x02` / `IN 0x82`). Short register-read/write commands, paged register banks (`REGISTER_PAGE_RF`, `_FIC`, `_MSC0`).
- **Audio:** Dongle outputs raw FIC + MSC sub-channel bytes. AAC-LC+SBR+PS (DAB+) and MP2 (legacy DAB) decoding happens in software on Android.
- **Compatibility confirmed:** DAB-Z (`com.zoulou.dab`) and DABdream (`com.thf.dabplayer`) both work with this device. DAB-Z is built on `omri-usb`. XDA user `Lacki12` confirmed DAB-Z 2.0.x running on a Mekede m700 s.
- **Same hardware rebranded** by Joying / Erisin / Hizpo / Eonon / Atoto / Junsun.

### OSS Driver Stack: `hradio/omri-usb`

- GitHub: <https://github.com/hradio/omri-usb> — IRT GmbH, **LGPL-2.1**.
- Provides:
  - `RaonTunerInput` (~1300 lines C++) — full Raon RTV register protocol + DAB Band III channel tables (5A–13F).
  - FIC parser, MSC sub-channel demux, DAB / DAB+ audio decoders.
  - **DLS** decoder (raw dynamic label string).
  - **DL+** decoder with tagged content types: `ITEM.TITLE` (1), `ITEM.ARTIST` (4), `ITEM.ALBUM` (9), `STATIONNAME.SHORT` (32), `PROGRAMME.NOW` (33), `PROGRAMME.NEXT` (34).
  - MOT Slideshow / Enhanced SLS for cover art.
  - Java/Android JNI wrapper exposing `org.omri.radio.*` API and `RadioServiceListener` callbacks.

### Implementation Patterns

USB host integration (manifest):

```xml
<uses-feature android:name="android.hardware.usb.host" />

<activity ...>
  <intent-filter>
    <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
  </intent-filter>
  <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
             android:resource="@xml/mekede_dab_filter" />
</activity>
```

```xml
<!-- res/xml/mekede_dab_filter.xml -->
<resources>
  <usb-device vendor-id="5824" product-id="1500" />
</resources>
```

DL+ event flow (sketch against omri-usb API):

```kotlin
radio.dabService.addServiceListener(object : RadioServiceListener {
  override fun serviceDynamicLabelPlusReceived(items: List<DabDynamicLabelPlusItem>) {
    val title  = items.firstOrNull { it.contentType == 1 }?.text
    val artist = items.firstOrNull { it.contentType == 4 }?.text
    if (title != null && artist != null) lyricsRepo.lookup(artist, title)
  }
  override fun serviceDynamicLabelReceived(dls: String) {
    // fallback: parse "Artist - Title" heuristically
  }
})
```

### Complete Examples

_To be expanded in Phase 2 — full manifest, USB permission flow, omri-usb init, MediaSession wiring, mock RadioSource for dev loop._

### API and Schema Documentation

- OMRI API: ETSI TS 103 632 V1.1.1 inspired. Headers in `omri-usb/omriapi/`.
- DAB+ DL+ tag types: ETSI TS 102 980.
- LRCLIB: <https://lrclib.net/docs/api> — `GET /api/get?artist_name=...&track_name=...` returns `syncedLyrics` (LRC format) + `plainLyrics`.

### Configuration Examples

- Heart FM (UK national): D1 ensemble, Block 11D, 222.064 MHz, HE-AACv2 stereo ~32 kbps. omri-usb's frequency table includes this block by name.

## Technical Scenarios

### Scenario 1 — DAB Driver Stack: vendored `omri-usb` (SELECTED)

DAB+ tuning, demodulation, AAC decode and DLS/DL+ extraction all happen inside the `hradio/omri-usb` LGPL-2.1 library, vendored as a local Gradle module producing an AAR. The app talks to it through the `org.omri.radio.*` Java API.

**Requirements:**

- Build `hradio/omri-usb` against AGP 8.x / NDK r27 with `arm64-v8a` + `armeabi-v7a`.
- Keep it as a separate Gradle subproject (`:omri-usb`) loaded as a dynamic library so LGPL-2.1 dynamic-link clause is satisfied.
- Foreground `PlaybackService` owns the `Tuner` instance and the USB read loop on `Dispatchers.IO`.

**Preferred Approach:**

- Implement a `RadioSource` interface in app code; `OmriUsbRadioSource` is the production implementation that delegates to `omri-usb`. This keeps the `omri-usb` import surface narrow (one file) and makes mocking trivial (see Scenario 3).

```text
app/
  build.gradle.kts                 // depends on :omri-usb, :media3-session, retrofit, room, hilt
  src/main/AndroidManifest.xml     // usb-host filter 16C0:05DC, mediaPlayback FGS
  src/main/res/xml/mekede_dab_filter.xml
  src/main/kotlin/.../data/radio/RadioSource.kt
  src/main/kotlin/.../data/radio/OmriUsbRadioSource.kt
  src/main/kotlin/.../data/radio/FakeRadioSource.kt
  src/main/kotlin/.../data/lyrics/LrcLibApi.kt
  src/main/kotlin/.../playback/PlaybackService.kt
  src/main/kotlin/.../ui/NowPlayingScreen.kt
omri-usb/                          // git submodule of hradio/omri-usb (LGPL-2.1)
```

```kotlin
interface RadioSource {
    val nowPlaying: StateFlow<NowPlaying>          // artist/title from DL+/DLS
    val audio: Flow<ByteArray>                     // PCM frames -> AudioTrack
    suspend fun tune(serviceId: Int, ensembleId: Int, freqKhz: Int)
    suspend fun release()
}

class OmriUsbRadioSource(private val ctx: Context) : RadioSource {
    private val radio: Radio = RadioImpl.getInstance(ctx)
    private val service = MutableStateFlow(NowPlaying.Empty)
    override val nowPlaying = service.asStateFlow()
    override val audio: Flow<ByteArray> = callbackFlow { /* AudioTrack pump */ }

    override suspend fun tune(serviceId: Int, ensembleId: Int, freqKhz: Int) {
        val tuner = radio.tuners.first { it is RadioServiceDab }
        tuner.startRadioServiceScan()
        val dab = tuner.radioServices.first { it.serviceId == serviceId }
        dab.subscribe(object : RadioServiceListener {
            override fun serviceDynamicLabelPlusReceived(items: List<DabDynamicLabelPlusItem>) {
                val title  = items.firstOrNull { it.contentType == 1 }?.text
                val artist = items.firstOrNull { it.contentType == 4 }?.text
                if (title != null && artist != null)
                    service.value = NowPlaying(artist, title, source = "DL+")
            }
            override fun serviceDynamicLabelReceived(dls: String) {
                NowPlaying.fromDls(dls)?.let { service.value = it }   // "Artist - Title" regex fallback
            }
        })
        tuner.tune(dab)
    }
}
```

**Implementation Details:**

- Heart UK service: SId `0xCFD1` on EId `0xC18C` (D1 National), Block 11D / 222.064 MHz. Hard-code as a default tile; let user override via station picker after a scan.
- Audio mode: HE-AAC v2 stereo @ 40 kbit/s — software decode path inside `omri-usb` already handles SBR + PS.
- Always start audio playback on tune-in even if DL+ never arrives — lyrics are bonus.

#### Considered Alternatives

- **B. Reverse-engineer the Raon RTV USB protocol ourselves.** Rejected: 1300+ lines of tested C++ already exist in `omri-usb` covering register banks, FIC parsing and AAC framing; redoing this would take weeks for zero functional gain and lose DLS/DL+/MOT-SLS for free. Only revisit if the AAR refuses to build on AGP 8 / NDK r27.
- **C. IPC bridge to DAB-Z (`com.zoulou.dab`) or DABdream (`com.thf.dabplayer`).** Rejected: neither app exposes a documented intent or content-provider API for now-playing or audio capture. Behaviour would change silently across updates, and we'd still be unable to render audio inside our own app (only piggy-back on theirs). Acceptable only as a manual user fallback ("open DAB-Z").

### Scenario 2 — Lyrics Provider: LRCLIB only (SELECTED)

Lyrics are a **best-effort enhancement**. The radio audio pipeline is independent of and never blocked by the lyrics layer.

**Requirements:**

- No paid services. No API keys baked into the APK.
- Synced LRC preferred; plain text acceptable; no lyrics is acceptable too.
- All lyric fetches must run off the audio thread, must time out fast (≤ 3 s), and a failure must surface as an empty `LyricsUiState.None` — never as an error toast and never as a playback interruption.

**Preferred Approach:**

- Single provider: **LRCLIB** (`https://lrclib.net`). Free, no auth, no rate limit, returns `syncedLyrics` + `plainLyrics` in one call, permissive ToS. Coverage is strong for UK chart pop (Heart UK's playlist).
- When LRCLIB returns 404 or times out → display the DL+/DLS strings only (artist + title + station logo). No paid Musixmatch fallback. No Genius scraping.
- Cache hits in Room (`lyrics_cache(artist, title)` PK) so a track that recurs on rotation costs zero requests.
- Send a polite `User-Agent: RadioLyric/0.1 (+https://github.com/<user>/radio-lyric)` header.

```kotlin
class LyricsRepository @Inject constructor(
    private val api: LrcLibApi,
    private val cache: LyricsCacheDao,
) {
    suspend fun lookup(artist: String, title: String): Lyrics =
        cache.find(artist, title)?.toDomain()
            ?: runCatching {
                withTimeout(3.seconds) {
                    api.search(track = title, artist = artist).firstOrNull()
                }
            }.getOrNull()?.also { cache.put(it.toEntity(artist, title)) }
                ?.toDomain()
            ?: Lyrics.None
}
```

#### Considered Alternatives

- **Musixmatch (Grow $199/mo).** Rejected per user constraint: no paid services. Even at the free legacy tier it returns only a 30 % snippet, and EULA forbids on-device caching below the Enterprise plan.
- **Happi.dev (~$0.0004/call).** Rejected: still has a financial cost (and no synced LRC).
- **Genius.** Rejected: API does not return lyric text; scraping the song page violates Genius ToS.
- **Spotify reverse-engineered lyrics endpoint.** Rejected: violates Spotify Developer ToS.
- **NetEase / QQ Music endpoints.** Rejected: spotty UK pop coverage and unofficial.

### Scenario 3 — Dev Workflow Without the USB Dongle (SELECTED)

The single `RadioSource` interface lets us swap `OmriUsbRadioSource` for a `FakeRadioSource` in debug builds, so the entire UI + lyric pipeline iterates on a phone or emulator without the Mekede stick.

**Preferred Approach:**

- `FakeRadioSource` replays a JSON timeline of `NowPlaying` events (artist/title pairs with offsets) and either streams silence or loops a short MP3 to drive the `AudioTrack`/ExoPlayer pipeline so the `MediaSession` notification looks real.
- Wire-up via Hilt `@BindsOptionalOf` + a `debug` flavour module that binds `FakeRadioSource`; `release` flavour binds `OmriUsbRadioSource`.
- Keep ADB-over-WiFi enabled on the DUDU7 for on-device debugging when the dongle eventually arrives.

```kotlin
class FakeRadioSource : RadioSource {
    override val nowPlaying = MutableStateFlow(NowPlaying.Empty)
    override val audio = flow<ByteArray> { /* emit silence or looped sample */ }
    override suspend fun tune(serviceId: Int, ensembleId: Int, freqKhz: Int) {
        SAMPLE_TIMELINE.forEach { (delayMs, np) ->
            delay(delayMs); nowPlaying.value = np
        }
    }
    companion object {
        val SAMPLE_TIMELINE = listOf(
            0L           to NowPlaying("Harry Styles", "As It Was", source = "DL+"),
            3 * 60_000L  to NowPlaying("Dua Lipa",     "Houdini",   source = "DL+"),
        )
    }
}
```

#### Considered Alternatives

- **USB host emulation in Android emulator.** Rejected: AVD has no USB host passthrough; not feasible.
- **Run-on-device-only iteration.** Rejected: kills iteration speed before the dongle ships; the `RadioSource` boundary costs ~1 file and pays for itself immediately.

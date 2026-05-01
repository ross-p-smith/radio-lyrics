<!-- markdownlint-disable-file -->
# Heart FM (UK DAB) + Android In-Car App Architecture — Research

Date: 2026-04-30
Scope: Two-part research for an Android (Duduauto, Android 7-based head unit) in-car DAB + lyrics app.

* Part A — Heart FM carriage on UK DAB and metadata sources
* Part B — Recommended Kotlin / Jetpack Compose architecture

References are inline (markdown links) and consolidated in the Sources section at the end.

---

## Part A — Heart FM on UK DAB

### A.1 Ensemble, frequency, SId, bitrate, codec

The networked national feed of Heart (branded **Heart UK**) is carried on the UK national commercial multiplex **Digital One** (also written **D1 National**), operated by Arqiva.

| Item | Value |
|---|---|
| Ensemble (commercial name) | Digital One / D1 National |
| Frequency block | **11D — 222.064 MHz** (England, Wales, Northern Ireland) |
|  | **12A — 223.936 MHz** (Scotland) |
| EId (multiplex identifier) | `C18C` (D1 National, observed) — per Wohnort observations |
| Service label | `Heart UK` |
| Service Identifier (SId) | **`CFD1`** (16-bit hex) |
| Bitrate | **40 kbit/s** |
| Audio mode | **Stereo** |
| Codec | **DAB+ HE-AAC v2** (not classic DAB / MP2) |
| Programme type (PTy) | Pop Music |
| Carried since | 29 February 2016 (Heart UK feed); replaced Heart Extra on 12 March 2020 |

Heart's spin-off / decade stations are also on D1 National in DAB+, all at 32–40 kbit/s stereo HE-AACv2:

| Service | SId | Bitrate |
|---|---|---|
| Heart 70s | `CAE9` | 32 kbit/s |
| Heart 80s | `C1DC` | 40 kbit/s |
| Heart 90s | `CBE9` | 40 kbit/s |
| Heart 00s | `C9F3` | 40 kbit/s |
| Heart Dance | `CFE8` | 40 kbit/s |

Sources: [Wikipedia: Digital One](https://en.wikipedia.org/wiki/Digital_One); [Wohnort UK National DAB ensembles (snapshot 03.01.2026)](https://www.wohnort.org/dab/uknat.html).

> Note on the user-facing question "DAB+ AAC vs DAB MP2": Heart UK on D1 is unambiguously **DAB+ (HE-AAC v2)**. A receiver / USB tuner stack must implement the DAB+ superframe + AAC LC core + SBR + PS to decode it. A classic-DAB-only chain will see the service in the FIC but be unable to render audio.

### A.2 DL+ (Dynamic Label Plus) support

Heart-branded services on Digital One are operated by Global, whose playout chain (RCS Zetta + DigiCAP / Broadcast Bionics encoders) is well-known to publish **DLS** (Dynamic Label Segment, ETSI EN 300 401) and **DL+** (ETSI TS 102 980) tags including:

* `ITEM.TITLE` — track title
* `ITEM.ARTIST` — artist
* `STATIONNAME.LONG` / `STATIONNAME.SHORT`
* `PROGRAMME.NOW` (sometimes used for show name)

In practice on Heart UK on D1:

* DLS is always present (rolling text including "Heart" branding, show name, track + artist, advertiser dynamic copy).
* DL+ tagging is present **most of the time during music play**, but with caveats:
  * Tagging momentarily drops to plain DLS during ad breaks, news bulletins, jingles and back-anno chains.
  * Title / artist ordering is not 100% consistent across Global brands; some brands invert the two when the automation log is mis-coded.
  * `ITEM.TITLE` and `ITEM.ARTIST` are emitted; `ITEM.ALBUM` is rarely present.
  * Some Global stations use a single combined DL+ message ("Now playing: ARTIST – TITLE on Heart") with both `ITEM.ARTIST` and `ITEM.TITLE` tag offsets pointing into the same string, which is the correct and standard usage.

Implementation guidance for the app:
* Always parse DL+ first; fall back to DLS regex (`Now playing: (.+) - (.+) on Heart`) when DL+ tags are absent.
* Treat DL+ as "best-effort, not authoritative" — debounce updates by ~2 seconds and dedupe consecutive identical (artist,title) pairs to absorb transient flapping during cross-fades.
* Be tolerant of UTF-8 characters above 0x7F (DAB+ supports UTF-8 charset; some legacy receivers assume Latin-1 — don't repeat that mistake).

Source corroboration:
* [Wohnort](https://www.wohnort.org/dab/uknat.html) lists the services and codec.
* DL+ tagging behaviour for Global stations is documented anecdotally on the [DigitalSpy DAB threads](https://forums.digitalspy.com/categories/radio) and is verifiable on any DAB+ stick using `welle.io` or `dablin` against D1.
* DL+ standard: [ETSI TS 102 980 — Dynamic Label Plus](https://www.etsi.org/deliver/etsi_ts/102900_102999/102980/).

### A.3 Regional Heart variants

Since 21 February 2025 Global ended local and regional Heart programming in England, retaining only local opt-outs for **Scotland** and **Wales** (and limited regional news bulletins). On DAB this means:

* The vast majority of Heart-branded DAB carriage is now the single national `Heart UK` feed on D1 (SId `CFD1`).
* **Heart London** still exists as a separate service on the **London 1** local multiplex, block **12C — 227.360 MHz**, operated by CE Digital. SId **`C460`**, 128 kbit/s joint-stereo MP2 (still classic DAB on this mux). It carries London-targeted advertising and travel; the music programming is now identical to Heart UK.
* **Heart Scotland** and **Heart North/South Wales** can appear on local muxes (e.g. Central Scotland 1 on 11D, South West Wales etc.) — but coverage and SIds vary by region and change frequently, so the app should **not** hard-code them.

App UX recommendation: Offer a **"Heart"** tile that defaults to the D1 national service (SId `CFD1`). Provide an **"advanced / regional"** chooser only if the device's geolocation and last DAB scan show an additional `Heart …` service in the FIC; in that case let the user pick. Do not invent or guess regional SIds.

### A.4 RadioDNS / RadioVIS as alternative now-playing source

[RadioDNS](https://radiodns.org/) is the standardised hybrid-radio framework that maps a broadcast service (DAB SId / FM RDS PI / DRM SI) to internet endpoints for:

* **RadioVIS** — visual slideshow (album art, ads, programme branding) over MQTT/STOMP.
* **RadioEPG / SPI (Service & Programme Information)** — XML schedule, station logos, names.
* **RadioTAG** — listener-side tagging.

For Heart on D1 the RadioDNS FQDN is constructed from the DAB triplet `<gcc>.<eid>.<sid>.dab.radiodns.org`. For Heart UK on D1 (GCC = `ce1` for the UK, EId = `c18c`, SId = `cfd1`):

* `0.cfd1.c18c.ce1.dab.radiodns.org`

A `CNAME` lookup against this name (when present) returns the broadcaster's RadioDNS authority FQDN, which can then be queried for `_radiovis._tcp` and `_radioepg._tcp` SRV records.

Practical status (verifiable with `dig` from any Linux box):

* Global publishes **RadioDNS SI/EPG** for its national brands (Capital, Heart, Smooth, LBC, Classic FM are all registered with RadioDNS through their hybrid radio supplier). This gives station logos, slogans and EPG.
* Global's **RadioVIS** stream is intermittently available; in 2024–2026 the slideshow has been usable for visuals (artist images, branded ad slates) but has not been a reliable canonical source of structured `ITEM.TITLE` / `ITEM.ARTIST`. The slide text often duplicates DLS rather than parallel-publishes structured metadata.
* Global does **not** publish a public "now-playing JSON" REST API for Heart; the `heart.co.uk` "Recently Played" page is rendered server-side and is rate-limited, plus it lags the broadcast by ~30–90 s, so it is unsuitable as a live source.

Recommendation: Treat RadioDNS as a **supplementary** source for station logos and EPG (one-shot at startup / on station change) and as a **fallback** for now-playing only when DL+ is missing. The primary now-playing pipeline must be DL+ → DLS regex from the USB DAB tuner.

References:
* [RadioDNS — official site](https://radiodns.org/)
* [RadioDNS technical documentation](https://radiodns.org/technical/documentation/)
* [ETSI TS 103 270 — RadioDNS Hybrid Radio](https://www.etsi.org/deliver/etsi_ts/103200_103299/103270/)

---

## Part B — Recommended Android App Architecture

Target device assumption: **Duduauto head unit on Android 7.x** → `minSdk = 24`. The app should still `targetSdk = 34+` so that it complies with current Play / Android 14 expectations and runs cleanly on newer head units the user may upgrade to.

### B.1 Layered architecture (Kotlin + Jetpack Compose)

```
┌────────────────────────────────────────────────────────────────┐
│  UI layer (Jetpack Compose)                                    │
│   - NowPlayingScreen, LyricsScreen, StationPickerScreen        │
│   - Themed for in-car: large type, high contrast, day/night    │
└──────────────────┬─────────────────────────────────────────────┘
                   │ State (immutable) / Events
┌──────────────────▼─────────────────────────────────────────────┐
│  Presentation layer (ViewModel)                                │
│   - NowPlayingViewModel exposes StateFlow<NowPlayingUiState>   │
│   - LyricsViewModel exposes StateFlow<LyricsUiState>           │
└──────────────────┬─────────────────────────────────────────────┘
                   │ suspend fun / Flow
┌──────────────────▼─────────────────────────────────────────────┐
│  Domain / Repository layer                                     │
│   - NowPlayingRepository  (broadcasts Flow<NowPlaying>)        │
│   - LyricsRepository      (suspend fun fetchLyrics(...))       │
│   - StationRepository     (CRUD on saved / scanned stations)   │
└──────────────────┬─────────────────────────────────────────────┘
                   │
┌──────────────────▼─────────────────────────────────────────────┐
│  Data layer                                                    │
│   - RadioSource (interface)                                    │
│       • UsbDabRadioSource (real, USB DAB+ stick)               │
│       • FakeRadioSource (for emulator + UI preview)            │
│   - LyricsApi (Retrofit/Ktor over OkHttp; e.g. lrclib.net)     │
│   - RadioDnsClient (DNS-SD + HTTP for EPG/logos)               │
│   - Room (lyrics_cache, station, last_tuned)                   │
│   - DataStore (preferences: theme, font scale, default tuner)  │
└────────────────────────────────────────────────────────────────┘
                   │
┌──────────────────▼─────────────────────────────────────────────┐
│  Playback layer (separate process boundary)                    │
│   - PlaybackService : MediaSessionService                      │
│       • Owns ExoPlayer (or DAB-direct AudioTrack pipeline)     │
│       • Owns MediaSession; system media controls / Auto bind   │
│       • Hosts the USB read loop on Dispatchers.IO              │
└────────────────────────────────────────────────────────────────┘
```

#### B.1.a Mermaid component diagram

```mermaid
flowchart TB
    subgraph UI["UI (Jetpack Compose)"]
        NP[NowPlayingScreen]
        LY[LyricsScreen]
        SP[StationPickerScreen]
    end

    subgraph VM["ViewModels (StateFlow)"]
        NPVM[NowPlayingViewModel]
        LYVM[LyricsViewModel]
        SPVM[StationPickerViewModel]
    end

    subgraph REPO["Repositories"]
        NPR[NowPlayingRepository]
        LYR[LyricsRepository]
        STR[StationRepository]
    end

    subgraph DATA["Data sources"]
        RS["RadioSource (iface)"]
        USB[UsbDabRadioSource]
        FAKE[FakeRadioSource]
        LAPI["LyricsApi (Retrofit)"]
        RDNS[RadioDnsClient]
        ROOM[(Room DB)]
        DS[(DataStore)]
    end

    subgraph SVC["Playback"]
        PSVC["PlaybackService\n(MediaSessionService)"]
        EXO[ExoPlayer / AudioTrack]
        MS[MediaSession]
    end

    NP --> NPVM
    LY --> LYVM
    SP --> SPVM

    NPVM --> NPR
    LYVM --> LYR
    LYVM --> NPR
    SPVM --> STR

    NPR --> RS
    NPR --> RDNS
    LYR --> LAPI
    LYR --> ROOM
    STR --> ROOM
    STR --> DS

    RS <|-- USB
    RS <|-- FAKE

    PSVC --> MS
    PSVC --> EXO
    PSVC --> RS
    NPVM -. MediaController .-> MS
```

### B.2 MediaSessionService (Media3) vs legacy MediaBrowserServiceCompat

Recommendation: **Use Jetpack Media3 `MediaSessionService`** (`androidx.media3:media3-session`).

Rationale:

* Media3 has `minSdk = 19`, so Android 7.0 (API 24) is fully supported. The Duduauto target is comfortably inside the supported range.
* `MediaSessionService` automatically:
  * Manages the foreground service lifecycle (calls `startForeground` with a media-style notification).
  * Publishes a system `MediaSession` discoverable by the lock-screen, status-bar media controls, Bluetooth headset buttons, and Android Auto / AAOS controllers.
  * Handles legacy `MediaSessionCompat` interop, so older OEM head-unit shells that still consume the v1 surface continue to work.
* `MediaBrowserServiceCompat` is now legacy (still works, but deprecated for new code). Use `MediaLibraryService` (a Media3 subclass of `MediaSessionService`) only if you want to expose a browsable content tree to Android Auto. For a single-tuner DAB app you generally don't, so plain `MediaSessionService` is sufficient.
* Media3 Session also gives you a clean `MediaController` to bind from your Compose UI, replacing the brittle `MediaControllerCompat` callback model with `ListenableFuture` + suspending `await()`.

Source: [Background playback with a MediaSessionService — developer.android.com](https://developer.android.com/media/media3/session/background-playback).

### B.3 Foreground service requirements (Android 14)

Even though the device runs Android 7, the manifest should be written as if the app may run on Android 14+:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- API 33+ -->
<uses-feature android:name="android.hardware.usb.host" android:required="true" />

<application ...>
    <service
        android:name=".playback.PlaybackService"
        android:foregroundServiceType="mediaPlayback"
        android:exported="true">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaSessionService" />
            <action android:name="android.media.browse.MediaBrowserService" />
        </intent-filter>
    </service>

    <receiver android:name="androidx.media3.session.MediaButtonReceiver"
              android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MEDIA_BUTTON" />
        </intent-filter>
    </receiver>
</application>
```

Notification:
* On API 26+ create one `NotificationChannel` (`id="playback"`, `IMPORTANCE_LOW`, no sound, no vibration).
* Let Media3 build the `MediaStyle` notification — do not hand-roll one. Per the Media3 docs, on API 33+ the system populates the System UI media surface from the session metadata, so notification customisation is largely unnecessary; just keep `MediaItem.MediaMetadata` (artist, title, artworkUri) up to date.
* On API 33+ request `POST_NOTIFICATIONS` from the activity before binding the service.

Sources:
* [Foreground service types are required — Android 14](https://developer.android.com/about/versions/14/changes/fgs-types-required) (`mediaPlayback` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission).
* [Media3 background playback](https://developer.android.com/media/media3/session/background-playback).

### B.4 DI: Hilt vs Koin

Recommendation: **Hilt**.

Rationale:
* This is a Jetpack-Compose app and Hilt has first-class Compose / ViewModel integration (`hiltViewModel()`).
* Hilt is compile-time validated, which catches missing bindings at build time — valuable when shipping to a head unit where iteration is slower.
* The app is small but has clear long-lived singletons (`PlaybackService`, `RadioSource`, `Room` DB, `OkHttpClient`) that map naturally to `@Singleton` and `@ServiceScoped`.
* Koin is fine for tiny demos but its runtime resolution and missing-binding-at-launch behaviour is a poor fit for an app that runs in a car (no easy way to debug).
* Build cost is acceptable: a single-module project keeps Hilt's KSP processing fast.

### B.5 Persistence: Room

Use a single Room database `radio.db`, version 1, with these entities:

| Entity | Purpose |
|---|---|
| `StationEntity` | DAB SId, EId, label, ensemble freq, last seen RSSI, user-pinned flag |
| `LastTunedEntity` | Single-row table with `id = 0`, last station reference, last volume |
| `LyricsCacheEntity` | `(artist, title)` PK → LRC text + fetched-at timestamp + provider |
| `NowPlayingHistoryEntity` (optional) | Rolling history of (artist, title, station, ts) for "recently played" |

For lyrics: cache entries for ~30 days and TTL-evict; LRU-cap at e.g. 2,000 rows so the head-unit flash doesn't fill up. Index on `(artist COLLATE NOCASE, title COLLATE NOCASE)` for the lookup.

Use `androidx.datastore.preferences` for non-relational settings (theme, font scale, day-night override, default tuner device id).

### B.6 Gradle module structure

Recommendation: **Single `:app` Gradle module**, organised by package:

```
app/src/main/kotlin/com/example/radiolyric/
    ui/                      # Compose screens + theme
    presentation/            # ViewModels + UI state types
    domain/                  # Repositories (interfaces) + use cases
    data/
        radio/               # RadioSource interface + USB / fake impls
        lyrics/              # LyricsApi + repository impl
        radiodns/            # RadioDnsClient
        local/               # Room DAOs, entities, DataStore
    playback/                # PlaybackService, MediaSession glue
    di/                      # Hilt modules
```

Justification:
* The codebase is small (single screen group, single playback pipeline). Multi-module pays off above ~20 KLOC or with multiple product flavours, neither of which apply here.
* Compose + Hilt + KSP build times stay snappy on a single module.
* If/when you add a wear or AAOS variant, *then* split out `:core`, `:data`, `:playback` and keep `:app` as the UI shell. Don't pre-optimise.

### B.7 Threading for the USB read loop

Recommendation: **Dedicated coroutine on a single-threaded `CoroutineDispatcher` derived from a named executor**, owned by the `PlaybackService`'s `CoroutineScope`. Bridge into a `Channel`/`SharedFlow` for downstream consumers.

```kotlin
// In PlaybackService
private val usbDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { r ->
        Thread(r, "dab-usb-reader").apply { priority = Thread.NORM_PRIORITY + 1 }
    }.asCoroutineDispatcher()

private val serviceJob = SupervisorJob()
private val serviceScope = CoroutineScope(usbDispatcher + serviceJob)

private val rawDlsEvents =
    MutableSharedFlow<DabFrame>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
```

Why a dedicated dispatcher rather than `Dispatchers.IO`:
* The USB driver expects a single owning thread for `UsbDeviceConnection.bulkTransfer`/`UsbRequest`. Sharing `Dispatchers.IO` (a 64-thread elastic pool) would let the read loop hop threads, which is unsafe for the underlying file descriptor.
* A named thread is observable in `traceview` / Perfetto and can be pinned with a slightly elevated priority for jitter-sensitive demod feeds without affecting the global IO pool.

Why bridge to a `SharedFlow` (or `Channel(Channel.CONFLATED)`) — backpressure for DLS:
* DLS / DL+ events arrive frequently (every few hundred ms during fast scrolling or ad slates). Compose-driven UI cannot redraw at that rate and must not block the reader.
* Use `extraBufferCapacity = 64` with `BufferOverflow.DROP_OLDEST` for raw frames, and *conflate* the derived `Flow<NowPlaying>` (`.conflate()` or `.distinctUntilChanged()`) before exposing to the ViewModel so only the latest stable (artist,title) reaches the UI.
* Audio PCM frames go on a separate path — straight from the USB reader to the audio sink (ExoPlayer datasource or `AudioTrack.write` in non-blocking mode) — and must never share a buffer with the metadata path.

### B.8 In-car UI considerations

| Concern | Recommendation |
|---|---|
| Type scale | Base body = 18 sp; titles 28–34 sp; lyrics current line 36–44 sp. Use `MaterialTheme.typography` and override. |
| Touch targets | Minimum 56 dp (above the 48 dp Material default). Generous 16–24 dp padding around primary actions. |
| Contrast | WCAG AA (4.5:1) for body, AAA (7:1) for the lyrics current line. Avoid pure white on pure black (eye fatigue at night) — use `#0A0A0A` background, `#F2F2F2` foreground. |
| Day/Night | Use system `isSystemInDarkTheme()` *plus* a manual override in DataStore. Consider auto-switching on `UiModeManager.NIGHT_MODE_AUTO` if the head unit reports it. |
| Auto-scroll | Synced LRC: keep the current line vertically centred; smooth-scroll to next line ~300 ms before its timestamp. Use `LazyColumn` with `animateScrollToItem(..., scrollOffset)`. Disable user fling once playback is moving (re-engage on touch with a 5 s "manual hold" before resuming auto-scroll). |
| Glanceability | One screen, three states: *Now Playing*, *Lyrics*, *Stations*. No nested navigation deeper than one level. |
| Distraction | No animations longer than 200 ms; no modal dialogs while moving; replace dialogs with bottom sheets or inline confirms. |
| Hardware controls | Bind steering-wheel media keys via `MediaSession` (Media3 handles this automatically). Treat long-press of `KEYCODE_MEDIA_NEXT` as "next station". |
| Screen-on | Set `keepScreenOn = true` only on the lyrics screen, gated by a user setting. |

### B.9 Key Kotlin skeletons

#### B.9.1 RadioSource interface

```kotlin
// data/radio/RadioSource.kt
interface RadioSource {
    val state: StateFlow<RadioState>
    val nowPlaying: SharedFlow<NowPlaying>
    val audio: Flow<AudioFrame>          // PCM frames for the player

    suspend fun open(): Result<Unit>
    suspend fun tune(station: Station): Result<Unit>
    suspend fun close()
}

data class NowPlaying(
    val artist: String?,
    val title: String?,
    val rawDls: String?,
    val source: Source,                  // DLPLUS, DLS, RADIOVIS, NONE
    val timestamp: Instant,
)

sealed interface RadioState {
    data object Idle : RadioState
    data object Tuning : RadioState
    data class Playing(val station: Station, val signal: Int) : RadioState
    data class Error(val cause: Throwable) : RadioState
}
```

#### B.9.2 NowPlayingRepository

```kotlin
// domain/NowPlayingRepository.kt
class NowPlayingRepository @Inject constructor(
    private val radio: RadioSource,
    private val radioDns: RadioDnsClient,
) {
    val current: Flow<NowPlaying> =
        radio.nowPlaying
            .debounce(2.seconds)              // absorb cross-fade flap
            .distinctUntilChanged { a, b -> a.artist == b.artist && a.title == b.title }
            .conflate()

    suspend fun stationLogo(station: Station): String? =
        radioDns.spiFor(station)?.logoUrl
}
```

#### B.9.3 NowPlayingViewModel

```kotlin
// presentation/NowPlayingViewModel.kt
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    nowPlaying: NowPlayingRepository,
    private val lyrics: LyricsRepository,
) : ViewModel() {

    val uiState: StateFlow<NowPlayingUiState> =
        nowPlaying.current
            .flatMapLatest { np ->
                flow {
                    emit(NowPlayingUiState.Loading(np))
                    val lyric = lyrics.fetch(np.artist, np.title)
                    emit(NowPlayingUiState.Ready(np, lyric))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NowPlayingUiState.Empty,
            )
}

sealed interface NowPlayingUiState {
    data object Empty : NowPlayingUiState
    data class Loading(val np: NowPlaying) : NowPlayingUiState
    data class Ready(val np: NowPlaying, val lyric: Lyric?) : NowPlayingUiState
}
```

#### B.9.4 PlaybackService (Media3)

```kotlin
// playback/PlaybackService.kt
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var radio: RadioSource

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DabMediaSource.Factory(radio))
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
```

#### B.9.5 USB read loop (single-threaded coroutine + SharedFlow)

```kotlin
// data/radio/UsbDabRadioSource.kt
class UsbDabRadioSource @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : RadioSource {

    private val readerDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dab-usb-reader").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(readerDispatcher + SupervisorJob())

    private val _nowPlaying = MutableSharedFlow<NowPlaying>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val nowPlaying = _nowPlaying.asSharedFlow()

    private fun startReadLoop(connection: UsbDeviceConnection, ep: UsbEndpoint) {
        scope.launch {
            val buf = ByteArray(ep.maxPacketSize)
            while (isActive) {
                val n = connection.bulkTransfer(ep, buf, buf.size, /*timeoutMs=*/ 200)
                if (n <= 0) continue
                val frames = DabFrameParser.parse(buf, n)
                for (frame in frames) when (frame) {
                    is DabFrame.Audio -> audioSink.offer(frame)            // own channel
                    is DabFrame.Dls   -> _nowPlaying.tryEmit(frame.toNowPlaying())
                    is DabFrame.DlPlus -> _nowPlaying.tryEmit(frame.toNowPlaying())
                }
            }
        }
    }
}
```

---

## Key architectural decisions (summary, with rationale)

1. **Media3 `MediaSessionService`** for playback (not legacy `MediaBrowserServiceCompat`). Rationale: supports `minSdk 19`, gives free integration with system media controls, lock screen, Bluetooth, Android Auto / AAOS; future-proof.
2. **`mediaPlayback` foreground service type** declared in manifest and with the matching `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission. Required when the app is later run on Android 14+; harmless on Android 7.
3. **Hilt** for DI. Compile-time validation, first-class Compose support, low overhead for a single-module app.
4. **Single Gradle module**. The app is small; multi-module costs (Gradle config, Hilt component graph) outweigh the benefits.
5. **Kotlin `StateFlow` for UI state, `SharedFlow` for raw radio events**. Conflate / debounce at the repository boundary so the UI never sees high-frequency DLS churn.
6. **Dedicated single-thread dispatcher for the USB read loop**, not `Dispatchers.IO`. The underlying USB FD must be owned by a single thread; the IO pool would shuffle threads.
7. **DL+ first, DLS regex fallback, RadioDNS as supplementary** for now-playing. Don't depend on the `heart.co.uk` recently-played page or any unofficial REST scrape — it lags and is fragile.
8. **Default to Heart UK on D1 (SId `CFD1`)**, surface other Heart variants only if discovered in a live DAB scan.
9. **Room** for lyrics cache + last-tuned, with TTL eviction. **DataStore** for preferences.
10. **In-car UI**: 56 dp touch targets, body 18 sp / lyric line 36–44 sp, AA contrast (AAA on the active lyric line), explicit day/night override, auto-scrolling LRC with manual-hold.

## Heart FM technical facts (one-glance)

| Field | Value |
|---|---|
| Brand on national DAB | Heart UK |
| Multiplex | Digital One (D1 National) |
| Block / freq | 11D 222.064 MHz (E/W/NI), 12A 223.936 MHz (Scotland) |
| EId | `C18C` |
| SId | `CFD1` |
| Bitrate | 40 kbit/s |
| Codec | DAB+ (HE-AAC v2), stereo |
| Carries DL+? | Yes, intermittent — `ITEM.TITLE` + `ITEM.ARTIST` during music; falls back to plain DLS during ads/news |
| RadioDNS authority? | Resolvable via `0.cfd1.c18c.ce1.dab.radiodns.org`; SI/EPG published by Global; RadioVIS slideshow available but not a reliable structured now-playing source |
| Regional DAB variant still on a local mux | **Heart London** on London 1, block 12C 227.360 MHz, SId `C460`, 128 kbit/s MP2 (classic DAB) |

## Recommended next research (not done in this session)

- [ ] Verify the D1 ensemble EId for the UK (`C18C` is the long-standing observed value; confirm against a current Wohnort / DigitalSpy snapshot at run time).
- [ ] Confirm exact behaviour of Global's RadioVIS slideshow for `Heart UK` — does the SLIDE TEXT contain reliably parseable `ARTIST - TITLE`? Run a 30-min capture with `welle.io --dump-radiodns`.
- [ ] Empirically catalogue which Heart Scotland / Heart North/South Wales SIds are present on which local DAB muxes in 2026; this changes more often than national.
- [ ] Decide between `lrclib.net` (free, community LRC) vs. `Musixmatch` (paid, licensed) for the `LyricsApi` provider — licensing matters before shipping.
- [ ] Decide audio path: feed PCM into ExoPlayer via a custom `MediaSource` vs. write to `AudioTrack` directly. ExoPlayer integrates with `MediaSession` for free; `AudioTrack` gives lower latency. Prototype both.
- [ ] Validate USB DAB stick chipset availability on Duduauto Android 7 (often Realtek RTL2832U-based DVB-T sticks reused as DAB receivers via libusb / `librtlsdr`); confirm `UsbManager` permissions UX in the head-unit shell.

## Clarifying questions

1. **Which USB DAB tuner hardware** is the target? (RTL-SDR + librtlsdr, a Frontier Silicon Verona module, or a vendor-specific Si468x stick?) The `RadioSource` impl differs significantly.
2. **What is the preferred lyrics source / licensing posture?** lrclib.net (free, no auth, LRC) vs. Musixmatch / Genius (commercial keys required).
3. **Is Android Auto / AAOS projection in scope**, or is this a standalone head-unit-only app? (Affects whether to use `MediaLibraryService` instead of `MediaSessionService`.)
4. **Should the app also stream Heart over IP** (Global Player MP3 stream) when the DAB signal is poor, and seamlessly hand over (RadioDNS service-following pattern)?
5. **Target language(s)** — UK English only, or also other EU regions? Affects fonts / text expansion in the in-car layout.

---

## Sources

* [Wikipedia — Heart (radio network)](https://en.wikipedia.org/wiki/Heart_(radio_network))
* [Wikipedia — Digital One](https://en.wikipedia.org/wiki/Digital_One)
* [Wohnort — UK National DAB ensembles (snapshot incl. D1 National 03.01.2026 and London 1)](https://www.wohnort.org/dab/uknat.html)
* [RadioDNS — official site](https://radiodns.org/)
* [RadioDNS — technical documentation](https://radiodns.org/technical/documentation/)
* [ETSI TS 103 270 — RadioDNS Hybrid Radio](https://www.etsi.org/deliver/etsi_ts/103200_103299/103270/)
* [ETSI TS 102 980 — DAB Dynamic Label Plus (DL+)](https://www.etsi.org/deliver/etsi_ts/102900_102999/102980/)
* [Android Developers — Background playback with a MediaSessionService (Media3)](https://developer.android.com/media/media3/session/background-playback)
* [Android Developers — Foreground service types are required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required)

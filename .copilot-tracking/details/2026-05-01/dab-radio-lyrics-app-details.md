<!-- markdownlint-disable-file -->
# Implementation Details: DAB Radio + Live Lyrics Android App (Mekede USB DAB)

## Context Reference

Sources:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md (primary; selected paths)
* .copilot-tracking/research/subagents/2026-04-30/mekede-dab-usb-research.md (hardware + omri-usb API)
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md (Compose + Media3 + Hilt + Room architecture)
* .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md (LRCLIB + LRC parser)
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md (FakeRadioSource + ADB-over-WiFi)
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md (BootReceiver + DUDU pickers)

## Implementation Phase 1: Project scaffolding

<!-- parallelizable: false -->

### Step 1.1: Root Gradle scaffolding

Create the multi-project Gradle build root using AGP 8.5+, Kotlin 2.0.x, the standalone Compose Compiler Gradle plugin, Hilt, and KSP. Use `libs.versions.toml` for centralized version pinning so `:app` and `:omri-usb` agree on Kotlin/AGP/NDK.

Files:
* /workspaces/radio-lyric/settings.gradle.kts - `pluginManagement` (gradlePluginPortal, google, mavenCentral); `dependencyResolutionManagement` (repositories, `versionCatalogs.create("libs") { from(files("gradle/libs.versions.toml")) }`); `include(":app", ":omri-usb")`.
* /workspaces/radio-lyric/build.gradle.kts - top-level `plugins { ... apply false }` for AGP, Kotlin, Compose Compiler, Hilt, KSP.
* /workspaces/radio-lyric/gradle/libs.versions.toml - pin AGP `8.5.2`, Kotlin `2.0.21`, Compose Compiler aligned, Hilt `2.51.1`, KSP `2.0.21-1.0.27`, Media3 `1.4.1`, Room `2.6.1`, Retrofit `2.11.0`, Moshi `1.15.1`, OkHttp `4.12.0`, Coroutines `1.8.1`, AndroidX core/lifecycle/datastore current.
* /workspaces/radio-lyric/gradle.properties - `org.gradle.jvmargs=-Xmx4g`, `android.useAndroidX=true`, `kotlin.code.style=official`, `android.nonTransitiveRClass=true`.
* /workspaces/radio-lyric/.gitmodules - placeholder for the `omri-usb` submodule (populated in Step 2.1).

Discrepancy references:
* None — matches research §"App architecture" exactly.

Success criteria:
* `./gradlew help` succeeds in the devcontainer.
* `./gradlew projects` lists `:app` and `:omri-usb` (the latter is empty until Step 2.1 but the include must already resolve).

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.6 - Single-module justification.

Dependencies:
* Devcontainer image from `.copilot-tracking/plans/2026-05-01/android-kotlin-devcontainer-plan.instructions.md`.

### Step 1.2: `:app` module configuration

Create the `:app` module with the production Android conventions from the research and a `debug` build type that activates the `FakeRadioSource` Hilt binding.

Files:
* /workspaces/radio-lyric/app/build.gradle.kts - `plugins { com.android.application; org.jetbrains.kotlin.android; org.jetbrains.kotlin.plugin.compose; com.google.dagger.hilt.android; com.google.devtools.ksp }`. `android { namespace = "com.example.radiolyric"; compileSdk = 35; defaultConfig { applicationId; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "0.1.0"; ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } }; buildFeatures { compose = true; buildConfig = true }; compileOptions { sourceCompatibility = VERSION_17; targetCompatibility = VERSION_17 }; kotlinOptions { jvmTarget = "17" }; buildTypes { debug { ... }; release { isMinifyEnabled = true; proguardFiles(...) } } }`. Dependencies: `:omri-usb`, Compose BOM, Media3, Hilt + Hilt-Compose, Room + KSP, Retrofit + Moshi + OkHttp, DataStore, Coroutines, Coil-Compose for cover art, JUnit + Truth + Turbine for unit tests.
* /workspaces/radio-lyric/app/proguard-rules.pro - keep rules for Hilt, Room entities, Moshi `@JsonClass(generateAdapter = true)` types, Media3 reflection, and `org.omri.radio.**` (AAR consumers).
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/.gitkeep - establish package root.
* /workspaces/radio-lyric/app/src/test/kotlin/com/example/radiolyric/.gitkeep - JVM tests root.
* /workspaces/radio-lyric/app/src/debug/assets/fixtures/.gitkeep - JSONL DLS replay fixtures (populated in Step 3.2).

Discrepancy references:
* DR-01: heartfm-and-architecture research recommends `targetSdk = 34+`; pinned at 34 here. Re-evaluate when Android 15 stable lands.

Success criteria:
* `./gradlew :app:assembleDebug` reaches the manifest-merger phase without classpath errors (the build will fail later until later phases land — this step only validates the configuration).

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B (architecture).
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Implication for ABI targets".

Dependencies:
* Step 1.1.

### Step 1.3: AndroidManifest + USB device filter

Author the manifest with USB host, foreground-service-media-playback, the Mekede USB filter, the playback service, the media-button receiver, and a placeholder activity. `BootReceiver` is registered here too but its implementation lands in Phase 9.

Files:
* /workspaces/radio-lyric/app/src/main/AndroidManifest.xml - Permissions: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. `<uses-feature android:name="android.hardware.usb.host" />`. `application` with `android:name=".RadioLyricApp"`, `installLocation="internalOnly"`. `MainActivity` with intent-filter `MAIN`/`LAUNCHER` AND `USB_DEVICE_ATTACHED` + `<meta-data ... resource="@xml/mekede_dab_filter" />`. `<service android:name=".playback.PlaybackService" foregroundServiceType="mediaPlayback" exported="true">` with `androidx.media3.session.MediaSessionService` action. `<receiver android:name="androidx.media3.session.MediaButtonReceiver" exported="true">` with `MEDIA_BUTTON` action. `<receiver android:name=".autostart.BootReceiver" ...>` (filters added in Phase 9).
* /workspaces/radio-lyric/app/src/main/res/xml/mekede_dab_filter.xml - `<usb-device vendor-id="5824" product-id="1500" />` (decimal of `0x16C0`/`0x05DC`).

Discrepancy references:
* None.

Success criteria:
* Manifest merger passes during `./gradlew :app:processDebugManifest`.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"USB host integration (manifest)".
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.3 (foreground-service requirements).

Dependencies:
* Step 1.2.

### Step 1.4: Application class + Hilt + Compose theme

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/RadioLyricApp.kt - `@HiltAndroidApp class RadioLyricApp : Application()`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/MainActivity.kt - `@AndroidEntryPoint class MainActivity : ComponentActivity()` setting `setContent { RadioLyricTheme { AppNavigation() } }`. Stub `AppNavigation()` returning a placeholder Composable until Phase 8.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/theme/Theme.kt - `RadioLyricTheme` Composable: dark-first ColorScheme `background = #0A0A0A`, `onBackground = #F2F2F2`, `primary` accent. Day-night override hooked up via `MaterialTheme` + future `SettingsRepository`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/theme/Type.kt - in-car typography (body 18 sp, headline 28–34 sp, lyrics current line 36–44 sp).
* /workspaces/radio-lyric/app/src/main/res/values/strings.xml - `app_name`, channel names, accessibility strings.
* /workspaces/radio-lyric/app/src/main/res/values/themes.xml - `Theme.RadioLyric` parent `Theme.Material3.Dark.NoActionBar`.

Discrepancy references:
* None.

Success criteria:
* `./gradlew :app:assembleDebug` builds (with empty stubs); `./gradlew :app:installDebug` would launch a blank-screen activity on a device.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.8 (in-car UI).

Dependencies:
* Step 1.3.

## Implementation Phase 2: Vendor and build `omri-usb`

<!-- parallelizable: true -->

### Step 2.1: Add omri-usb as Git submodule

Files:
* /workspaces/radio-lyric/.gitmodules - `[submodule "omri-usb"] path = omri-usb url = https://github.com/hradio/omri-usb`.
* /workspaces/radio-lyric/omri-usb/ - submodule populated by `git submodule add https://github.com/hradio/omri-usb.git omri-usb`. Pin to a specific commit (record SHA in `.gitmodules` comment) for reproducibility.

Commands (run in the devcontainer terminal):
```bash
cd /workspaces/radio-lyric
git submodule add https://github.com/hradio/omri-usb.git omri-usb
cd omri-usb && git rev-parse HEAD  # record this SHA in a comment
```

Discrepancy references:
* None.

Success criteria:
* `omri-usb/omriusb/src/main/cpp/raontunerinput.cpp` exists locally.
* `:omri-usb` resolves in `./gradlew projects`.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"OSS Driver Stack: hradio/omri-usb".

Dependencies:
* Step 1.1 (settings.gradle include).

### Step 2.2: Port omri-usb to AGP 8.x / NDK r27

Upstream `omri-usb` was last updated against AGP 3.x. Port it without rewriting source: bump `compileSdk`, add `namespace`, switch to `externalNativeBuild { cmake { ... } }`, set ABI filters, suppress deprecation warnings as warnings (not errors).

Files:
* /workspaces/radio-lyric/omri-usb/build.gradle (top-level) - retain as-is or convert to KTS; ensure `buildscript { dependencies { classpath(libs.android.gradle.plugin) } }` is removed in favor of `pluginManagement` from root.
* /workspaces/radio-lyric/omri-usb/omriusb/build.gradle (or `.kts`) - `plugins { id("com.android.library") }`. `android { namespace = "org.omri"; compileSdk = 35; defaultConfig { minSdk = 24; ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }; externalNativeBuild { cmake { cppFlags("-std=c++17", "-Wno-deprecated-declarations") } } }; externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }; compileOptions { sourceCompatibility = VERSION_17; targetCompatibility = VERSION_17 } }`. Remove any `useLibrary 'org.apache.http.legacy'` if present (deprecated).
* /workspaces/radio-lyric/omri-usb/local.properties - `ndk.dir` not needed; let AGP auto-detect from sdkmanager-installed `ndk;27.x`.

Patches (only if needed; document in the planning log under DD-items):
* If CMake fails on `find_package(Threads)` against NDK r27, add `target_link_libraries(omriusb PRIVATE Threads::Threads)` and `find_package(Threads REQUIRED)` at the top of `omriusb/src/main/cpp/CMakeLists.txt`.
* If Java sources fail under `--release 17`, add `compileOptions { isCoreLibraryDesugaringEnabled = true }` and the `desugar_jdk_libs` dep.

Discrepancy references:
* DD-01: Research says "expect compileSdk / NDK CMake bumps" — this step formalises the bumps. Capture any patches in the planning log.

Success criteria:
* `./gradlew :omri-usb:omriusb:assembleRelease` produces an AAR.
* AAR contains `jni/arm64-v8a/libomriusb.so` and `jni/armeabi-v7a/libomriusb.so`.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Build hradio/omri-usb against current AGP 8.x / NDK r27".
* https://github.com/hradio/omri-usb (upstream sources).

Dependencies:
* Step 2.1.

### Step 2.3: Validate omri-usb AAR API surface

Files:
* /workspaces/radio-lyric/omri-usb/.copilot-build-notes.md - record commit SHA, AGP version, NDK version, applied patches, and `unzip -l` listing of the AAR's `classes.jar` showing `org/omri/radio/RadioImpl.class`, `org/omri/radioservice/RadioServiceListener.class`, `org/omri/radioservice/metadata/TextualDabDynamicLabelPlus.class`.

Commands:
```bash
./gradlew :omri-usb:omriusb:assembleRelease
unzip -l omri-usb/omriusb/build/outputs/aar/omriusb-release.aar | grep -E "classes.jar|libomriusb.so"
unzip -p omri-usb/omriusb/build/outputs/aar/omriusb-release.aar classes.jar | jar tf - | grep -E "org/omri/radio/.*class"
```

Discrepancy references:
* None.

Success criteria:
* `org/omri/radio/RadioImpl.class` and `org/omri/radioservice/RadioServiceListener.class` are present in the AAR.
* Both ABIs ship native libs.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"DL+ event flow (sketch against omri-usb API)".

Dependencies:
* Step 2.2.

## Implementation Phase 3: RadioSource abstraction + FakeRadioSource

<!-- parallelizable: true -->

### Step 3.1: RadioSource sealed interface + domain types

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/RadioSource.kt - `interface RadioSource { val state: StateFlow<RadioState>; val nowPlaying: StateFlow<NowPlaying>; val audio: Flow<ByteArray>; suspend fun open(): Result<Unit>; suspend fun tune(station: Station): Result<Unit>; suspend fun close() }`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/NowPlaying.kt - `data class NowPlaying(val artist: String?, val title: String?, val rawDls: String?, val source: Source, val timestamp: Instant)` where `enum class Source { DLPLUS, DLS, FAKE, NONE }` and `companion object { val Empty = ...; fun fromDls(dls: String): NowPlaying? }` (regex strips `Now playing:` / `on Heart` and splits on ` - `).
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/RadioState.kt - `sealed interface RadioState { data object Idle; data object Tuning; data class Playing(val station: Station); data class Error(val message: String) }`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/Station.kt - `data class Station(val sid: Int, val eid: Int, val frequencyKhz: Int, val label: String)`.

Discrepancy references:
* None.

Success criteria:
* Compiles in isolation; no `omri-usb` import.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §1 (sealed interface pattern).
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.9.1.

Dependencies:
* Step 1.4.

### Step 3.2: FakeRadioSource implementation

Files:
* /workspaces/radio-lyric/app/src/debug/kotlin/com/example/radiolyric/data/radio/FakeRadioSource.kt - implements `RadioSource`. `tune()` launches a coroutine that emits `SAMPLE_TIMELINE` with delays; `audio` flow loops a bundled silent PCM `assets/fixtures/silence-2s.pcm` so the `AudioTrack` pipeline downstream can be exercised. Optional `JsonlScriptedSource(file: File)` overload for replaying captured fixtures.
* /workspaces/radio-lyric/app/src/debug/assets/fixtures/silence-2s.pcm - 2 seconds of S16_LE stereo @ 48 kHz (zeros). Generate with `dd if=/dev/zero of=silence-2s.pcm bs=1 count=$((48000*2*2*2))` during scaffolding.
* /workspaces/radio-lyric/app/src/debug/assets/fixtures/heart-uk-sample.jsonl - one DL+ event per line (artist, title, ts). Seed with two Harry Styles + Dua Lipa entries from the research timeline.

Discrepancy references:
* None.

Success criteria:
* `FakeRadioSource().tune(...)` collects three distinct `NowPlaying` values within ≤ 6 minutes (test runtime in unit test uses virtual time via `kotlinx-coroutines-test`).

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Scenario 3 — Dev Workflow Without the USB Dongle".
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §3 (JSONL replay).

Dependencies:
* Step 3.1.

### Step 3.3: Hilt DI module with debug/release flavor split

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/di/RadioModule.kt - `@Module @InstallIn(SingletonComponent::class) abstract class RadioModule { @Binds @Singleton abstract fun bindRadioSource(impl: RealRadioSourceProvider): RadioSource }`. To avoid two source-set Hilt modules colliding, define a thin `RealRadioSourceProvider` interface and bind it in `src/debug/kotlin/.../di/DebugRadioBindings.kt` to `FakeRadioSource` and in `src/release/kotlin/.../di/ReleaseRadioBindings.kt` to `OmriUsbRadioSource`.
* /workspaces/radio-lyric/app/src/debug/kotlin/com/example/radiolyric/di/DebugRadioBindings.kt - `@Module @InstallIn(SingletonComponent::class) abstract class DebugRadioBindings { @Binds @Singleton abstract fun bind(impl: FakeRadioSource): RealRadioSourceProvider }`.
* /workspaces/radio-lyric/app/src/release/kotlin/com/example/radiolyric/di/ReleaseRadioBindings.kt - same shape, binding `OmriUsbRadioSource`. Stub implementation returns a placeholder `RadioSource` until Phase 4 completes.

Discrepancy references:
* DD-02: Research suggests `BuildConfig.USE_FAKE_RADIO`; the plan uses source-set splits because Hilt validates at compile time and surfaces missing bindings earlier.

Success criteria:
* `./gradlew :app:assembleDebug` (binds `FakeRadioSource`) and `./gradlew :app:assembleRelease` (binds `OmriUsbRadioSource`) both compile.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §1 (Hilt swap pattern).

Dependencies:
* Steps 3.1, 3.2.

### Step 3.4: JVM unit tests for NowPlaying.fromDls

Files:
* /workspaces/radio-lyric/app/src/test/kotlin/com/example/radiolyric/data/radio/NowPlayingTest.kt - test cases: `"Harry Styles - As It Was"`, `"Now playing: Dua Lipa - Houdini on Heart"`, `"Heart UK"` (no song info → `null`), Latin-1 fallback bytes, UTF-8 emoji, mid-string ` - ` (only first split).

Success criteria:
* `./gradlew :app:testDebugUnitTest` passes.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.2 (DLS regex guidance).

Dependencies:
* Step 3.1.

## Implementation Phase 4: OmriUsbRadioSource (real implementation)

<!-- parallelizable: false -->

### Step 4.1: OmriUsbRadioSource implementation

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/OmriUsbRadioSource.kt - `@Singleton class OmriUsbRadioSource @Inject constructor(@ApplicationContext private val ctx: Context) : RadioSource, RealRadioSourceProvider`. Lazily initialises `RadioImpl.getInstance(ctx)`, finds the Mekede tuner among `radio.tuners`, registers a `RadioServiceListener` that maps DL+ tags 1=title, 4=artist into `MutableStateFlow<NowPlaying>` (DLS regex fallback). The `audio: Flow<ByteArray>` reads PCM from `omri-usb`'s callback API on a single-thread `Dispatcher` named `dab-usb-reader`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/UsbDispatchers.kt - `object UsbDispatchers { val Reader: CoroutineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "dab-usb-reader").apply { priority = Thread.NORM_PRIORITY + 1 } }.asCoroutineDispatcher() }`.

Implementation notes:
* DL+ tag IDs: TITLE = 1, ARTIST = 4, ALBUM = 9, STATIONNAME.SHORT = 32 (per research §"OSS Driver Stack").
* Bridge raw frames to a `MutableSharedFlow<DabFrame>(replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)`; expose `nowPlaying` as `.distinctUntilChanged()` after a 2 s `debounce` to absorb DL+ flapping.
* Audio path is independent of metadata path — never share buffers.

Discrepancy references:
* DR-02: Research §B.7 recommends a dedicated dispatcher (not `Dispatchers.IO`) — implemented here.

Success criteria:
* `:app` compiles in `release` flavor with the binding from Step 3.3.
* On a real Mekede device, `nowPlaying.value` updates after `tune()` completes (verified manually in Phase 10.3).

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Scenario 1" (full code skeleton).
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.7 (threading).
* .copilot-tracking/research/subagents/2026-04-30/mekede-dab-usb-research.md (DL+ tag map).

Dependencies:
* Step 2.3 (omri-usb AAR available), Step 3.3 (binding hook).

### Step 4.2: USB permission flow

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/usb/UsbPermissionGateway.kt - encapsulates: `snapshot()` reads `usbManager.deviceList`, filters by `vendorId == 0x16C0 && productId == 0x05DC`. `requestPermission(device)` builds a `PendingIntent.getBroadcast(... FLAG_IMMUTABLE)` and calls `usbManager.requestPermission`. Receiver is registered with `Context.RECEIVER_NOT_EXPORTED` (API 33+) or default flags below. Exposes a `Flow<UsbDevice?>` of granted devices.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/MainActivity.kt - on `onResume`, calls `UsbPermissionGateway.snapshot()`; on `USB_DEVICE_ATTACHED` intent, request permission; on grant, `ContextCompat.startForegroundService(Intent(this, PlaybackService::class.java))`.

Discrepancy references:
* None.

Success criteria:
* Cold-boot path: device already attached → app finds it via `snapshot()`.
* Hot-plug path: device attached after launch → `USB_DEVICE_ATTACHED` intent + permission dialog → service starts.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/usb-dab-integration-research.md §4.3 + §4.4 (Android 12+ flag and receiver-export requirements).
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"USB race".

Dependencies:
* Step 4.1.

### Step 4.3: Default Heart UK station + tune flow

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/Stations.kt - `object Stations { val HeartUK = Station(sid = 0xCFD1, eid = 0xC18C, frequencyKhz = 222_064, label = "Heart UK") }` plus a small list of D1 Heart variants (Heart 70s/80s/90s/00s/Dance) seeded into Room on first run.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/radio/OmriUsbRadioSource.kt - `tune()` calls `tuner.startRadioServiceScan()`, awaits, then `tuner.tune(service)` on the matching `RadioService`. Audio playback path is started before the DL+ subscription so silence isn't blocking on metadata.

Discrepancy references:
* DR-03: Research §A.1 confirms SId `0xCFD1` / EId `0xC18C` for D1 National. Recorded; revisit if D1 mux ID drifts.

Success criteria:
* On real hardware (Phase 10.3), Heart UK plays within 10 s of `tune(Stations.HeartUK)`.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.1.

Dependencies:
* Steps 4.1, 4.2.

## Implementation Phase 5: Media3 PlaybackService

<!-- parallelizable: false -->

### Step 5.1: PlaybackService MediaSessionService

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/PlaybackService.kt - `@AndroidEntryPoint class PlaybackService : MediaSessionService()`. `@Inject lateinit var radioSource: RadioSource`. In `onCreate()`: build `MediaSession.Builder(this, exoPlayer).setId("radio-lyric-session").build()`. Start a `mediaPlayback` foreground notification (channel `playback`, `IMPORTANCE_LOW`) within 5 s of `onStartCommand`. Pump `radioSource.audio` into `AudioTrack` (S16_LE stereo @ 48 kHz, `MODE_STREAM`) on `UsbDispatchers.Reader`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/PlaybackNotification.kt - `NotificationChannel("playback", "DAB playback", IMPORTANCE_LOW)`. `MediaStyleNotificationHelper.MediaStyle(mediaSession)` build (Media3 helper).
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/AudioPump.kt - thin wrapper around `AudioTrack` that exposes `suspend fun write(frames: ByteArray)` honouring blocking semantics; close on cancellation.

Implementation notes:
* If `omri-usb` already exposes a Media3 `MediaSource` integration through its `Player` API, prefer that and drop the AudioTrack pump. Investigate during implementation; record the decision in the planning log.

Discrepancy references:
* None.

Success criteria:
* Service starts foreground within 5 s of `onStartCommand` (no `ForegroundServiceDidNotStartInTimeException`).
* Audio plays through device speakers in the debug flavor (silence) and on real hardware (Heart UK).

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.2, §B.3.
* https://developer.android.com/media/media3/session/background-playback.

Dependencies:
* Step 4.3.

### Step 5.2: NowPlaying → MediaSession metadata

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/PlaybackService.kt (extended) - `serviceScope.launch { radioSource.nowPlaying.distinctUntilChangedBy { it.artist to it.title }.debounce(2_000).collect { np -> mediaSession.player.setMediaItem(MediaItem.Builder().setMediaMetadata(MediaMetadata.Builder().setArtist(np.artist).setTitle(np.title).setAlbumTitle(np.source.name).build()).build()) } }`.

Discrepancy references:
* None.

Success criteria:
* Lock-screen / status-bar media controls show live `artist` + `title`.
* Steering-wheel `MEDIA_PLAY_PAUSE` toggles audio (via `MediaButtonReceiver` + Media3 default mapping).

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.7 (debounce + conflate guidance).

Dependencies:
* Step 5.1.

### Step 5.3: MediaController binding from UI

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/MediaControllerProvider.kt - `@Singleton class MediaControllerProvider @Inject constructor(@ApplicationContext private val ctx: Context)`. `suspend fun controller(): MediaController` builds `MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync().await()`. Caches one instance.

Discrepancy references:
* None.

Success criteria:
* From a Compose ViewModel, `mediaControllerProvider.controller().play()` resumes audio.

Context references:
* https://developer.android.com/media/media3/session/background-playback#mediacontroller.

Dependencies:
* Step 5.2.

## Implementation Phase 6: Persistence layer (Room + DataStore)

<!-- parallelizable: true -->

### Step 6.1: Room database

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/RadioDatabase.kt - `@Database(entities = [StationEntity::class, LastTunedEntity::class, LyricsCacheEntity::class], version = 1) abstract class RadioDatabase : RoomDatabase() { abstract fun stationDao(): StationDao; abstract fun lastTunedDao(): LastTunedDao; abstract fun lyricsCacheDao(): LyricsCacheDao }`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/StationEntity.kt - `@Entity(tableName = "stations") data class StationEntity(@PrimaryKey val sid: Int, val eid: Int, val frequencyKhz: Int, val label: String, val pinned: Boolean = false, val lastSeenRssi: Int? = null)`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/LastTunedEntity.kt - `@Entity(tableName = "last_tuned") data class LastTunedEntity(@PrimaryKey val id: Int = 0, val stationSid: Int, val volume: Int)`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/LyricsCacheEntity.kt - `@Entity(tableName = "lyrics_cache", primaryKeys = ["artist", "title"], indices = [Index(value = ["artist", "title"], unique = true)]) data class LyricsCacheEntity(val artist: String, val title: String, val syncedLyrics: String?, val plainLyrics: String?, val provider: String, val fetchedAt: Long)` with `COLLATE NOCASE` enforced via `@ColumnInfo(collate = ColumnInfo.NOCASE)` on `artist` + `title`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/Daos.kt - `StationDao`, `LastTunedDao`, `LyricsCacheDao` with the queries the repositories need.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/di/DatabaseModule.kt - Hilt provider for `RadioDatabase` and DAOs.

Discrepancy references:
* None.

Success criteria:
* Room compiler runs cleanly (no schema warnings).
* `./gradlew :app:assembleDebug` succeeds.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.5.

Dependencies:
* Step 1.4.

### Step 6.2: Lyrics cache eviction policy

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/LyricsCachePolicy.kt - `@Singleton class LyricsCachePolicy @Inject constructor(private val dao: LyricsCacheDao)`. `suspend fun evict()`: delete rows where `fetchedAt < now - 30.days`, then trim by `id` to leave 2000 rows. Called from `RadioLyricApp.onCreate { applicationScope.launch { policy.evict() } }`.

Success criteria:
* Manual unit test inserts 2100 rows then asserts ≤ 2000 after `evict()`.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.5.

Dependencies:
* Step 6.1.

### Step 6.3: DataStore preferences

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/local/SettingsRepository.kt - wraps `Context.dataStore` (`name = "settings"`). Exposes `Flow<AppSettings>` and `suspend fun update(...)`. Keys: `theme` (`SYSTEM`/`DARK`/`LIGHT`), `font_scale` (Float), `default_station_sid` (Int), `keep_screen_on_lyrics` (Boolean).
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/di/SettingsModule.kt - Hilt provider.

Success criteria:
* Settings round-trip via `update()` then `Flow` collection.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.5.

Dependencies:
* Step 1.4.

## Implementation Phase 7: Lyrics layer (LRCLIB + LRC parser)

<!-- parallelizable: true -->

### Step 7.1: LrcLibApi Retrofit client

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/lyrics/LrcLibApi.kt - Retrofit interface with `@GET("api/search") suspend fun search(@Query("track_name") track: String, @Query("artist_name") artist: String): List<LrcLibTrack>` and `@GET("api/get/{id}") suspend fun getById(@Path("id") id: Long): LrcLibTrack`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/lyrics/LrcLibTrack.kt - `@JsonClass(generateAdapter = true) data class LrcLibTrack(val id: Long, val trackName: String, val artistName: String, val albumName: String?, val duration: Double?, val instrumental: Boolean = false, val plainLyrics: String?, val syncedLyrics: String?)`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/di/NetworkModule.kt - Hilt provider for `OkHttpClient` (User-Agent interceptor `RadioLyric/0.1 (+https://github.com/<user>/radio-lyric)`, `callTimeout(3, SECONDS)`), `Retrofit` (base URL `https://lrclib.net/`, MoshiConverterFactory), `LrcLibApi`.

Discrepancy references:
* None.

Success criteria:
* Integration test against the live API for a known UK pop track returns `syncedLyrics` non-null.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md §3.3.

Dependencies:
* Step 1.2.

### Step 7.2: LyricsRepository

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/lyrics/Lyrics.kt - `sealed interface Lyrics { data object None : Lyrics; data class Plain(val text: String) : Lyrics; data class Synced(val lines: List<LyricLine>) : Lyrics }`. `data class LyricLine(val timeMs: Long, val text: String)`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/lyrics/LyricsRepository.kt - `@Singleton class LyricsRepository @Inject constructor(private val api: LrcLibApi, private val cache: LyricsCacheDao, private val parser: LrcParser)`. `suspend fun lookup(artist: String, title: String): Lyrics`: cache hit → return parsed; else `runCatching { withTimeout(3.seconds) { api.search(title, artist).firstOrNull() } }.getOrNull()` → on success cache + return; on failure return `Lyrics.None`. NEVER throws upward.

Discrepancy references:
* DD-03: Research mentions a Happi.dev fallback as a footnote; the plan rejects it (no key, free-only). Recorded for completeness.

Success criteria:
* Unit test: simulated timeout returns `Lyrics.None` without throwing.
* Unit test: cached entry returns without API call.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Scenario 2" (full skeleton).

Dependencies:
* Steps 6.1, 7.1.

### Step 7.3: LRC parser

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/data/lyrics/LrcParser.kt - `class LrcParser @Inject constructor() { fun parse(lrc: String): List<LyricLine> }`. Implementation per research §3.4 (regex `\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]`, multi-timestamp fan-out, sort ascending).
* /workspaces/radio-lyric/app/src/test/kotlin/com/example/radiolyric/data/lyrics/LrcParserTest.kt - cases: empty input, single-timestamp line, multi-timestamp line, `.xx` vs `.xxx` fractions, leading metadata (`[ar:Harry Styles]`) ignored, blank lyric line emits `LyricLine(time, "")`.

Discrepancy references:
* None.

Success criteria:
* `./gradlew :app:testDebugUnitTest` passes the new parser tests.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md §3.4.

Dependencies:
* None (pure JVM).

## Implementation Phase 8: Compose UI + ViewModels

<!-- parallelizable: false -->

### Step 8.1: ViewModels

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/presentation/NowPlayingViewModel.kt - `@HiltViewModel class NowPlayingViewModel @Inject constructor(private val radioSource: RadioSource, private val controllerProvider: MediaControllerProvider) : ViewModel()`. Exposes `uiState: StateFlow<NowPlayingUiState>` collected from `radioSource.nowPlaying.conflate()`. Public `tune(station: Station)`, `play()`, `pause()` delegate to `MediaController`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/presentation/LyricsViewModel.kt - `@HiltViewModel class LyricsViewModel @Inject constructor(private val radioSource: RadioSource, private val lyricsRepo: LyricsRepository) : ViewModel()`. `uiState: StateFlow<LyricsUiState>` reacts to `radioSource.nowPlaying.distinctUntilChangedBy { it.artist to it.title }` → `Loading` → `lyricsRepo.lookup(...)` → `Synced | Plain | None`. `currentPositionMs: StateFlow<Long>` ticks every 200 ms while playing for the active-line highlight.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/presentation/StationPickerViewModel.kt - `@HiltViewModel class StationPickerViewModel @Inject constructor(private val stationDao: StationDao, private val settings: SettingsRepository) : ViewModel()`. Exposes scanned + pinned stations, `setDefault(sid: Int)`.

Discrepancy references:
* None.

Success criteria:
* ViewModel unit tests: feed scripted `NowPlaying` values, assert `LyricsUiState` transitions through `Loading → Synced` and `Loading → None` on simulated timeout.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.1 (layered architecture).

Dependencies:
* Steps 5.3, 6.3, 7.2.

### Step 8.2: Compose screens

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/NowPlayingScreen.kt - large artist + title (headline 28–34 sp), station label, album art (Coil) when MOT slideshow is available, play/pause + station-picker buttons (56 dp).
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/LyricsScreen.kt - `LazyColumn` with `rememberLazyListState()` and `LaunchedEffect(activeIndex) { state.animateScrollToItem(activeIndex) }`. Active index computed via binary search on `LyricLine.timeMs` against `currentPositionMs`. Current line styled headline 36–44 sp + accent color; other lines body 18 sp + onSurfaceVariant. `Modifier.keepScreenOn` gated by `SettingsRepository.keep_screen_on_lyrics`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/StationPickerScreen.kt - list of pinned + scanned stations; tap tunes via `NowPlayingViewModel.tune(...)`; long-press toggles default.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/components/InCarTouchTarget.kt - composable wrapper enforcing `Modifier.heightIn(min = 56.dp).padding(horizontal = 24.dp)`.

Discrepancy references:
* None.

Success criteria:
* Compose `@Preview`s render in Android Studio without runtime crashes.
* Smoke-tested in Phase 10.2 on the emulator.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.8.
* .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md §3.4 (`LyricsView` composable).

Dependencies:
* Step 8.1.

### Step 8.3: Navigation

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/ui/AppNavigation.kt - `NavHost(startDestination = "now_playing")` with three destinations. Single-activity, no nesting deeper than one level. `keepScreenOn` Modifier applied conditionally on the lyrics destination.

Discrepancy references:
* None.

Success criteria:
* App launches into Now Playing; bottom nav (or top tabs — choose one in implementation) switches between the three screens with no animation longer than 200 ms.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.8.

Dependencies:
* Step 8.2.

## Implementation Phase 9: Autostart on Duduauto

<!-- parallelizable: true -->

### Step 9.1: BootReceiver

Files:
* /workspaces/radio-lyric/app/src/main/AndroidManifest.xml (extended) - inside the existing `<receiver android:name=".autostart.BootReceiver" ...>`, add intent-filter actions for AOSP (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`), MediaTek (`QUICKBOOT_POWERON`, `BOOT_IPO`, `com.htc.intent.action.QUICKBOOT_POWERON`), and confirmed OEM ACC variants (`com.fyt.boot.ACCON`, `com.glsx.boot.ACCON`, `com.cayboy.action.ACC_ON`, `com.carboy.action.ACC_ON`, `com.microntek.startApp`). Defensive: also include `android.intent.action.ACC_ON`, `com.android.action.ACC_ON`, `com.xy.power.ACC_ON`, `com.mtcd.action.ACC_ON`, `com.mtce.action.ACC_ON`, `com.wits.action.ACC_ON` (folklore — recorded as DR-04). Set `android:exported="true"` and `android:permission="android.permission.RECEIVE_BOOT_COMPLETED"`.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/autostart/BootReceiver.kt - `class BootReceiver : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { Log.i("BootReceiver", "received ${intent.action}"); ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).putExtra("trigger", intent.action)) } }`.

Discrepancy references:
* DR-04: Folklore OEM ACC intents included defensively per autostart research §"Vendor ACC intents".

Success criteria:
* `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.radiolyric` triggers `PlaybackService.onCreate`.

Context references:
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Implementation Patterns".

Dependencies:
* Phase 5 complete (PlaybackService exists).

### Step 9.2: PlaybackService runtime hooks

Files:
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/PlaybackService.kt (extended) - in `onStartCommand`: 1) snapshot `usbManager.deviceList.values.firstOrNull(::isMekedeDab)` and tune if found; 2) `registerReceiver(usbAttachReceiver, IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, RECEIVER_NOT_EXPORTED)`; 3) `registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))` (cannot be manifest-declared); 4) `handler.postDelayed({ pollDeviceList() }, 12_000L)` to absorb the Duduauto deep-sleep USB-power-cut window.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/UsbAttachReceiver.kt - inner receiver class.
* /workspaces/radio-lyric/app/src/main/kotlin/com/example/radiolyric/playback/ScreenOnReceiver.kt - inner receiver class. On `ACTION_SCREEN_ON`, ensure `radioSource.state` is not `Idle`; if so, re-tune the default station.

Discrepancy references:
* None.

Success criteria:
* Manual ignition cycle on the Duduauto results in audio resuming within ~12 s of the screen coming up (Phase 10.3).

Context references:
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Implementation Patterns" + §"USB race".

Dependencies:
* Steps 5.1, 9.1.

### Step 9.3: README user-facing setup

Files:
* /workspaces/radio-lyric/README.md - new top-level README with sections: 1. Hardware (Mekede DUDU7 + Mekede USB DAB dongle), 2. Build (devcontainer, `./gradlew :app:assembleRelease`), 3. Install (`adb connect <car-ip>:5555`, `adb install -r -t app/build/outputs/apk/release/app-release.apk`, open the app once), 4. Autostart setup (Scenario A — `Settings → Canbus → Startup Settings → Select app to auto start on bootup`; Scenario B fallback — `Settings → More features → Tasks` Vehicle Ignition + 3 s delay), 5. Optional ADB hardening (`adb shell dumpsys deviceidle whitelist +com.example.radiolyric`), 6. Verification commands (`adb shell am broadcast` set from autostart research §"Configuration Examples"), 7. Troubleshooting (USB permission, LRCLIB outage, DL+ missing — fall back to DLS regex).

Discrepancy references:
* None.

Success criteria:
* README lints clean (mega-linter rules) and walks a new user from install to autostart-verified.

Context references:
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Recommendation".

Dependencies:
* Step 9.2.

## Implementation Phase 10: Validation

<!-- parallelizable: false -->

### Step 10.1: Full project build

Validation commands:
* `./gradlew :omri-usb:omriusb:assembleRelease` — vendor AAR.
* `./gradlew :app:lintDebug` — Android lint.
* `./gradlew :app:assembleDebug :app:assembleRelease` — both flavors.
* `./gradlew :app:testDebugUnitTest` — JVM unit tests.

Success criteria:
* All four invocations exit 0.
* Lint baseline empty or only allowed warnings recorded in `app/lint-baseline.xml`.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md (whole-project gating).

Dependencies:
* Phases 1–9 complete.

### Step 10.2: Emulator smoke test (debug flavor)

Run inside the emulator overlay devcontainer (per the devcontainer plan §Phase 2).

Steps:
1. Boot emulator: `emulator -avd radiolyric_debug -no-window -no-audio &`.
2. `adb wait-for-device`.
3. `./gradlew :app:installDebug` then `adb shell am start -n com.example.radiolyric/.MainActivity`.
4. Verify in `adb logcat -s RadioLyric:V` that `FakeRadioSource` emits `Harry Styles - As It Was` within 1 s.
5. If the emulator has internet, verify `LyricsRepository.lookup("Harry Styles", "As It Was")` returns `Synced` and the lyrics screen scrolls.
6. If offline, verify `Lyrics.None` is rendered without crash.

Success criteria:
* App stays alive for ≥ 5 minutes without crash.
* `FakeRadioSource` advances through ≥ 2 timeline entries.
* Lyrics screen never throws, regardless of network state.

Context references:
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §4.

Dependencies:
* Step 10.1.

### Step 10.3: In-car deploy + autostart verification (deferred to user)

Document, do not execute (requires the Duduauto in the car):

```bash
HEADUNIT=192.168.1.42:5555
adb connect "$HEADUNIT"
adb -s "$HEADUNIT" install -r -t -d app/build/outputs/apk/release/app-release.apk
adb -s "$HEADUNIT" shell am force-stop com.example.radiolyric
adb -s "$HEADUNIT" shell am start -n com.example.radiolyric/.MainActivity
adb -s "$HEADUNIT" logcat -v time -s "RadioLyric:V" "AndroidRuntime:E"
```

Then on the head unit:
1. `Settings → Canbus → Startup Settings → Select app to auto start on bootup` → pick Radio-Lyric.
2. Cycle ignition off and on; confirm app launches within ~3 s of screen on.
3. Simulate broadcasts:
   ```bash
   adb -s "$HEADUNIT" shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.radiolyric
   adb -s "$HEADUNIT" shell am broadcast -a com.fyt.boot.ACCON -p com.example.radiolyric
   adb -s "$HEADUNIT" shell am broadcast -a android.intent.action.QUICKBOOT_POWERON -p com.example.radiolyric
   ```
4. Confirm Heart UK plays and DL+ artist+title appear in the notification.

Success criteria:
* Manual checklist completed by user; outcomes recorded in the changes file.

Context references:
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Configuration Examples".
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §5.

Dependencies:
* Steps 9.3, 10.1.

### Step 10.4: Report blocking issues

When validation surfaces blockers larger than minor fixes, do NOT attempt large-scale refactors inline. Record the issue and recommend follow-on planning:

* `omri-usb` AAR fails to build on AGP 8 / NDK r27 → record patches attempted in `omri-usb/.copilot-build-notes.md` and request a follow-on research task to fork + modernise upstream.
* USB permission dialog never appears on stock DUDU firmware → recommend a research task on per-firmware USB host quirks (forum.dudu-auto.com search).
* Heart UK SId drifts (mux re-arrangement) → recommend a follow-on task to add a station-rescan flow.
* LRCLIB sustained outage → recommend a research task on an alternative free synced-lyrics source (NetEase, Megalobiz) and revisit Scenario 2.

Success criteria:
* Issues catalogued with file paths and recommended next steps; no inline rewrite of completed phases.

Context references:
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Potential Next Research".

Dependencies:
* Steps 10.1–10.3.

## Dependencies

* Devcontainer toolchain (`.copilot-tracking/plans/2026-05-01/android-kotlin-devcontainer-plan.instructions.md`).
* NDK r27 installed via `sdkmanager "ndk;27.0.12077973"` (or the latest r27).
* Outbound HTTPS to `dl.google.com`, `repo.maven.apache.org`, `lrclib.net`, `github.com/hradio/omri-usb`.

## Success Criteria

* `./gradlew :app:assembleRelease :app:testDebugUnitTest` exits 0 in the devcontainer.
* Debug-flavor APK runs end-to-end on the emulator with `FakeRadioSource` driving the UI and lyrics pipeline.
* Release-flavor APK installs on the Duduauto, the Mekede dongle is recognised by the manifest filter, audio plays, DL+ artist+title surfaces, lyrics render when LRCLIB has a hit, and the app autostarts on ignition after the user picks it in `Canbus → Startup Settings`.

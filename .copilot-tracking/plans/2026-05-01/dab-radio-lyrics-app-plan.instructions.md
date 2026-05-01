---
applyTo: '.copilot-tracking/changes/2026-05-01/dab-radio-lyrics-app-changes.md'
---
<!-- markdownlint-disable-file -->
# Implementation Plan: DAB Radio + Live Lyrics Android App (Mekede USB DAB)

## Overview

Build the `radio-lyric` Android head-unit app: a single-Gradle-module Kotlin + Jetpack Compose app that drives a Mekede USB DAB+ dongle through a vendored `hradio/omri-usb` AAR, plays HE-AAC v2 audio via Media3 `MediaSessionService`, extracts DLS / DL+ artist+title metadata, fetches synced lyrics from LRCLIB (with Room cache), exposes a Now Playing / Lyrics / Stations Compose UI, supports a hardware-free dev loop via a `FakeRadioSource`, and autostarts on Duduauto 7 ignition through the DUDU Settings picker plus a defensive manifest `BootReceiver`.

## Objectives

### User Requirements

* Stream audio from the Mekede DAB+ USB dongle and tune Heart UK by default — Source: `.copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md` task brief.
* Extract now-playing artist + title from the DAB+ stream (DLS / DL+) and render synced lyrics — Source: same brief.
* Use only free, no-key lyrics services; never let lyrics failure stop audio — Source: same brief, Scenario 2.
* Allow development and debugging without the USB dongle attached — Source: same brief, Scenario 3 + `.copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md`.
* Autostart the app on Duduauto 7 power-on without a manual tap — Source: `.copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md` task brief.
* Devcontainer toolchain already in place — Source: completed plan `.copilot-tracking/plans/2026-05-01/android-kotlin-devcontainer-plan.instructions.md`.

### Derived Objectives

* Vendor `hradio/omri-usb` as a `:omri-usb` Gradle subproject (LGPL-2.1 dynamic-link compliance) — Derived from: license analysis in research §"OSS Driver Stack".
* Build the app as a single `:app` module organised by package, not multi-module — Derived from: scope sizing in heartfm-and-architecture research §B.6.
* Treat lyrics as best-effort: ≤ 3 s timeout, no toasts, never blocks audio — Derived from: research Scenario 2 requirements.
* Ship a Hilt `debug` flavor that binds `FakeRadioSource` so the emulator and a phone iterate the full UI without USB host — Derived from: emulator-USB-passthrough impossibility in debug-without-hardware research §4.
* Default `targetSdk = 34`, `minSdk = 24` to cover Duduauto's Android 12/13 reality and any future Android 7 head units — Derived from: heartfm-and-architecture research §B.
* Ship `arm64-v8a` and `armeabi-v7a` ABIs for the native `omri-usb` libraries — Derived from: UIS7870 ABI guidance in research §"Target Head Unit".
* Layer autostart as a separate phase that depends on the foreground service from the playback phase — Derived from: autostart research Scenarios A + B.

## Context Summary

### Project Files

* /workspaces/radio-lyric — Greenfield workspace; only `.devcontainer/` and `.copilot-tracking/` exist. No Gradle project yet.
* /workspaces/radio-lyric/.devcontainer/ — JDK 21 + Android SDK 35 toolchain ready (devcontainer plan completed); host the build inside this container.

### References

* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md — Primary research; selects omri-usb + LRCLIB + FakeRadioSource paths and pins Heart UK SId/EId/freq.
* .copilot-tracking/research/subagents/2026-04-30/mekede-dab-usb-research.md — Mekede dongle hardware (VID `0x16C0`, PID `0x05DC`, Raon RTV protocol) and `omri-usb` API surface.
* .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md — Layered Compose + Media3 + Hilt + Room architecture, threading model, in-car UI guidance, full Manifest skeleton.
* .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md — LRCLIB API contract, sample Retrofit client, LRC parser + Compose `LyricsView`.
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md — `RadioSource` sealed-interface pattern, JSONL DLS replay, ADB-over-WiFi workflow, emulator USB-host limits.
* .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md — DUDU Settings picker (Scenario A) + manifest `BootReceiver` + foreground-service rules + USB-attach race mitigation.
* .copilot-tracking/research/subagents/2026-05-01/headunit-autostart-research.md — Primary-source OEM ACC intent table and ADB verification commands.
* https://github.com/hradio/omri-usb — Upstream LGPL-2.1 source to vendor as `:omri-usb` subproject.
* https://lrclib.net/docs — LRCLIB API contract.
* https://developer.android.com/media/media3/session/background-playback — Media3 `MediaSessionService` reference.
* https://developer.android.com/about/versions/14/changes/fgs-types-required — `mediaPlayback` foreground-service-type rules.

### Standards References

* .github/copilot-instructions.md — Not present in repo; defer to inline conventions in the research docs.
* LGPL-2.1 (omri-usb) — Keep `:omri-usb` as a separate Gradle subproject and dynamically link to satisfy the LGPL clause.

## Implementation Checklist

### [x] Implementation Phase 1: Project scaffolding

<!-- parallelizable: false -->

* [x] Step 1.1: Create root Gradle settings (`settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`) with AGP 8.5+, Kotlin 2.0.x, Compose Compiler Gradle plugin, Hilt, KSP
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 9-44)
* [x] Step 1.2: Create `:app` module with `build.gradle.kts` (`minSdk=24`, `targetSdk=34`, `compileSdk=35`, `arm64-v8a`+`armeabi-v7a` ABI splits, Hilt+KSP plugins, debug/release build types), `proguard-rules.pro`, and package skeleton under `com.example.radiolyric`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 46-86)
* [x] Step 1.3: Author `app/src/main/AndroidManifest.xml` with USB host feature, foreground-service permissions, USB device filter `res/xml/mekede_dab_filter.xml` (`16C0`/`05DC`), `MainActivity`, `PlaybackService`, `MediaButtonReceiver`, and (deferred to Phase 9) `BootReceiver` placeholder
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 88-128)
* [x] Step 1.4: Set up Hilt entrypoints (`RadioLyricApp : Application` with `@HiltAndroidApp`, `MainActivity` with `@AndroidEntryPoint`, base Compose theme + in-car typography overrides)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 130-160)

### [ ] Implementation Phase 2: Vendor and build `omri-usb`

<!-- parallelizable: true -->

* [ ] Step 2.1: Add `omri-usb` as a Git submodule at `omri-usb/` (LGPL-2.1) and include it as `:omri-usb` in `settings.gradle.kts`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 162-184)
* [ ] Step 2.2: Port the vendored `omri-usb` `build.gradle` to AGP 8.x / NDK r27 (`compileSdk=35`, `minSdk=24`, AGP namespace, `externalNativeBuild { cmake { ... } }` arguments, ABI filters `arm64-v8a`+`armeabi-v7a`, suppress deprecated APIs as warnings)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 186-228)
* [ ] Step 2.3: Validate the AAR builds and exposes `org.omri.radio.*` API by running `./gradlew :omri-usb:assembleRelease` and inspecting the AAR `classes.jar` + `jni/` ABI folders
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 230-249)

### [ ] Implementation Phase 3: RadioSource abstraction + FakeRadioSource

<!-- parallelizable: true -->

* [ ] Step 3.1: Define `data/radio/RadioSource.kt` sealed interface, `NowPlaying`, `RadioState`, `Station`, `AudioFrame` types in pure Kotlin (no `omri-usb` import)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 251-285)
* [ ] Step 3.2: Implement `data/radio/FakeRadioSource.kt` that loops a bundled silent PCM sample and emits a scripted `NowPlaying` timeline (Harry Styles → Dua Lipa → ...) with optional JSONL replay loader for fixtures under `app/src/debug/assets/fixtures/`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 287-323)
* [ ] Step 3.3: Add Hilt module `di/RadioModule.kt` with `debug` flavor variant binding `FakeRadioSource` and `release` variant binding `OmriUsbRadioSource` (placeholder until Phase 4)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 325-352)
* [ ] Step 3.4: Add JVM unit tests for `NowPlaying.fromDls()` (regex `Artist - Title`, `Now playing: ... on Heart` prefix stripping, UTF-8 vs Latin-1 fallback)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 354-378)

### [ ] Implementation Phase 4: OmriUsbRadioSource (real implementation)

<!-- parallelizable: false -->

* [ ] Step 4.1: Implement `data/radio/OmriUsbRadioSource.kt` against `org.omri.radio.RadioImpl` — initialise tuner, expose `nowPlaying: StateFlow<NowPlaying>` from `RadioServiceListener.serviceDynamicLabelPlusReceived` (DL+ tags 1=title, 4=artist) with DLS regex fallback; expose `audio: Flow<ByteArray>` via `callbackFlow` reading PCM frames on a dedicated single-thread dispatcher named `dab-usb-reader`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 380-432)
* [ ] Step 4.2: Implement USB permission flow in `MainActivity` (or a `UsbPermissionGateway` helper): handle `USB_DEVICE_ATTACHED` intent, snapshot `UsbManager.deviceList` on resume to cover already-attached devices, request permission with `FLAG_IMMUTABLE` PendingIntent, register receiver with `RECEIVER_NOT_EXPORTED`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 434-471)
* [ ] Step 4.3: Hard-code Heart UK (SId `0xCFD1`, EId `0xC18C`, Block 11D / 222.064 MHz) as the default tile; wire `tune()` to `omri-usb`'s `Tuner.startRadioServiceScan` then `tuner.tune(service)`; ensure audio playback starts even when DL+ never arrives
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 473-505)

### [ ] Implementation Phase 5: Media3 PlaybackService

<!-- parallelizable: false -->

* [ ] Step 5.1: Implement `playback/PlaybackService : MediaSessionService` that owns the `RadioSource` lifecycle, builds a Media3 `MediaSession`, publishes a `mediaPlayback` foreground notification (channel `playback`, `IMPORTANCE_LOW`), and pumps `RadioSource.audio` into `AudioTrack` (or an ExoPlayer custom `MediaSource` if simpler) on a dedicated thread
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 507-562)
* [ ] Step 5.2: Map `RadioSource.nowPlaying` updates to `MediaSession.setMediaItem(MediaItem.Builder().setMediaMetadata(...))` so steering-wheel keys, lock-screen, and Android Auto see live artist + title; debounce updates by 2 s and `distinctUntilChanged` to absorb DL+ flapping
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 564-595)
* [ ] Step 5.3: Wire `MediaController` from the UI layer with `SessionToken(context, ComponentName(...))` and `ListenableFuture.await()`; expose play/pause/next-station via `MediaController` to keep Compose decoupled from the service
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 597-625)

### [ ] Implementation Phase 6: Persistence layer (Room + DataStore)

<!-- parallelizable: true -->

* [ ] Step 6.1: Define Room database `radio.db` v1 with entities `StationEntity`, `LastTunedEntity`, `LyricsCacheEntity`, indices on `(artist COLLATE NOCASE, title COLLATE NOCASE)`; DAOs for each; Hilt `DatabaseModule` provides singleton
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 627-678)
* [ ] Step 6.2: Add `LyricsCachePolicy` (TTL 30 days, LRU cap 2000 rows, eviction in a coroutine on app start)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 680-705)
* [ ] Step 6.3: Add `androidx.datastore.preferences` for `theme`, `font_scale`, `day_night_override`, `default_station_sid`; expose via `SettingsRepository`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 707-733)

### [ ] Implementation Phase 7: Lyrics layer (LRCLIB + LRC parser)

<!-- parallelizable: true -->

* [ ] Step 7.1: Implement `data/lyrics/LrcLibApi.kt` (Retrofit + Moshi) with `search(track, artist)` and `getById(id)` endpoints; OkHttp client adds `User-Agent: RadioLyric/0.1 (+https://github.com/<user>/radio-lyric)` and 3 s call timeout
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 735-775)
* [ ] Step 7.2: Implement `LyricsRepository.lookup(artist, title): Lyrics` — Room cache hit first, then API call wrapped in `withTimeout(3.seconds) + runCatching`, then `Lyrics.None` on any failure (no toast, no exception bubbling)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 777-810)
* [ ] Step 7.3: Implement `LrcParser.parse(lrc: String): List<LyricLine>` (handle `[mm:ss.xx]` and `[mm:ss.xxx]`, multiple timestamps per line, sort ascending) with JVM unit tests covering Heart-UK-style payloads
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 812-845)

### [ ] Implementation Phase 8: Compose UI + ViewModels

<!-- parallelizable: false -->

* [ ] Step 8.1: Implement `presentation/NowPlayingViewModel` and `LyricsViewModel` — ViewModel-scoped coroutines collect `nowPlaying` (conflated) and call `LyricsRepository.lookup` on `(artist, title)` change; expose `StateFlow<NowPlayingUiState>` and `StateFlow<LyricsUiState>` (`Loading | Synced(lines) | Plain(text) | None`)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 847-895)
* [ ] Step 8.2: Implement `ui/NowPlayingScreen`, `ui/LyricsScreen`, `ui/StationPickerScreen` Composables with in-car typography (body 18 sp, current lyric line 36–44 sp), 56 dp touch targets, dark-first theme `#0A0A0A` / `#F2F2F2`, `LazyColumn.animateScrollToItem` for synced auto-scroll with binary-search active index
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 897-953)
* [ ] Step 8.3: Wire single-activity navigation between the three screens (Compose Navigation), `keepScreenOn = true` only on the lyrics screen gated by a DataStore flag
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 955-980)

### [ ] Implementation Phase 9: Autostart on Duduauto

<!-- parallelizable: true -->

* [ ] Step 9.1: Add `autostart/BootReceiver` (manifest-declared, exported, `RECEIVE_BOOT_COMPLETED` permission) listening for AOSP (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`), MediaTek (`QUICKBOOT_POWERON`, `BOOT_IPO`), and the confirmed OEM ACC intents (`com.fyt.boot.ACCON`, `com.glsx.boot.ACCON`, `com.cayboy.action.ACC_ON`, `com.carboy.action.ACC_ON`, `com.microntek.startApp`); fan to `ContextCompat.startForegroundService(PlaybackService)`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 982-1025)
* [ ] Step 9.2: Extend `PlaybackService.onStartCommand` with: snapshot `UsbManager.deviceList`, register runtime `USB_DEVICE_ATTACHED`/`DETACHED` receiver, register runtime `SCREEN_ON` receiver, schedule a 12 s delayed device re-poll to absorb the Duduauto deep-sleep USB-power-cut window
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 1027-1065)
* [ ] Step 9.3: Author `README.md` user-facing setup section: install APK, open once (clear stopped state), `Settings → Canbus → Startup Settings → Select app to auto start on bootup` (Scenario A) with `Settings → More features → Tasks` (Vehicle Ignition, 3 s delay) as fallback, optional `adb shell dumpsys deviceidle whitelist +com.example.radiolyric`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 1067-1100)

### [ ] Implementation Phase 10: Validation

<!-- parallelizable: false -->

* [ ] Step 10.1: Run full project build: `./gradlew :omri-usb:assembleRelease :app:lintDebug :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest`
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 1102-1126)
* [ ] Step 10.2: Smoke-test the debug flavor on the emulator inside the devcontainer overlay: app launches, `FakeRadioSource` emits the scripted timeline, lyrics fetch from LRCLIB succeeds for at least one entry (or gracefully renders `Lyrics.None` on offline emulator)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 1128-1156)
* [ ] Step 10.3: Document the in-car deploy + autostart verification checklist (ADB-over-WiFi connect, `adb install -r`, `am force-stop` + `am start`, autostart picker selection, ignition cycle test, ADB simulation of `BOOT_COMPLETED` + `com.fyt.boot.ACCON`)
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 1158-1190)
* [ ] Step 10.4: Report blocking issues (omri-usb build failures on AGP 8 / NDK r27, USB permission race on stock DUDU firmware, Heart UK SId drift, LRCLIB outage) with next-step recommendations rather than inline large refactors
  * Details: .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md (Lines 1192-1218)

## Planning Log

See .copilot-tracking/plans/logs/2026-05-01/dab-radio-lyrics-app-log.md for discrepancy tracking, implementation paths considered, and suggested follow-on work.

## Dependencies

* Devcontainer from `.copilot-tracking/plans/2026-05-01/android-kotlin-devcontainer-plan.instructions.md` (JDK 21, Android SDK 35, build-tools 35.0.0, ADB).
* NDK r27 available via `sdkmanager` for the `omri-usb` native build.
* Internet access from the dev container to `dl.google.com`, Maven Central, `lrclib.net`, and `github.com/hradio/omri-usb`.
* Kotlin 2.0.x + AGP 8.5+ + Compose Compiler Gradle plugin (Kotlin-aligned version).
* Hilt 2.51+, Room 2.6+, Media3 1.4+, Retrofit 2.11+, Moshi 1.15+, OkHttp 4.12+.
* (Phase 10.2 only) An emulator overlay devcontainer or a host with Android Studio for AVD.
* (Phase 10.3 only) Mekede DUDU7 head unit on the same Wi-Fi with developer options + ADB-over-WiFi enabled.

## Success Criteria

* `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` succeed inside the devcontainer — Traces to: User Requirement "build the app" + Derived Objective "single-module project".
* The `debug` flavor binds `FakeRadioSource` and runs end-to-end (Now Playing → LRCLIB lookup → lyrics scroll) on the emulator without any USB hardware — Traces to: User Requirement "debug without dongle attached".
* The `release` flavor binds `OmriUsbRadioSource`, the manifest declares the Mekede `16C0:05DC` USB filter, and `omri-usb` AAR ships native libs for `arm64-v8a` + `armeabi-v7a` — Traces to: User Requirement "stream from Mekede DAB+ USB dongle".
* Heart UK (SId `0xCFD1`, EId `0xC18C`, 222.064 MHz) is the default tile; tuning succeeds and audio plays even when DL+/lyrics are absent — Traces to: User Requirement "tune Heart FM" + research Scenario 2 best-effort rule.
* DLS / DL+ artist+title flow into LRCLIB; cached responses live in Room; lookups time out at 3 s; lyrics failure never produces a toast or playback interruption — Traces to: User Requirement "render lyrics best-effort".
* Manifest-declared `BootReceiver` + DUDU Settings picker documented in `README.md` give a hands-free autostart on ignition — Traces to: User Requirement "autostart on power-on".
* All native + Java sources of `omri-usb` are vendored under `omri-usb/` as a separate Gradle subproject (LGPL-2.1 dynamic-link compliance) — Traces to: Derived Objective "LGPL compliance".

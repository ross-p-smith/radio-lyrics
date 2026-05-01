<!-- markdownlint-disable-file -->
# Planning Log: DAB Radio + Live Lyrics Android App (Mekede USB DAB)

## Discrepancy Log

Gaps and differences identified between research findings and the implementation plan.

### Unaddressed Research Items

* DR-01: Heart London (SId `C460`, London 1 mux, classic DAB MP2 @ 128 kbps) is not exposed by the default tile.
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.3
  * Reason: User goal is "Heart FM" generically; the national D1 feed is the safe default. Regional pickers are deferred to a follow-on station-rescan story.
  * Impact: low

* DR-02: RadioDNS / RadioVIS (logos, EPG) is researched but not in the v1 scope.
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.4
  * Reason: Cover art / EPG is bonus; scope creep risk and the research notes RadioVIS is "intermittently available" for Heart.
  * Impact: low

* DR-03: Heart UK SId `0xCFD1` / EId `0xC18C` is hard-coded; mux re-arrangements would require user re-scan.
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.1
  * Reason: Acceptable for v1; user can re-tune via the station picker after `startRadioServiceScan`.
  * Impact: medium (one-time breakage if D1 ever re-IDs Heart UK).

* DR-04: Folklore OEM ACC intents (`com.xy.power.ACC_ON`, `com.mtcd.action.ACC_ON`, `com.mtce.action.ACC_ON`, `com.wits.action.ACC_ON`) have no primary-source confirmation.
  * Source: .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Vendor ACC intents — what's real, what's folklore"
  * Reason: Included defensively in the manifest filter; no runtime cost. Confirmation deferred to the live `dumpsys activity broadcasts` capture in WI-03.
  * Impact: low

* DR-05: USB-traffic logging (`usbmon` / Wireshark USBPcap) for protocol RE.
  * Source: .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §6
  * Reason: Only relevant if `omri-usb` misbehaves with the user's specific Mekede stick; revisit during Phase 10.3.
  * Impact: low

* DR-06: NowPlayingHistory (rolling "recently played" Room table) is mentioned as optional.
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.5
  * Reason: Out of scope for v1; can be added without schema migration once Room v2 is needed.
  * Impact: low

* DR-07: Confirm exact Mekede dongle VID/PID via `adb shell lsusb -v` before shipping.
  * Source: .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Potential Next Research"
  * Reason: Only confirmable on user hardware; folded into the Phase 10.3 verification checklist.
  * Impact: medium (a wrong filter blocks USB attach intent).

* DR-08: LRCLIB ToS review for in-car personal use.
  * Source: .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Potential Next Research"
  * Reason: Research notes the project explicitly targets third-party app integration; legal review is light-touch for a personal hobby app. Recorded for awareness.
  * Impact: low

* DR-09: Decompile DUDU `Tasks` app to find the actual Vehicle Ignition broadcast.
  * Source: .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Potential Next Research"
  * Reason: The Tasks UI configuration path already works; decompilation only needed if we ever want to drop the manual Tasks setup step.
  * Impact: low

### Plan Deviations from Research

* DD-01: Plan formalises the AGP 8 / NDK r27 port of `omri-usb` as Phase 2.2 with a fallback strategy (CMake threads patch, desugaring) rather than treating it as research-only.
  * Research recommends: "Build hradio/omri-usb against current AGP 8.x / NDK r27" as a future research item.
  * Plan implements: an explicit phase with patches captured in `omri-usb/.copilot-build-notes.md`; if the port fails the planner escalates to a fork-and-modernise follow-on (WI-04) rather than blocking the whole task.
  * Rationale: The whole pipeline depends on this AAR; punting it to "research later" would block Phase 4 indefinitely.

* DD-02: Hilt binding swap uses source-set splits (`src/debug/kotlin/.../DebugRadioBindings.kt` vs `src/release/kotlin/.../ReleaseRadioBindings.kt`) instead of `BuildConfig.USE_FAKE_RADIO`.
  * Research recommends: a `BuildConfig.USE_FAKE_RADIO` flag inside one Hilt module.
  * Plan implements: source-set-scoped Hilt modules.
  * Rationale: Hilt validates bindings at compile time; source-set splits surface missing or duplicate bindings before the app launches, which matters more on a head unit where iteration is slow.

* DD-03: Lyrics fallback chain is LRCLIB-only — no Happi.dev fallback.
  * Research recommends (subagent): Happi.dev as a paid plain-text fallback.
  * Plan implements: LRCLIB → `Lyrics.None` (no paid services per user constraint).
  * Rationale: Direct user constraint in the primary research's §"Scenario 2 — Lyrics Provider".

* DD-04: AudioTrack pump is the default audio sink in the plan; ExoPlayer integration with `omri-usb` is noted as a Phase 5.1 implementation-time decision.
  * Research recommends: ExoPlayer-backed `MediaSession` for full Media3 surface.
  * Plan implements: AudioTrack pump first; switch to ExoPlayer only if `omri-usb` exposes a clean `MediaSource` integration.
  * Rationale: `omri-usb` does software AAC decode and emits PCM; bridging that into a custom ExoPlayer `MediaSource` is more code than `AudioTrack.write` in a coroutine. Media3 `MediaSession` still exposes lock-screen / steering-wheel controls regardless of which sink is used.

### Implementation Deviations Discovered

* DD-05 (Phase 1): `:omri-usb` include in `settings.gradle.kts` was commented out with a TODO so `./gradlew help` could succeed before Phase 2 vendors the submodule.
  * Plan specifies: include `:omri-usb` from Step 1.1.
  * Implementation differs: include is commented; Phase 2 Step 2.1 must uncomment.
  * Rationale: Without the submodule directory, Gradle would fail to configure.
* DD-06 (Phase 1): Devcontainer `/home/vscode/.gradle` and `/home/vscode/.android` had to be `chown`'d to `vscode` inside the phase to allow the wrapper to write caches.
  * Plan specifies: rely on devcontainer post-create.
  * Implementation differs: chown applied inline.
  * Rationale: Root ownership blocked Gradle wrapper download. Folded into WI-10 below.
* DD-07 (Phase 1): Manifest references `@mipmap/ic_launcher` resources that do not yet exist.
  * Plan specifies: nothing about launcher icons.
  * Implementation differs: defer icons to Phase 8.
  * Rationale: Process-debug-manifest passes; assemble-debug currently can't until icons land.

## Implementation Paths Considered

### Selected: Vendored `omri-usb` AAR + Media3 `MediaSessionService` + LRCLIB + FakeRadioSource

* Approach: Vendor `hradio/omri-usb` as a `:omri-usb` Gradle subproject (LGPL-2.1 dynamic-link compliance), build a thin `RadioSource` Kotlin interface in `:app`, host a `mediaPlayback` foreground service backed by Media3 `MediaSessionService`, pump PCM into `AudioTrack`, fetch lyrics from LRCLIB into a Room cache, ship a `debug` flavor whose Hilt graph binds `FakeRadioSource` for emulator iteration.
* Rationale: Reuses ~1300 lines of tested C++ for Raon RTV register protocol + DAB+ AAC decode + DLS / DL+ / MOT; gets free synced lyrics without an API key; covers the no-hardware dev loop with zero emulator-USB-passthrough hackery.
* Evidence: .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"Scenarios 1–3" (all marked SELECTED in primary research).

### IP-01: Reverse-engineer the Raon RTV USB protocol from scratch

* Approach: Skip `omri-usb`, write our own Kotlin/JNI driver against the dongle's bulk endpoints, implement DAB+ AAC decode in software.
* Trade-offs: Full control over license + Kotlin-native code; weeks of work and zero functional gain over `omri-usb`.
* Rejection rationale: Selected research §"Considered Alternatives B"; prohibitive cost for hobby scope.

### IP-02: IPC bridge to DAB-Z (`com.zoulou.dab`) via `NotificationListenerService`

* Approach: Let DAB-Z own the dongle, scrape its `MediaSession` notification for now-playing.
* Trade-offs: No code to write for tuning; cannot render audio inside our process; brittle across DAB-Z updates; user must keep two apps in sync.
* Rejection rationale: Selected research §"Considered Alternatives C"; "behaviour would change silently across updates".

### IP-03: Monkeyboard / Keystone (`04D8:000A`) hardware via `freshollie/monkeyboard-radio-android`

* Approach: Different USB DAB family — CDC-ACM serial, analog audio out, GPL-3 reference implementation.
* Trade-offs: Cheaper, proven Android driver path; **wrong hardware** for the user (they have the Mekede dongle); requires wiring 3.5 mm AUX into the head unit.
* Rejection rationale: User's confirmed hardware is the Mekede `16C0:05DC` Raon RTV stick. Recorded for completeness because the subagent research covers this path in depth.

### IP-04: RTL-SDR + welle.io / dablin software-defined DAB stack

* Approach: Replace the Mekede dongle with an RTL-SDR; run a full DAB demodulator on the head-unit CPU.
* Trade-offs: Best DL+ fidelity (we own the demod); 10× CPU cost; requires the user to buy and wire different hardware.
* Rejection rationale: User has the Mekede dongle; CPU headroom on UIS7870 is plentiful but not infinite for a head unit running a launcher + carplay + our app simultaneously.

### IP-05: Musixmatch as the lyrics provider

* Approach: Use the licensed Musixmatch Pro API (Grow tier, $199/mo) for synced lyrics.
* Trade-offs: Best catalogue + legal certainty; $199/mo + EULA forbids on-device caching below the Enterprise tier.
* Rejection rationale: User constraint "no paid services". Direct violation.

### IP-06: Multi-module Gradle layout (`:core`, `:data`, `:playback`, `:app`)

* Approach: Split the app into multiple Gradle modules from day one.
* Trade-offs: Cleaner boundaries; faster incremental builds at scale; over-engineering for ~ a few KLOC of greenfield code.
* Rejection rationale: Research §B.6 explicitly recommends single-module until multi-platform variants (wear, AAOS) appear.

### IP-07: Launcher replacement (`<category android:name="android.intent.category.HOME" />`)

* Approach: Become the head unit's launcher to guarantee autostart.
* Trade-offs: Bullet-proof autostart; breaks DUDU vehicle widgets, reverse camera trigger, steering-wheel mapping.
* Rejection rationale: Autostart research §"Scenario C"; too invasive for the benefit. May ship as an opt-in `:app:carlauncher` flavor in a future iteration (WI-05).

## Suggested Follow-On Work

Items identified during planning that fall outside current scope.

* WI-01: MOT slideshow / cover art rendering. — Subscribe to `omri-usb`'s slideshow callback and render cover art on the Now Playing screen (Coil already on the dependency list). (low priority)
  * Source: .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md §"OSS Driver Stack" (MOT support listed).
  * Dependency: Phase 4 (`OmriUsbRadioSource`) complete.

* WI-02: Station rescan + regional Heart variants picker. — Add a UI to run `tuner.startRadioServiceScan()` on demand and surface regional Heart Scotland / Wales / London services when present. (medium priority)
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §A.3.
  * Dependency: Phase 8 (UI scaffolding).

* WI-03: Live `dumpsys activity broadcasts` capture during a real Duduauto key cycle. — Confirm which OEM ACC intents DUDU OS actually fires, then prune the folklore filters from the manifest. (low priority)
  * Source: .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Potential Next Research".
  * Dependency: Phase 10.3 in-car deploy.

* WI-04: Fork + modernise `hradio/omri-usb` upstream. — If the AGP 8 / NDK r27 port in Phase 2.2 requires non-trivial patches, publish them as a fork or PR upstream. (low priority)
  * Source: DD-01 in this log.
  * Dependency: Phase 2 outcome.

* WI-05: `:app:carlauncher` opt-in flavor that adds `category.HOME` for users who want bullet-proof autostart at the cost of replacing DUDU Launcher. (low priority)
  * Source: .copilot-tracking/research/2026-05-01/autostart-on-duduauto-research.md §"Scenario C".
  * Dependency: Phase 9 stable.

* WI-06: NowPlayingHistory Room v2 schema + "recently played" tab. (low priority)
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.5.
  * Dependency: Phase 6 stable.

* WI-07: Android Auto / AAOS browse tree (`MediaLibraryService` instead of plain `MediaSessionService`). (low priority)
  * Source: .copilot-tracking/research/subagents/2026-04-30/heartfm-and-architecture-research.md §B.2.
  * Dependency: Phase 5 stable.

* WI-08: USB-traffic capture protocol diagnostics (`usbmon` or USBPcap) when `omri-usb` misbehaves on the user's specific Mekede revision. (low priority, blocked-on-hardware)
  * Source: .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md §6.
  * Dependency: Symptom emerges during Phase 10.3.

* WI-09: Replace AudioTrack pump with an ExoPlayer custom `MediaSource` if `omri-usb` exposes a frame-pull API cleanly. (medium priority)
  * Source: DD-04 in this log.
  * Dependency: Phase 5 stable + a Phase 5 implementation-time decision recorded in changes file.

* WI-10: Patch devcontainer `postCreateCommand` to `chown -R vscode:vscode /home/vscode/.gradle /home/vscode/.android` so future container rebuilds don't repeat DEV-1 / DD-06. (low priority)
  * Source: Phase 1 implementor report.
  * Dependency: None.

* WI-11: Add launcher mipmaps (`ic_launcher`, `ic_launcher_round`) before Phase 8 / Phase 10 build smoke tests so `./gradlew :app:assembleDebug` reaches green. (medium priority)
  * Source: DD-07 in this log.
  * Dependency: Phase 8 UI work.

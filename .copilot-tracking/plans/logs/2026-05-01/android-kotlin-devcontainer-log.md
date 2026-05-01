<!-- markdownlint-disable-file -->
# Planning Log: Android Kotlin Devcontainer for radio-lyric

## Discrepancy Log

### Unaddressed Research Items

* DR-01: Pinned `CMDLINE_TOOLS_VERSION=11076708` may drift if Google rotates the URL.
  * Source: .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Dockerfile section)
  * Reason: Plan pins a known-good version with a single-line `ARG` for explicit upgrades; live URL discovery is out of scope.
  * Impact: low (one-line bump when the URL changes).
* DR-02: Exact Duduauto 7 Android API level is unverified (likely 22-29).
  * Source: .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Potential Next Research)
  * Reason: Cannot be discovered without ADB access to the head unit; resolved later by Step 3.3.
  * Impact: medium — feeds into the eventual app's `minSdk`, not the devcontainer itself.
* DR-03: ktlint and detekt static analysis tooling is not provisioned by any plan step.
  * Source: .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Task Implementation Requests — "ktlint/detekt, Android Lint")
  * Reason: Plan covers Android Lint (Step 3.2 `./gradlew lint`) but does not install ktlint/detekt CLIs, add Gradle plugins, or surface them as VS Code extensions; user request for "all the debugging tools" includes static analysis.
  * Impact: medium — both are typically applied as Gradle plugins on the app side rather than baked into the image, so deferral to the app-scaffold work item (WI-01) is reasonable, but the deferral is currently undocumented.
* DR-04: NDK package install (`ndk;26.3.11579264`) is omitted without explicit plan-side rationale.
  * Source: .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Android SDK install strategy — "only if NDK is needed")
  * Reason: Research conditionally excludes NDK because the lyric app has no native code today; plan inherits the exclusion silently.
  * Impact: low — consistent with research; flag exists only so a future native dependency triggers a re-evaluation.

### Plan Deviations from Research

* DD-01: Emulator excluded from the default image.
  * Research recommends: do not bake emulator into primary image; provide overlay.
  * Plan implements: same — primary `Dockerfile` has no emulator; `Dockerfile.emulator` is opt-in.
  * Rationale: Aligned with research; recorded for traceability since the user asked broadly for "all the debugging tools" — emulator is offered, just not default.
* DD-02: README (Step 2.2) documents Android 11+ `adb pair` flow without flagging that the Duduauto 7 head unit is Android 7.x-class.
  * Research recommends: support both legacy `adb connect <ip>:5555` and Android 11+ `adb pair` wireless debugging.
  * Plan implements: README will cover both flows generically per Step 2.2 description.
  * Rationale: Project context locks the primary target to Android 7.x where only the legacy port-5555 path applies; README should lead with `adb connect :5555` and mark `adb pair` as "for newer Android targets" to avoid pairing-failure churn during in-car bring-up.
  * Status (post-impl): Resolved — `.devcontainer/README.md` leads with the legacy `:5555` flow and presents `adb pair` as an explicit sidebar labelled "for Android 11+ targets only".
* DD-03: Step 3.2 (`./gradlew assembleDebug` smoke test) was not executed during this implementation pass.
  * Plan specifies: bootstrap a minimal Compose app via `gradle init` and run `assembleDebug` / `test` / `lint` inside the container.
  * Implementation differs: Step 3.1 toolchain verification was executed and passed; Step 3.2 was deferred.
  * Rationale: The implementation details file marks the scaffold as "Temporary scaffold; not part of the final commit unless the user wants a starter app committed." Bootstrapping a throwaway Android Gradle project (~5–10 min of dependency downloads, no committed artifacts) duplicates WI-01 (the actual app scaffold). Toolchain verification (Step 3.1) already demonstrates the image is build-ready (JDK 21, sdkmanager 12.0, adb 1.0.41, build-tools/platform-tools/platforms;android-35 all installed). Recommended action: validate `./gradlew assembleDebug` opportunistically as part of WI-01.
* DD-04: Encountered and fixed an upstream blocker during the image build (Step 3.1).
  * Plan specifies: `docker build -t radio-lyric-dev .devcontainer` should succeed.
  * Implementation differs: First build failed because `mcr.microsoft.com/devcontainers/java:1-21-bookworm` ships a stale Yarn apt source whose GPG key is no longer trusted (`NO_PUBKEY 62D54FD4003F6525`), causing `apt-get update` to abort.
  * Rationale: Added `rm -f /etc/apt/sources.list.d/yarn.list` before `apt-get update` in `.devcontainer/Dockerfile`. Yarn is not needed for an Android Kotlin build; this is the lowest-impact fix and survives base-image rebuilds. Documented under "Additional or Deviating Changes" in the changes log.

## Implementation Paths Considered

### Selected: JDK 21 MS Java base + manual Android SDK in Dockerfile

* Approach: `mcr.microsoft.com/devcontainers/java:1-21-bookworm` + hand-rolled `sdkmanager` install of API 35 + `--network=host` for ADB-over-Wi-Fi + named-volume caches.
* Rationale: Official, current, auditable, minimal moving parts; supports the project's mock-driven no-USB-DAB dev loop.
* Evidence: .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Scenario A — SELECTED)

### IP-01: Community Android devcontainer feature (`devcontainers-contrib/features/android-sdk`)

* Approach: Use a community feature to install the Android SDK declaratively.
* Trade-offs: Less Dockerfile code; opaque pinning; lags AGP releases.
* Rejection rationale: Auditability and currency favored over brevity.

### IP-02: Pre-built community Android image (`mingc/android-build-box`, `cimg/android`)

* Approach: Pull a CI-flavored Android image as the base.
* Trade-offs: Fast start; non-`vscode` user; conflicts with devcontainer expectations.
* Rejection rationale: Devcontainer integration friction outweighs convenience.

### IP-03: Host-only Android Studio (no devcontainer)

* Approach: Skip containerization; use Android Studio on host.
* Trade-offs: Best GUI tooling; zero reproducibility; doesn't fulfill the user's request.
* Rejection rationale: Doesn't satisfy the explicit "devcontainer" requirement; offered as a complement rather than replacement.

### IP-04: JDK 17 instead of JDK 21

* Approach: Older LTS JDK.
* Trade-offs: Slightly broader compatibility today; future AGP bumps will force a move.
* Rejection rationale: JDK 21 is current AGP recommendation.

### IP-05: Bake emulator + system-images into primary image

* Approach: Single image includes the AVD stack.
* Trade-offs: One-shot setup; +5–8 GB; requires `/dev/kvm`.
* Rejection rationale: Bloat and host dependency; provided as opt-in overlay instead.

## Suggested Follow-On Work

* WI-01: Scaffold the actual `radio-lyric` Android Kotlin/Compose app skeleton (`gradle init` Android template, package layout, Hilt, MediaSession, **ktlint + detekt Gradle plugins**). (high)
  * Source: research overview in .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md
  * Dependency: Devcontainer (this plan) complete.
* WI-02: Implement `RadioSource` abstraction with mock + recorded-DLS replay for hardware-free debugging. (high)
  * Source: .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md
  * Dependency: WI-01.
* WI-03: Pick lyrics provider (LRCLIB recommended) and add API client + UI. (medium)
  * Source: .copilot-tracking/research/subagents/2026-04-30/lyrics-api-research.md
  * Dependency: WI-01.
* WI-04: USB DAB integration layer (USB Host API or `dabZ` IPC sniffing). (medium)
  * Source: .copilot-tracking/research/subagents/2026-04-30/usb-dab-integration-research.md
  * Dependency: WI-01, WI-02.
* WI-05: CI pipeline (GitHub Actions) reusing the devcontainer image. (low)
  * Source: implied by reproducibility goal
  * Dependency: this plan + WI-01.
* WI-06: Verify Duduauto 7 Android API level via `adb shell getprop` once on Wi-Fi; finalize `minSdk`. (low)
  * Source: DR-02 above
  * Dependency: Step 3.3.

---
applyTo: '.copilot-tracking/changes/2026-05-01/android-kotlin-devcontainer-changes.md'
---
<!-- markdownlint-disable-file -->
# Implementation Plan: Android Kotlin Devcontainer for radio-lyric

## Overview

Bootstrap a reproducible VS Code devcontainer (JDK 21 + Android SDK 35 + ADB-over-Wi-Fi) so the radio-lyric Android Kotlin app can be built, unit-tested, and deployed to the in-car Duduauto 7 head unit without requiring the USB DAB dongle to be attached during development.

## Objectives

### User Requirements

* Provide a devcontainer for building an Android app in Kotlin with all debugging tools — Source: user prompt 2026-05-01.
* Do not require Apple Silicon / ARM64 support — Source: user message 2026-05-01.
* Support debugging the radio-lyric app without the USB DAB dongue attached (car-bound device) — Source: original task brief in .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md.

### Derived Objectives

* Pin a known-good toolchain (JDK 21, AGP 8.x, Kotlin 2.0.x, Compose Compiler Gradle plugin) — Derived from: avoiding the AGP/Kotlin/Compose version-matrix churn called out in the research doc.
* Enable ADB-over-Wi-Fi to the in-car head unit from inside the container — Derived from: device lives in the car, USB attach is impractical.
* Keep image lean (no emulator by default) and provide an opt-in emulator overlay — Derived from: 5–8 GB image bloat trade-off documented in research.
* Persist Gradle and `.android` caches via named volumes for fast rebuilds — Derived from: research caching recommendation.

## Context Summary

### Project Files

* /home/rosmith/src/radio-lyric — Greenfield workspace; only `.copilot-tracking/` research exists, no Gradle project yet. Devcontainer must support `gradle init` / scaffolding from inside.

### References

* .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md — Selected approach, complete Dockerfile/devcontainer.json/post-create.sh examples, alternatives evaluated.
* .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md — Overall app brief, target device (Duduauto 7), mock-driven dev loop expectation.
* .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md — No-USB-DAB debug strategy that the devcontainer must enable (ADB-over-Wi-Fi + JVM unit tests on mock RadioSource).
* https://developer.android.com/tools/sdkmanager — Android cmdline-tools install protocol.
* https://developer.android.com/build/releases/gradle-plugin — AGP/JDK/Gradle compatibility matrix.
* https://containers.dev/implementors/json_reference/ — devcontainer.json spec.

### Standards References

* None repository-specific yet (greenfield). Follow Microsoft devcontainer conventions: non-root `vscode` user, `mcr.microsoft.com/devcontainers/*` base, features for cross-cutting concerns.

## Implementation Checklist

### [x] Implementation Phase 1: Devcontainer scaffolding

<!-- parallelizable: false -->

* [x] Step 1.1: Create `.devcontainer/Dockerfile` with JDK 21 base + Android SDK 35 + build-tools 35.0.0 + platform-tools + adb
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 9-44)
* [x] Step 1.2: Create `.devcontainer/devcontainer.json` with `--network=host`, named-volume caches, VS Code extensions, JDK runtime config
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 46-78)
* [x] Step 1.3: Create `.devcontainer/post-create.sh` (toolchain verification + ADB-over-Wi-Fi hint)
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 80-99)

### [x] Implementation Phase 2: Optional emulator overlay

<!-- parallelizable: true -->

* [x] Step 2.1: Create `.devcontainer/Dockerfile.emulator` overlay adding `emulator` + `system-images;android-34;google_apis;x86_64`
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 101-119)
* [x] Step 2.2: Document emulator-mode runArgs (`--device=/dev/kvm --group-add=kvm`) in a short `.devcontainer/README.md`
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 121-138)

### [x] Implementation Phase 3: Validation

<!-- parallelizable: false -->

* [x] Step 3.1: Build the devcontainer image and verify toolchain (`java -version`, `sdkmanager --version`, `adb version`)
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 140-156)
* [x] Step 3.2: Smoke-test Gradle Android build by scaffolding a minimal Compose app and running `./gradlew assembleDebug` and `./gradlew test`
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 158-176)
* [x] Step 3.3: Verify `adb connect <car-ip>:5555` reachability from inside the container (deferred to user with car on Wi-Fi; record outcome)
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 178-191)
* [x] Step 3.4: Report blocking issues (license-acceptance failures, cmdline-tools URL changes, KVM unavailability) with next-step recommendations
  * Details: .copilot-tracking/details/2026-05-01/android-kotlin-devcontainer-details.md (Lines 193-205)

## Planning Log

See .copilot-tracking/plans/logs/2026-05-01/android-kotlin-devcontainer-log.md for discrepancy tracking, implementation paths considered, and suggested follow-on work.

## Dependencies

* Docker Engine on Linux/amd64 host with internet access to `dl.google.com` and `mcr.microsoft.com`.
* VS Code with the Dev Containers extension.
* (Phase 3.3 only) Duduauto 7 head unit reachable on the same Wi-Fi with developer/wireless-debugging mode enabled.

## Success Criteria

* `.devcontainer/` folder produces a working container on first build — Traces to: User Requirement "devcontainer for building Android Kotlin app".
* `./gradlew assembleDebug` and `./gradlew test` succeed on a Compose sample inside the container — Traces to: Derived Objective "pin known-good toolchain".
* `adb` is present and `adb connect <ip>:5555` succeeds on Linux host with `--network=host` — Traces to: User Requirement "debug without USB DAB attached".
* Default image excludes the emulator and system-images (lean); overlay variant adds them — Traces to: Derived Objective "keep image lean".
* Gradle and `.android` caches survive container rebuilds via named volumes — Traces to: Derived Objective "fast rebuilds".

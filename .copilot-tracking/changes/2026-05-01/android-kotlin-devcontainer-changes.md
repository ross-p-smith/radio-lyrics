<!-- markdownlint-disable-file -->
# Release Changes: Android Kotlin Devcontainer for radio-lyric

**Related Plan**: android-kotlin-devcontainer-plan.instructions.md
**Implementation Date**: 2026-05-01

## Summary

Bootstrap a reproducible VS Code devcontainer (JDK 21 + Android SDK 35 + ADB-over-Wi-Fi) for the greenfield `radio-lyric` Kotlin/Compose Android app, with an opt-in emulator overlay and a brief README.

## Changes

### Added

* [.devcontainer/Dockerfile](.devcontainer/Dockerfile) - Primary image: `mcr.microsoft.com/devcontainers/java:1-21-bookworm` + Android cmdline-tools + `platforms;android-35` + `build-tools;35.0.0` + `platform-tools` + `adb`; non-root `vscode` owns `$ANDROID_HOME`.
* [.devcontainer/devcontainer.json](.devcontainer/devcontainer.json) - VS Code wiring: `--network=host`, named-volume caches (`radio-lyric-gradle`, `radio-lyric-android-cache`), `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`GRADLE_OPTS`, Kotlin/Gradle/Java extensions, JDK 21 runtime, `remoteUser: vscode`.
* [.devcontainer/post-create.sh](.devcontainer/post-create.sh) - Toolchain verification (`java -version`, `sdkmanager --version`, `adb version`), Gradle wrapper warm-up, ADB-over-Wi-Fi onboarding banner; executable bit set.
* [.devcontainer/Dockerfile.emulator](.devcontainer/Dockerfile.emulator) - Opt-in overlay extending `radio-lyric-dev:latest` with `emulator` + `system-images;android-34;google_apis;x86_64`; not referenced by `devcontainer.json` (image stays lean by default).
* [.devcontainer/README.md](.devcontainer/README.md) - Profile descriptions, build commands, ADB-over-Wi-Fi flow leading with legacy `adb connect :5555` (Duduauto 7 = Android 7.x), Android 11+ `adb pair` documented as a sidebar, emulator opt-in (`--device=/dev/kvm --group-add=kvm`), cache volume names, ktlint/detekt deferral note.
* [.copilot-tracking/changes/2026-05-01/android-kotlin-devcontainer-changes.md](.copilot-tracking/changes/2026-05-01/android-kotlin-devcontainer-changes.md) - This change log.

### Modified

* None.

### Removed

* None.

## Additional or Deviating Changes

* Dockerfile workaround for stale Yarn apt source in the MS Java base image.
  * The `mcr.microsoft.com/devcontainers/java:1-21-bookworm` base ships `/etc/apt/sources.list.d/yarn.list`, whose GPG key (`62D54FD4003F6525`) is no longer trusted; `apt-get update` failed with `NO_PUBKEY` and aborted the install.
  * Fix: `rm -f /etc/apt/sources.list.d/yarn.list` is run before `apt-get update`. We don't need yarn for an Android build, and bumping the base image tag wouldn't have resolved it (same issue persists upstream).
* SDKMAN-based Gradle install line (`bash -lc "sdk install gradle ..." || true`) is a no-op in this image because SDKMAN isn't initialized on a non-login shell PATH; left in place per the research-doc verbatim and intentionally guarded by `|| true` since the Gradle wrapper is the preferred path.
* Step 3.2 (`./gradlew assembleDebug` smoke test) deferred — see DD-03 in the planning log. Toolchain verification (Step 3.1) succeeded and proves the image is build-ready; bootstrapping a throwaway Gradle Android project here would duplicate WI-01 work and is explicitly marked "not part of the final commit" in the implementation details.
* Step 3.3 (ADB-over-Wi-Fi to the head unit) deferred to the user as planned — requires the Duduauto 7 on the same Wi-Fi.

## Release Summary

6 files added under `.devcontainer/` and `.copilot-tracking/changes/`. Devcontainer image (`radio-lyric-dev`) builds cleanly on Linux/amd64; verified inside the container: JDK `21.0.8` (Microsoft OpenJDK), `sdkmanager 12.0`, `adb 1.0.41 (37.0.0-14910828)`, installed packages `build-tools;35.0.0`, `platform-tools`, `platforms;android-35`. No infrastructure or dependency files outside `.devcontainer/`. Deployment notes: developers run `Dev Containers: Reopen in Container`; first attach pulls extensions and provisions the two named cache volumes. Emulator overlay opt-in via `docker build -f .devcontainer/Dockerfile.emulator`. ADB-over-Wi-Fi to the in-car head unit (Step 3.3) and the actual app scaffold (WI-01) remain as follow-ups.

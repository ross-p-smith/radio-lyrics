<!-- markdownlint-disable-file -->
# Implementation Details: Android Kotlin Devcontainer for radio-lyric

## Context Reference

Sources: .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (selected approach + complete artifacts), .copilot-tracking/research/2026-04-30/dab-radio-lyrics-app-research.md (target app), .copilot-tracking/research/subagents/2026-04-30/debug-without-hardware-research.md (no-hardware debug loop).

## Implementation Phase 1: Devcontainer scaffolding

<!-- parallelizable: false -->

### Step 1.1: Create `.devcontainer/Dockerfile`

Create the image definition. Base on `mcr.microsoft.com/devcontainers/java:1-21-bookworm`; install Android cmdline-tools at a pinned `CMDLINE_TOOLS_VERSION`; install `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`; accept licenses non-interactively; chown `$ANDROID_HOME` to `vscode`.

Files:
* .devcontainer/Dockerfile - New; copy verbatim from research doc "Complete Examples → `.devcontainer/Dockerfile`" section.

Discrepancy references:
* Addresses DR-01 (cmdline-tools version drift).
* Acknowledges DR-04: NDK is intentionally excluded from the image; revisit if the app gains a native dependency.

Success criteria:
* `docker build` completes without license-prompt hangs.
* `sdkmanager --list_installed` inside image shows `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`.
* `$ANDROID_HOME` writable by `vscode` user.

Context references:
* .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Dockerfile example)

Dependencies:
* Docker daemon, internet access to `dl.google.com`.

### Step 1.2: Create `.devcontainer/devcontainer.json`

Wire the image into VS Code: enable `--network=host` (Linux host) for ADB-over-Wi-Fi, mount named volumes `radio-lyric-gradle` → `/home/vscode/.gradle` and `radio-lyric-android-cache` → `/home/vscode/.android`, set `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`GRADLE_OPTS`, install Kotlin/Gradle/Java VS Code extensions, set `remoteUser: vscode`.

Files:
* .devcontainer/devcontainer.json - New; copy from research doc "Complete Examples → `.devcontainer/devcontainer.json`" section.

Discrepancy references:
* None.

Success criteria:
* `Dev Containers: Reopen in Container` succeeds.
* Inside container: `echo $ANDROID_HOME` prints `/opt/android-sdk`.
* Listed VS Code extensions install on first attach.

Context references:
* .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (devcontainer.json example)

Dependencies:
* Step 1.1 image builds.

### Step 1.3: Create `.devcontainer/post-create.sh`

Verification + onboarding hint. Runs `java -version`, `sdkmanager --version`, `adb version`, pre-warms `./gradlew --version` if a wrapper exists, prints ADB-over-Wi-Fi instructions.

Files:
* .devcontainer/post-create.sh - New; copy from research doc; `chmod +x`.

Discrepancy references:
* None.

Success criteria:
* Script is executable; runs cleanly during `postCreateCommand`.
* Output includes "Devcontainer ready" banner with `adb connect` hint.

Context references:
* .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (post-create.sh example)

Dependencies:
* Steps 1.1, 1.2.

## Implementation Phase 2: Optional emulator overlay

<!-- parallelizable: true -->

### Step 2.1: Create `.devcontainer/Dockerfile.emulator`

Overlay image that extends the primary image and adds `emulator` + an x86_64 system image. Not built by default; opt-in for developers wanting a containerized AVD.

Files:
* .devcontainer/Dockerfile.emulator - New; copy from research doc "Configuration Examples → emulator-enabled variant".

Discrepancy references:
* Addresses DD-01 (emulator deliberately excluded from default image).

Success criteria:
* File exists; documents that it inherits from `radio-lyric-dev:latest`.
* Not referenced by `devcontainer.json` (default path stays lean).

Context references:
* .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Dockerfile.emulator example)

Dependencies:
* Step 1.1.

### Step 2.2: Document emulator usage in `.devcontainer/README.md`

Briefly explain the two profiles (default vs. emulator overlay), the `--device=/dev/kvm --group-add=kvm` runArgs needed for emulator mode, and the ADB-over-Wi-Fi flow.

ADB guidance ordering (matches Duduauto 7 = Android 7.x-class target):
1. Lead with legacy `adb connect <car-ip>:5555` (the realistic path for the head unit).
2. Document `adb pair <ip>:<port>` only as a sidebar labelled "for Android 11+ targets".

Also note that Kotlin static-analysis tooling (ktlint, detekt) is intentionally not installed in the image; it is added later as Gradle plugins by the app-scaffold work item (WI-01) so version pinning lives in the project, not the container.

Files:
* .devcontainer/README.md - New; ~30-50 lines.

Discrepancy references:
* None.

Success criteria:
* README covers: build commands, ADB-over-Wi-Fi pairing steps, emulator opt-in, cache volume names.
* Conforms to repo markdown conventions (markdownlint-disable not needed outside `.copilot-tracking/`).

Context references:
* .copilot-tracking/research/2026-05-01/android-kotlin-devcontainer-research.md (Implementation Details section)

Dependencies:
* None (parallelizable with 2.1).

## Implementation Phase 3: Validation

<!-- parallelizable: false -->

### Step 3.1: Build and verify toolchain

Build the image and exec into it to confirm versions.

Validation commands:
* `docker build -t radio-lyric-dev .devcontainer` — image build
* `docker run --rm radio-lyric-dev java -version` — JDK 21 present
* `docker run --rm radio-lyric-dev sdkmanager --version` — Android tools present
* `docker run --rm radio-lyric-dev adb version` — adb present

Files:
* No file changes.

Success criteria:
* All four commands exit 0; JDK reports 21.x.

### Step 3.2: Smoke-test Android Gradle build

Scaffold a minimal Compose app inside the container (or use a temporary `gradle init` Android template) and confirm full build works.

Validation commands:
* `./gradlew assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk`
* `./gradlew test` — JVM unit tests pass (sanity)
* `./gradlew lint` — Android Lint runs

Files:
* (Temporary scaffold; not part of the final commit unless the user wants a starter app committed.)

Success criteria:
* All Gradle tasks succeed.
* Gradle cache populates the `radio-lyric-gradle` named volume (rebuild speed verified on second run).

### Step 3.3: ADB-over-Wi-Fi reachability (deferred to hardware availability)

This step requires the Duduauto 7 head unit on the same Wi-Fi. Capture the outcome when the user runs it; do not block plan completion.

Validation commands (to run when in the car / on the same Wi-Fi):
* `adb start-server`
* `adb connect <car-ip>:5555` (or `adb pair <ip>:<port>` first on Android 11+)
* `adb devices` — head unit shows `device`

Success criteria:
* Head unit appears in `adb devices` from inside the container.
* `adb shell getprop ro.build.version.release` returns the Duduauto Android version (also unblocks DR-02).

### Step 3.4: Report blocking issues

If validation fails beyond minor fixes, surface the issue category and recommended next step rather than expanding scope inline.

Known potential blockers:
* Google rotated the cmdline-tools URL → bump `CMDLINE_TOOLS_VERSION` ARG.
* `yes | sdkmanager --licenses` fails on a new license → re-run interactively once, capture license file.
* `--network=host` denied by host policy → fall back to default bridge (outbound `adb connect` still works) and document.
* `/dev/kvm` unavailable → emulator overlay unsupported on that host; use real device only.

Success criteria:
* Each encountered failure documented with the next recommended action; no silent breakage.

## Dependencies

* Docker Engine (Linux/amd64).
* Internet access to `mcr.microsoft.com` and `dl.google.com`.
* VS Code Dev Containers extension.

## Success Criteria

* `.devcontainer/` produces a working container on first build with JDK 21 + Android SDK 35 + adb.
* `./gradlew assembleDebug` and `./gradlew test` succeed on a Compose sample.
* `adb connect` succeeds from inside the container on a Linux host using `--network=host`.
* Image stays lean by default; emulator is opt-in via overlay.

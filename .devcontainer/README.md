# radio-lyric devcontainer

Reproducible JDK 21 + Android SDK 35 environment for building, unit-testing, and deploying the `radio-lyric` head-unit app to the in-car Duduauto 7 over ADB-over-Wi-Fi.

## Profiles

Two image profiles are provided:

- **Default** (`Dockerfile`) — JDK 21, Android SDK 35, build-tools 35.0.0, platform-tools, `adb`. No emulator. Used automatically by `devcontainer.json`.
- **Emulator overlay** (`Dockerfile.emulator`) — opt-in extension that adds `emulator` and `system-images;android-34;google_apis;x86_64`. Adds ~5–8 GB and requires KVM on the host.

## Build commands

Inside the container (after `Dev Containers: Reopen in Container`):

```bash
./gradlew assembleDebug    # debug APK -> app/build/outputs/apk/debug/
./gradlew test             # JVM unit tests (no device required)
./gradlew lint             # Android Lint
./gradlew installDebug     # install on connected ADB device
```

The Gradle wrapper is preferred over the SDKMAN-installed `gradle` baked into the image.

## ADB-over-Wi-Fi to the Duduauto 7

The Duduauto 7 head unit is Android 7.x-class, so the legacy port-5555 flow is the realistic path. Enable "ADB over network" / "Wireless debugging" in Developer options on the head unit, then from inside the container:

```bash
adb start-server
adb connect 192.168.X.Y:5555
adb devices                # head unit should appear as `device`
adb shell getprop ro.build.version.release   # confirms Android version
```

> **Sidebar — for Android 11+ targets only:** newer devices use a paired wireless-debugging flow:
>
> ```bash
> adb pair 192.168.X.Y:<pair-port>     # one-time, enter the pairing code
> adb connect 192.168.X.Y:5555
> ```
>
> The Duduauto 7 does not support this; use the legacy flow above.

`--network=host` is set in `devcontainer.json` so `adb connect` reaches the device without port-forward gymnastics. If the host policy disallows host networking, the default bridge network still works for outbound `adb connect`.

## Emulator opt-in

Build the overlay image once the primary image exists:

```bash
docker build -t radio-lyric-dev .devcontainer
docker build -t radio-lyric-dev-emulator -f .devcontainer/Dockerfile.emulator .devcontainer
```

Then add these to `devcontainer.json` `runArgs` (or use a separate devcontainer config) and rebuild:

```jsonc
"runArgs": ["--network=host", "--device=/dev/kvm", "--group-add=kvm"]
```

The host must expose `/dev/kvm` and the user must be in the `kvm` group. Headless launch:

```bash
emulator -avd <name> -no-window -no-audio -gpu swiftshader_indirect
```

## Cache volumes

Two named Docker volumes persist across container rebuilds for fast incremental builds:

- `radio-lyric-gradle` → `/home/vscode/.gradle`
- `radio-lyric-android-cache` → `/home/vscode/.android`

Remove them with `docker volume rm radio-lyric-gradle radio-lyric-android-cache` if you need a clean slate.

## Static analysis (ktlint, detekt)

Kotlin static-analysis tooling is intentionally **not** baked into the image. It is added later as Gradle plugins by the app-scaffold work item so version pinning lives in the project (`build.gradle.kts`), not the container. Android Lint (`./gradlew lint`) is available out of the box.

<!-- markdownlint-disable-file -->
# Release Changes: DAB Radio + Live Lyrics Android App (Mekede USB DAB)

**Related Plan**: dab-radio-lyrics-app-plan.instructions.md
**Implementation Date**: 2026-05-01

## Summary

Scaffold the `radio-lyric` Android head-unit app: Gradle + Kotlin + Jetpack Compose
single-module project that drives a Mekede USB DAB+ dongle through a vendored
`hradio/omri-usb` AAR, plays HE-AAC v2 audio via Media3 `MediaSessionService`,
extracts DLS / DL+ artist+title metadata, fetches synced lyrics from LRCLIB
(with Room cache), exposes a Now Playing / Lyrics / Stations Compose UI,
supports a hardware-free dev loop via a `FakeRadioSource`, and autostarts on
Duduauto 7 ignition via the DUDU Settings picker plus a defensive manifest
`BootReceiver`.

## Changes

### Added

* settings.gradle.kts - Root Gradle settings; `:omri-usb` include commented out pending Phase 2 (DEV-2).
* build.gradle.kts - Top-level plugin declarations (AGP, Kotlin, Compose Compiler, Hilt, KSP) with `apply false`.
* gradle.properties - JVM args, AndroidX flags, non-transitive R class.
* gradle/libs.versions.toml - Pinned version catalog (AGP 8.5.2, Kotlin 2.0.21, Hilt 2.51.1, KSP 2.0.21-1.0.27, Media3 1.4.1, Room 2.6.1, Retrofit 2.11.0, Moshi 1.15.1, OkHttp 4.12.0, Coroutines 1.8.1).
* .gitmodules - Placeholder for `omri-usb` submodule (populated in Phase 2).
* .gitignore - Standard Android/Gradle ignores.
* local.properties - Android SDK location (gitignored).
* gradlew, gradlew.bat - Gradle 8.10.2 wrapper scripts.
* gradle/wrapper/gradle-wrapper.jar, gradle-wrapper.properties - Gradle 8.10.2 wrapper distribution descriptor.
* app/build.gradle.kts - `:app` module config (compileSdk=35, minSdk=24, targetSdk=34, arm64-v8a+armeabi-v7a ABI filters, Hilt+KSP, Media3, Room, Retrofit, Moshi, OkHttp, DataStore, Coil, Coroutines, JUnit/Truth/Turbine).
* app/proguard-rules.pro - Keep rules for Hilt, Room, Moshi, Media3, omri-usb.
* app/src/main/AndroidManifest.xml - USB host, foreground-service-mediaPlayback, MainActivity (USB_DEVICE_ATTACHED), PlaybackService, MediaButtonReceiver, BootReceiver placeholder.
* app/src/main/res/xml/mekede_dab_filter.xml - USB device filter (vendor-id=5824 / product-id=1500 = 0x16C0 / 0x05DC).
* app/src/main/res/xml/data_extraction_rules.xml - Backup/transfer rules.
* app/src/main/res/values/strings.xml - App name, channel names, accessibility strings.
* app/src/main/res/values/themes.xml - `Theme.RadioLyric` (Material3 Dark NoActionBar).
* app/src/main/kotlin/com/example/radiolyric/RadioLyricApp.kt - `@HiltAndroidApp` Application class.
* app/src/main/kotlin/com/example/radiolyric/MainActivity.kt - `@AndroidEntryPoint` ComponentActivity with placeholder `AppNavigation()`.
* app/src/main/kotlin/com/example/radiolyric/ui/theme/Theme.kt - Dark-first ColorScheme `#0A0A0A` / `#F2F2F2`.
* app/src/main/kotlin/com/example/radiolyric/ui/theme/Type.kt - In-car typography (body 18sp, headline 28-34sp, lyrics 36-44sp).
* `.gitkeep` placeholders under app/src/main/kotlin/.../, app/src/test/kotlin/.../, app/src/debug/assets/fixtures/.

### Modified

### Removed

## Additional or Deviating Changes

* DEV-1: `/home/vscode/.gradle` and `/home/vscode/.android` were owned by root; fixed inline with `sudo chown -R vscode:vscode`.
  * Reason: Devcontainer post-create did not pre-create these as the `vscode` user.
  * Follow-on: Patch devcontainer `postCreateCommand` to chown both directories.
* DEV-2: `:omri-usb` include in `settings.gradle.kts` is commented out with a TODO.
  * Reason: Phase 2 has not vendored the submodule yet; commenting allows `./gradlew help` to succeed in Phase 1.
  * Follow-on: Phase 2 Step 2.1 must uncomment the include and populate `.gitmodules`.
* DEV-3: Manifest references `@mipmap/ic_launcher`/`@mipmap/ic_launcher_round` which don't exist; `processDebugManifest` is green but `assembleDebug` will fail until launcher icons are added.
  * Follow-on: Add launcher mipmaps in Phase 8 UI work (or strip icon attributes from manifest).

## Release Summary

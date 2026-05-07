package com.example.radiolyric.data.radio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the DAB radio backend.
 *
 * Implementations:
 * - `OmriUsbRadioSource` (main source set): drives the Mekede USB tuner via the vendored
 *   `omri-usb` library. Used by release builds and by debug builds by default.
 * - `FakeRadioSource` (debug source set): emits a scripted timeline against bundled fixtures.
 *   Selected by debug builds when `-Pradio.source=fake` is passed at build time.
 *
 * This contract is intentionally pure-Kotlin (no Android, no `omri-usb`) so it can be unit-tested
 * on the JVM and so the variant split is enforced at compile time.
 */
interface RadioSource {
    /** Tuner lifecycle state. Hot — replays the latest value to new collectors. */
    val state: StateFlow<RadioState>

    /** Latest now-playing snapshot. Hot — replays the latest value to new collectors. */
    val nowPlaying: StateFlow<NowPlaying>

    /**
     * Decoded PCM audio frames (S16_LE stereo @ 48 kHz). Cold — backpressure is the consumer's job.
     */
    val audio: Flow<ByteArray>

    /** Open USB / driver resources. Idempotent. */
    suspend fun open(): Result<Unit>

    /** Tune to the given station. Cancels any in-flight tune. */
    suspend fun tune(station: Station): Result<Unit>

    /** Release all resources. After [close], the instance is unusable. */
    suspend fun close()
}

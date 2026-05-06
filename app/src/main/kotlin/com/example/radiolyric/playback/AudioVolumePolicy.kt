package com.example.radiolyric.playback

/**
 * Documents the SYU canbus volume-sync constraint observed live on the
 * Mekede DUDU7 head unit (2026-05-05 capture, see
 * `docs/target-device-facts.md` §8).
 *
 * `dumpsys audio` excerpt (verbatim):
 * ```
 * setStreamVolume(streamType=3 STREAM_MUSIC, index=36, flags=0, packageName=com.syu.ms)
 * ```
 * The `com.syu.ms` canbus daemon overwrites `STREAM_MUSIC` volume every
 * roughly 10 seconds to mirror the steering-wheel / hardware knob position.
 *
 * Implication: this app **must never** call
 * `android.media.AudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, …)`.
 * Any value we set will be reverted within seconds and will fight the OEM
 * stack, producing audible volume jumps. Adjust playback gain via Media3 /
 * ExoPlayer `Player.setVolume(Float)` instead, which is per-session and not
 * touched by the canbus daemon.
 *
 * Kept as a grep-discoverable anchor so future contributors find this note
 * when searching for `setStreamVolume` or `AudioVolumePolicy`.
 */
internal object AudioVolumePolicy {
    /** See KDoc — never call AudioManager.setStreamVolume(STREAM_MUSIC, …). */
    const val NOTE: String = "SYU canbus overwrites STREAM_MUSIC every ~10s on Mekede DUDU7"
}

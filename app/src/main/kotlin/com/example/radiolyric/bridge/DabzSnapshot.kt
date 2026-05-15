package com.example.radiolyric.bridge

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Snapshot of the upstream media producer (DAB-Z) used by the radio source layer.
 *
 * Both fields may be `null` until the controller delivers its first callback; the bridge source is
 * responsible for treating `(null, null)` as `NowPlaying.Empty`.
 */
data class DabzSnapshot(
        val metadata: MediaMetadataCompat?,
        val playback: PlaybackStateCompat?,
)

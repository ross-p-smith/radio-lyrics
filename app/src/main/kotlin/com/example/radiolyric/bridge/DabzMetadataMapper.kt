package com.example.radiolyric.bridge

import android.support.v4.media.MediaMetadataCompat
import com.example.radiolyric.data.radio.NowPlaying
import java.time.Instant

/**
 * Maps a [MediaMetadataCompat] from DAB-Z's `MediaSession` to our internal [NowPlaying] type.
 *
 * Mapping rules (from research §"Confirmed metadata mapping"):
 * - DL+ fast path: when both `METADATA_KEY_ARTIST` and `METADATA_KEY_TITLE` are non-blank, emit
 *   `NowPlaying(artist, title, source = DLPLUS)` directly.
 * - DLS fallback: otherwise feed `METADATA_KEY_DISPLAY_SUBTITLE` (or `METADATA_KEY_TITLE`) into
 *   the existing [NowPlaying.fromDls] regex parser to recover the legacy `Now playing: …` shape.
 * - `METADATA_KEY_ALBUM` is **deliberately never read** — DAB-Z fills it with `"Artist - Title"`
 *   for legacy display compat, which would corrupt the LRCLIB query if used as the album field.
 */
internal enum class DabzMappingPath {
    DLPLUS,
    DLS,
    EMPTY
}

internal data class DabzMappingResult(val nowPlaying: NowPlaying, val path: DabzMappingPath)

/**
 * Pure-Kotlin core of the mapper, isolated from `MediaMetadataCompat` so it is unit-testable on a
 * plain JVM (the compat type relies on `android.os.Bundle` which is stubbed-out in unit tests).
 */
internal fun mapToNowPlaying(
        artist: String?,
        title: String?,
        displaySubtitle: String?,
        now: () -> Instant = Instant::now,
): DabzMappingResult {
    val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
    val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }

    if (cleanArtist != null && cleanTitle != null) {
        return DabzMappingResult(
                NowPlaying(
                        artist = cleanArtist,
                        title = cleanTitle,
                        rawDls = null,
                        source = NowPlaying.Source.DLPLUS,
                        timestamp = now(),
                ),
                DabzMappingPath.DLPLUS,
        )
    }

    val dlsCandidate = displaySubtitle?.takeIf { it.isNotBlank() } ?: cleanTitle
    if (dlsCandidate != null) {
        val parsed = NowPlaying.fromDls(dlsCandidate)
        if (parsed != null) {
            return DabzMappingResult(parsed.copy(timestamp = now()), DabzMappingPath.DLS)
        }
    }
    return DabzMappingResult(NowPlaying.Empty, DabzMappingPath.EMPTY)
}

/** Production-side extension that pulls fields off the compat type and delegates to the core. */
internal fun MediaMetadataCompat?.toNowPlaying(now: () -> Instant = Instant::now): DabzMappingResult {
    val md = this ?: return DabzMappingResult(NowPlaying.Empty, DabzMappingPath.EMPTY)
    return mapToNowPlaying(
            artist = md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
            title = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE),
            displaySubtitle = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE),
            now = now,
    )
}

package com.example.radiolyric.bridge

import android.support.v4.media.MediaMetadataCompat
import com.example.radiolyric.data.radio.NowPlaying
import java.time.Instant

/**
 * Maps a [MediaMetadataCompat] from DAB-Z's `MediaSession` to our internal [NowPlaying] type.
 *
 * Mapping rules (ordered):
 * 1. **DAB-Z "Now on" path** (verified live on Mekede DUDU7, May 2026): when `TITLE` matches
 *    `Now on <station>: <artist> with <title>`, parse it. In this DAB-Z release `ARTIST` holds the
 *    station name (e.g. "Heart UK") and `ALBUM` holds the DAB ensemble (e.g. "D1 National"), so
 *    they must be ignored — only the parsed inner fields are trustworthy. Emitted as `DLPLUS`.
 * 2. **DL+ fast path**: when both `METADATA_KEY_ARTIST` and `METADATA_KEY_TITLE` are non-blank
 *    and the title is *not* a `Now on …` line, treat them as already-split DL+ fields.
 * 3. **DLS fallback**: feed `METADATA_KEY_DISPLAY_SUBTITLE` (or `METADATA_KEY_TITLE`) into the
 *    existing [NowPlaying.fromDls] regex parser to recover the legacy `Now playing: …` shape.
 * 4. `METADATA_KEY_ALBUM` is **deliberately never read** — DAB-Z fills it with the DAB ensemble
 *    label, which would corrupt the LRCLIB query if used as the album field.
 */
internal enum class DabzMappingPath {
    DLPLUS,
    DLS,
    EMPTY,
}

internal data class DabzMappingResult(val nowPlaying: NowPlaying, val path: DabzMappingPath)

// DAB-Z TITLE format observed live on Mekede DUDU7 (May 2026):
//   - Song:        "On Air Now on Heart UK: Noah Kahan with Stick Season"
//   - DJ/show:     "On Air Now on Heart UK: Ben Atkinson"            (no ` with `)
//   - Slogan/jingle: "Heart UK - turn up the feel good!"              (no `Now on` prefix)
// We accept either `On Air Now on …:` or bare `Now on …:` as the prefix; only emit DLPLUS when
// the payload contains ` with ` separating artist and title. Anything else is EMPTY so we never
// publish station slogans, DJ names, or the station-name ARTIST field as song metadata.
private val NOW_ON_REGEX =
        Regex("""^(?:On Air )?Now on [^:]+:\s*(.+?)\s+with\s+(.+)$""", RegexOption.IGNORE_CASE)
private val NOW_ON_PREFIX_REGEX =
        Regex("""^(?:On Air )?Now on [^:]+:.*$""", RegexOption.IGNORE_CASE)

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

    // 1. DAB-Z "Now on <station>: <artist> with <title>" packs the real now-playing inside the
    //    TITLE field while ARTIST holds the station name. Detect and parse first.
    if (cleanTitle != null) {
        val m = NOW_ON_REGEX.matchEntire(cleanTitle)
        if (m != null) {
            val parsedArtist = m.groupValues[1].trim()
            val parsedTitle = m.groupValues[2].trim()
            if (parsedArtist.isNotBlank() && parsedTitle.isNotBlank()) {
                return DabzMappingResult(
                        NowPlaying(
                                artist = parsedArtist,
                                title = parsedTitle,
                                rawDls = cleanTitle,
                                source = NowPlaying.Source.DLPLUS,
                                timestamp = now(),
                        ),
                        DabzMappingPath.DLPLUS,
                )
            }
            // Pattern matched but inner fields blank → treat as no song (e.g. between tracks).
            return DabzMappingResult(NowPlaying.Empty, DabzMappingPath.EMPTY)
        }
        // Title looks like a `Now on <station>:` line but has no `<artist> with <title>` payload
        // (e.g. between songs). Don't fall through to the DL+ fast path — ARTIST is the station
        // name in this DAB-Z release and would otherwise pollute downstream lyrics queries.
        if (NOW_ON_PREFIX_REGEX.matches(cleanTitle)) {
            return DabzMappingResult(NowPlaying.Empty, DabzMappingPath.EMPTY)
        }
    }

    // 2. DL+ fast path for DAB-Z releases (or other compat sources) that publish split fields.
    //    Defensive guard: if TITLE starts with `<ARTIST> -` (e.g. "Heart UK - turn up the feel
    //    good!"), it's a station slogan — DAB-Z's ARTIST field is the station name in this
    //    release. Treat as EMPTY rather than publishing the slogan as a song.
    if (cleanArtist != null && cleanTitle != null) {
        val sloganPrefix = "$cleanArtist -"
        if (cleanTitle.startsWith(sloganPrefix, ignoreCase = true)) {
            return DabzMappingResult(NowPlaying.Empty, DabzMappingPath.EMPTY)
        }
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

    // 3. DLS regex fallback.
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
internal fun MediaMetadataCompat?.toNowPlaying(
        now: () -> Instant = Instant::now
): DabzMappingResult {
    val md = this ?: return DabzMappingResult(NowPlaying.Empty, DabzMappingPath.EMPTY)
    return mapToNowPlaying(
            artist = md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
            title = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE),
            displaySubtitle = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE),
            now = now,
    )
}

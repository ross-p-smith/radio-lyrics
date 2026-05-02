package com.example.radiolyric.data.radio

import java.time.Instant

/**
 * A snapshot of "what is currently playing" derived from DAB metadata.
 *
 * The wire-format upstream (DLS / DL+) is encoded in either Latin-1 or UTF-8 depending on the
 * broadcaster's `CharSet` field. Bytes-to-`String` decoding happens in the radio source layer
 * (`OmriUsbRadioSource`) before [fromDls] is invoked, so this type is encoding-agnostic.
 */
data class NowPlaying(
        val artist: String?,
        val title: String?,
        val rawDls: String?,
        val source: Source,
        val timestamp: Instant,
) {
    enum class Source {
        DLPLUS,
        DLS,
        FAKE,
        NONE
    }

    companion object {
        /** Sentinel used as the initial value of `RadioSource.nowPlaying`. */
        val Empty =
                NowPlaying(
                        artist = null,
                        title = null,
                        rawDls = null,
                        source = Source.NONE,
                        timestamp = Instant.EPOCH,
                )

        private val PREFIX = Regex("""^\s*now playing:\s*""", RegexOption.IGNORE_CASE)
        private val SUFFIX = Regex("""\s+on\s+heart\s*$""", RegexOption.IGNORE_CASE)

        /**
         * Parse a DLS string of the form `[Now playing: ]Artist - Title[ on Heart]`.
         *
         * - Strips a leading `Now playing:` prefix and a trailing ` on Heart` suffix.
         * - Splits on the **first** ` - ` so that titles containing ` - ` (e.g. `Foo - Bar - Baz`)
         * yield `artist = "Foo"`, `title = "Bar - Baz"`.
         * - Returns `null` if the string is blank or has no ` - ` separator.
         */
        fun fromDls(dls: String): NowPlaying? {
            val trimmed = dls.trim()
            if (trimmed.isEmpty()) return null
            val withoutPrefix = PREFIX.replaceFirst(trimmed, "")
            val withoutSuffix = SUFFIX.replaceFirst(withoutPrefix, "").trim()
            val splitIndex = withoutSuffix.indexOf(" - ")
            if (splitIndex <= 0) return null
            val artist = withoutSuffix.substring(0, splitIndex).trim()
            val title = withoutSuffix.substring(splitIndex + 3).trim()
            if (artist.isEmpty() || title.isEmpty()) return null
            return NowPlaying(
                    artist = artist,
                    title = title,
                    rawDls = dls,
                    source = Source.DLS,
                    timestamp = Instant.now(),
            )
        }
    }
}

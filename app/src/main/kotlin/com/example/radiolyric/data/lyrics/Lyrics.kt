package com.example.radiolyric.data.lyrics

/** A single timestamped lyric line. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Result of a lyrics lookup. Always one of three flavors:
 * - [None]: nothing found / lookup failed (best-effort policy: never throws upward).
 * - [Plain]: lyrics body without timestamps (LRCLIB `plainLyrics`).
 * - [Synced]: timestamped lyric lines parsed from LRCLIB `syncedLyrics`.
 */
sealed interface Lyrics {
    data object None : Lyrics
    data class Plain(val text: String) : Lyrics
    data class Synced(val lines: List<LyricLine>) : Lyrics
}

package com.example.radiolyric.data.lyrics

import javax.inject.Inject

/**
 * Parses an LRC-format lyrics body into timestamped [LyricLine]s.
 *
 * Supported syntax:
 * - Single timestamp: `[mm:ss.xx]Lyric text`
 * - Multiple timestamps: `[01:23.45][03:45.67]Repeated chorus line`
 * - Two- and three-digit fractions: `[01:23.45]` or `[01:23.456]`
 * - Colon separator on fractions: `[01:23:45]` (treated as `01:23.45`)
 * - Metadata tags (`[ar:Foo]`, `[ti:Bar]`, `[length:03:21]`) are ignored.
 * - Blank lyric body emits `LyricLine(time, "")` so consumers can render an empty beat.
 *
 * Output is sorted ascending by [LyricLine.timeMs].
 */
class LrcParser @Inject constructor() {

    fun parse(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()
        val out = mutableListOf<LyricLine>()
        for (rawLine in lrc.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isEmpty()) continue

            val text = TIMESTAMP_REGEX.replace(line, "").trim()
            for (m in matches) {
                val timeMs = m.toMillis() ?: continue
                out.add(LyricLine(timeMs, text))
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun MatchResult.toMillis(): Long? {
        val minutes = groupValues[1].toLongOrNull() ?: return null
        val seconds = groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60) return null
        val fracRaw = groupValues.getOrNull(3).orEmpty()
        // LRC metadata tags like `[ar:Harry Styles]` parse to minutes=ar (fails toLong) → returns
        // null above. Length tags `[length:03:21]` look like timestamps but with metadata-style
        // bracket usage; defensively cap at sane minute counts.
        if (minutes > 999) return null
        val fracMs =
                when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100L
                    2 -> fracRaw.toLong() * 10L
                    3 -> fracRaw.toLong()
                    else -> fracRaw.substring(0, 3).toLong()
                }
        return minutes * 60_000L + seconds * 1_000L + fracMs
    }

    private companion object {
        // [mm:ss.fff] or [mm:ss:fff] (fractional separator may be . or :)
        private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    }
}

package com.example.radiolyric.data.lyrics

import com.example.radiolyric.data.local.LyricsCacheDao
import com.example.radiolyric.data.local.LyricsCacheEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout

/**
 * Best-effort lyrics lookup. Cache hits skip the network. Network calls are time-boxed at 3 s and
 * any failure (timeout, IO, JSON, ...) collapses to [Lyrics.None]. Never throws upward.
 */
@Singleton
class LyricsRepository
@Inject
constructor(
        private val api: LrcLibApi,
        private val cache: LyricsCacheDao,
        private val parser: LrcParser,
) {

    suspend fun lookup(artist: String, title: String): Lyrics {
        if (artist.isBlank() || title.isBlank()) return Lyrics.None

        cache.find(artist, title)?.let { return it.toLyrics() }

        val track =
                runCatching {
                            withTimeout(NETWORK_TIMEOUT) {
                                api.search(track = title, artist = artist).firstOrNull()
                            }
                        }
                        .getOrNull()
                        ?: return Lyrics.None

        val entity =
                LyricsCacheEntity(
                        artist = artist,
                        title = title,
                        syncedLyrics = track.syncedLyrics,
                        plainLyrics = track.plainLyrics,
                        provider = PROVIDER_LRCLIB,
                        fetchedAt = System.currentTimeMillis(),
                )
        runCatching { cache.upsert(entity) }
        return entity.toLyrics()
    }

    private fun LyricsCacheEntity.toLyrics(): Lyrics {
        if (!syncedLyrics.isNullOrBlank()) {
            val lines = parser.parse(syncedLyrics)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        if (!plainLyrics.isNullOrBlank()) return Lyrics.Plain(plainLyrics)
        return Lyrics.None
    }

    private companion object {
        private val NETWORK_TIMEOUT = 3.seconds
        private const val PROVIDER_LRCLIB = "lrclib"
    }
}

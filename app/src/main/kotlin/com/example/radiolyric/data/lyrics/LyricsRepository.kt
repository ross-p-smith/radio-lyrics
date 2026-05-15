package com.example.radiolyric.data.lyrics

import com.example.radiolyric.data.local.LyricsCacheDao
import com.example.radiolyric.data.local.LyricsCacheEntity
import com.example.radiolyric.devtools.AppLog as Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ensureActive
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
        Log.i(TAG, "lookup: artist='$artist' title='$title'")

        cache.find(artist, title)?.let {
            Log.i(TAG, "lookup: source=CACHE artist='$artist' title='$title'")
            Log.i(TAG, "lookup: cache hit for '$artist' / '$title'")
            return it.toLyrics()
        }

        Log.i(TAG, "lookup: source=NETWORK artist='$artist' title='$title'")

        val track =
                runCatching {
                            withTimeout(NETWORK_TIMEOUT) {
                                api.search(track = title, artist = artist).firstOrNull()
                            }
                        }
                        .onFailure { e ->
                            // True upstream cancellation (caller cancelled the collecting flow,
                            // e.g. station change) must propagate so the surrounding
                            // transformLatest unwinds cleanly. A TimeoutCancellationException
                            // raised from inside withTimeout, however, is a real lookup failure
                            // — the caller is still active, so ensureActive() does NOT throw and
                            // we collapse to Lyrics.None like any other network error.
                            coroutineContext.ensureActive()
                            Log.w(TAG, "lookup: LRCLIB search failed for '$artist' / '$title'", e)
                        }
                        .getOrNull()
        if (track == null) {
            Log.i(TAG, "lookup: LRCLIB returned no match for '$artist' / '$title'")
            return Lyrics.None
        }
        Log.i(
                TAG,
                "lookup: LRCLIB hit id=${track.id} synced=${!track.syncedLyrics.isNullOrBlank()} plain=${!track.plainLyrics.isNullOrBlank()}",
        )

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
        private const val TAG = "LyricsRepository"
        // LRCLIB searches routinely take 5-8s end-to-end (slow upstream + TLS handshake on
        // car-head-unit networks), so 3s was too aggressive and timed out every real lookup.
        private val NETWORK_TIMEOUT = 15.seconds
        private const val PROVIDER_LRCLIB = "lrclib"
    }
}

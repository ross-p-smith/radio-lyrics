package com.example.radiolyric.data.lyrics

import android.content.Context
import com.example.radiolyric.data.local.LyricsCacheDao
import com.example.radiolyric.data.local.LyricsCacheEntity
import com.example.radiolyric.devtools.AppLog as Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class LyricsCacheSeedLoader
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val cacheDao: LyricsCacheDao,
) {

    suspend fun seedFromAssets() = withContext(Dispatchers.IO) {
        val lines = runCatching {
                    context.assets.open(SEED_FILE).bufferedReader().use { reader ->
                        reader.readLines().filter { it.isNotBlank() }
                    }
                }
                .onFailure {
                    Log.i(TAG, "seed: no shipped lyrics seed file found at '$SEED_FILE'")
                }
                .getOrNull()
                ?: return@withContext

        if (lines.isEmpty()) return@withContext

        var upserts = 0
        lines.forEach { line ->
            runCatching {
                        val json = JSONObject(line)
                        val artist = json.optString("artist").trim()
                        val title = json.optString("title").trim()
                        if (artist.isBlank() || title.isBlank()) return@runCatching

                        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
                        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
                        if (synced == null && plain == null) return@runCatching

                        cacheDao.upsert(
                                LyricsCacheEntity(
                                        artist = artist,
                                        title = title,
                                        syncedLyrics = synced,
                                        plainLyrics = plain,
                                        provider = json.optString("provider").ifBlank { "seed" },
                                        fetchedAt = System.currentTimeMillis(),
                                ),
                        )
                        upserts += 1
                    }
                    .onFailure { e ->
                        Log.w(TAG, "seed: failed to parse one seed line", e)
                    }
        }

        Log.i(TAG, "seed: upserted $upserts lyric entries from assets")
    }

    private companion object {
        private const val TAG = "LyricsCacheSeed"
        private const val SEED_FILE = "lyrics/seed_lyrics_cache.jsonl"
    }
}

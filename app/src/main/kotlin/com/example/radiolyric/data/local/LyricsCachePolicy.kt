package com.example.radiolyric.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * Trims [LyricsCacheDao] to the lyrics-cache budget:
 * - rows older than [TTL] are deleted outright;
 * - any remaining rows beyond [MAX_ROWS] are evicted oldest-first.
 *
 * Called once from `RadioLyricApp.onCreate` on a background coroutine.
 */
@Singleton
class LyricsCachePolicy @Inject constructor(private val dao: LyricsCacheDao) {

    suspend fun evict(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - TTL.inWholeMilliseconds
        val byTtl = dao.deleteOlderThan(cutoff)
        val byCap = dao.trimToMax(MAX_ROWS)
        return byTtl + byCap
    }

    private companion object {
        private val TTL = 30.days
        private const val MAX_ROWS = 2000
    }
}

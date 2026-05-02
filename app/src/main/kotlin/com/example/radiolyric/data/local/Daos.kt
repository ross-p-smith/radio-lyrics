package com.example.radiolyric.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY pinned DESC, label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE sid = :sid LIMIT 1")
    suspend fun findBySid(sid: Int): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stations: List<StationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: StationEntity)

    @Query("UPDATE stations SET pinned = :pinned WHERE sid = :sid")
    suspend fun setPinned(sid: Int, pinned: Boolean)
}

@Dao
interface LastTunedDao {
    @Query("SELECT * FROM last_tuned WHERE id = 0 LIMIT 1")
    suspend fun get(): LastTunedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(entity: LastTunedEntity)
}

@Dao
interface LyricsCacheDao {
    @Query(
            """
            SELECT * FROM lyrics_cache
            WHERE artist = :artist COLLATE NOCASE
              AND title  = :title  COLLATE NOCASE
            LIMIT 1
            """,
    )
    suspend fun find(artist: String, title: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE fetched_at < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM lyrics_cache") suspend fun count(): Int

    /**
     * Trims the oldest rows so at most [maxRows] remain. Returns rows deleted. Implemented as a
     * subquery rather than a window function to keep SQLite portable.
     */
    @Query(
            """
            DELETE FROM lyrics_cache
            WHERE rowid IN (
                SELECT rowid FROM lyrics_cache
                ORDER BY fetched_at ASC
                LIMIT MAX(0, (SELECT COUNT(*) FROM lyrics_cache) - :maxRows)
            )
            """,
    )
    suspend fun trimToMax(maxRows: Int): Int
}

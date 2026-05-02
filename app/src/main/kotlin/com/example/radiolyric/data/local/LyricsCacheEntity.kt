package com.example.radiolyric.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Cached LRCLIB lookup keyed by `(artist, title)` with case-insensitive collation so a slight
 * casing difference between DL+ and the canonical track name still hits cache.
 */
@Entity(
        tableName = "lyrics_cache",
        primaryKeys = ["artist", "title"],
        indices = [Index(value = ["artist", "title"], unique = true)],
)
data class LyricsCacheEntity(
        @ColumnInfo(collate = ColumnInfo.NOCASE) val artist: String,
        @ColumnInfo(collate = ColumnInfo.NOCASE) val title: String,
        @ColumnInfo(name = "synced_lyrics") val syncedLyrics: String?,
        @ColumnInfo(name = "plain_lyrics") val plainLyrics: String?,
        val provider: String,
        @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

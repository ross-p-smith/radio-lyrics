package com.example.radiolyric.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the most recently tuned station + last known volume.
 *
 * `id` is fixed to `0` so an `INSERT ... ON CONFLICT REPLACE` always overwrites the same row.
 */
@Entity(tableName = "last_tuned")
data class LastTunedEntity(
        @PrimaryKey val id: Int = 0,
        val stationSid: Int,
        val volume: Int,
)

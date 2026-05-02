package com.example.radiolyric.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A discoverable DAB station (scanned or seeded). */
@Entity(tableName = "stations")
data class StationEntity(
        @PrimaryKey val sid: Int,
        val eid: Int,
        @ColumnInfo(name = "frequency_khz") val frequencyKhz: Int,
        val label: String,
        val pinned: Boolean = false,
        @ColumnInfo(name = "last_seen_rssi") val lastSeenRssi: Int? = null,
)

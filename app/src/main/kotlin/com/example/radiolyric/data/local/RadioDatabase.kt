package com.example.radiolyric.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
        entities = [StationEntity::class, LastTunedEntity::class, LyricsCacheEntity::class],
        version = 1,
        exportSchema = false,
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun lastTunedDao(): LastTunedDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
}

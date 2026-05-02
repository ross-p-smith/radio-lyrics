package com.example.radiolyric.di

import android.content.Context
import androidx.room.Room
import com.example.radiolyric.data.local.LastTunedDao
import com.example.radiolyric.data.local.LyricsCacheDao
import com.example.radiolyric.data.local.RadioDatabase
import com.example.radiolyric.data.local.StationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): RadioDatabase =
            Room.databaseBuilder(ctx, RadioDatabase::class.java, "radio.db")
                    .fallbackToDestructiveMigration()
                    .build()

    @Provides fun provideStationDao(db: RadioDatabase): StationDao = db.stationDao()

    @Provides fun provideLastTunedDao(db: RadioDatabase): LastTunedDao = db.lastTunedDao()

    @Provides fun provideLyricsCacheDao(db: RadioDatabase): LyricsCacheDao = db.lyricsCacheDao()
}

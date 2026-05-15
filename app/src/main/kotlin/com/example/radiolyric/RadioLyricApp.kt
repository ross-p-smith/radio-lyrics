package com.example.radiolyric

import android.app.Application
import com.example.radiolyric.data.lyrics.LyricsCacheSeedLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hilt application root. Phase 1 stub — additional initialization (logging, Room pre-warming,
 * notification channels) lands in later phases.
 */
@HiltAndroidApp
class RadioLyricApp : Application() {

    @Inject lateinit var lyricsCacheSeedLoader: LyricsCacheSeedLoader

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { lyricsCacheSeedLoader.seedFromAssets() }
    }
}

package com.example.radiolyric.playback

import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.radiolyric.data.radio.RadioSource
import com.example.radiolyric.data.radio.Stations
import com.example.radiolyric.data.radio.UsbDispatchers
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Foreground [MediaSessionService] that owns the [RadioSource] lifecycle, pumps PCM into
 * [AudioPump], and surfaces DL+ metadata to the lock-screen via [RadioPlayer].
 *
 * Lifecycle:
 * - `onCreate`: build the [MediaSession] and start collecting `nowPlaying` + `audio`.
 * - `onStartCommand`: open the radio source and tune the default station (Heart UK).
 * - `onDestroy`: tear down everything.
 *
 * The Media3 framework auto-publishes the foreground notification via the default
 * `MediaNotificationProvider` once the session is registered, so we only need to ensure the
 * notification channel exists.
 */
@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    @Inject lateinit var radioSource: RadioSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audioJob: Job? = null
    private var metaJob: Job? = null

    private val audioPump = AudioPump()
    private lateinit var player: RadioPlayer
    private var session: MediaSession? = null
    private var openInProgress = false

    override fun onCreate() {
        super.onCreate()
        PlaybackNotification.ensureChannel(this)
        player =
                RadioPlayer(
                        looper = mainLooper,
                        onPlayWhenReadyChange = { wantsPlay ->
                            if (wantsPlay) audioPump.resume() else audioPump.pause()
                        },
                )
        session =
                MediaSession.Builder(this, player)
                        .setId("radio-lyric-session")
                        .build()

        startCollectingMetadata()
        startCollectingAudio()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!openInProgress && session != null) {
            openInProgress = true
            scope.launch {
                runCatching {
                            radioSource.open().getOrThrow()
                            radioSource.tune(Stations.HeartUK).getOrThrow()
                        }
                        .onFailure { Log.w(TAG, "Initial tune failed", it) }
                openInProgress = false
            }
            // Reflect the autostart intent on the player so the notification shows "playing".
            player.setNowPlaying(
                    artist = null,
                    title = Stations.HeartUK.label,
                    source = "DAB",
            )
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        audioJob?.cancel()
        metaJob?.cancel()
        scope.cancel()
        audioPump.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        // Detach radio source on a final non-suspending hop; if this is too slow the JVM still
        // tears down the process.
        scope.launch { runCatching { radioSource.close() } }
        super.onDestroy()
    }

    private fun startCollectingAudio() {
        audioJob =
                scope.launch(UsbDispatchers.Reader) {
                    radioSource.audio.collect { frame -> audioPump.write(frame) }
                }
    }

    private fun startCollectingMetadata() {
        metaJob =
                scope.launch {
                    @OptIn(kotlinx.coroutines.FlowPreview::class)
                    radioSource.nowPlaying
                            .distinctUntilChangedBy { it.artist to it.title }
                            .debounce(METADATA_DEBOUNCE_MS)
                            .collectLatest { np ->
                                player.setNowPlaying(
                                        artist = np.artist,
                                        title = np.title,
                                        source = np.source.name,
                                )
                            }
                }
    }

    private companion object {
        private const val TAG = "PlaybackService"
        private const val METADATA_DEBOUNCE_MS = 2_000L
    }
}

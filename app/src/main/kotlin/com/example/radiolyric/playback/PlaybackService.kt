package com.example.radiolyric.playback

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.radiolyric.BuildConfig
import com.example.radiolyric.data.radio.RadioSource
import com.example.radiolyric.data.radio.Stations
import com.example.radiolyric.data.radio.UsbDispatchers
import com.example.radiolyric.devtools.AppLog as Log
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
 *
 * **DAB-Z mode (`BuildConfig.RADIO_SOURCE == "dabz"`):** the service still runs as a foreground
 * service so the OS keeps us alive during a drive, but skips Media3 `MediaSession` construction,
 * `RadioPlayer`, the audio pump, and the auto-tune call. DAB-Z owns the tuner and audio render; we
 * are a read-only metadata consumer (see `DabzBridgeRadioSource` and DD-06 in the planning log).
 * The session-less foreground notification reflects the current `NowPlaying` from DAB-Z.
 */
@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    @Inject lateinit var radioSource: RadioSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audioJob: Job? = null
    private var metaJob: Job? = null

    private val audioPump = AudioPump()
    private var player: RadioPlayer? = null
    private var session: MediaSession? = null
    private var openInProgress = false

    private val isDabzMode: Boolean
        get() = BuildConfig.RADIO_SOURCE == "dabz"

    override fun onCreate() {
        super.onCreate()
        // AudioVolumePolicy: never call AudioManager.setStreamVolume(STREAM_MUSIC, …)
        // — see AudioVolumePolicy.kt (Mekede DUDU7 SYU canbus overwrites it every ~10s).
        PlaybackNotification.ensureChannel(this)

        if (isDabzMode) {
            Log.i(TAG, "DAB-Z mode: skipping MediaSession/RadioPlayer/AudioPump construction")
            startCollectingMetadata()
            return
        }

        val newPlayer =
                RadioPlayer(
                        looper = mainLooper,
                        onPlayWhenReadyChange = { wantsPlay ->
                            if (wantsPlay) audioPump.resume() else audioPump.pause()
                        },
                )
        player = newPlayer
        session = MediaSession.Builder(this, newPlayer).setId("radio-lyric-session").build()

        startCollectingMetadata()
        startCollectingAudio()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // WI-05: Android 13+ kills any service started via startForegroundService() that does not
        // call startForeground() within ~10s. Tuner open() + tune() can comfortably run longer
        // than that (full DAB band-III scan is ~2 minutes), so we publish a placeholder
        // "Preparing…" notification immediately. Media3's DefaultMediaNotificationProvider will
        // replace it with the real now-playing notification once the MediaSession goes active.
        val preparing: Notification =
                if (isDabzMode)
                        PlaybackNotification.buildBridge(
                                this,
                                currentNp = radioSource.nowPlaying.value
                        )
                else PlaybackNotification.buildPreparing(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    PlaybackNotification.FOREGROUND_NOTIFICATION_ID,
                    preparing,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(PlaybackNotification.FOREGROUND_NOTIFICATION_ID, preparing)
        }

        if (isDabzMode) {
            if (!openInProgress) {
                openInProgress = true
                scope.launch {
                    runCatching { radioSource.open().getOrThrow() }.onFailure {
                        Log.w(TAG, "DAB-Z bridge open() failed", it)
                    }
                    openInProgress = false
                }
            }
            return START_STICKY
        }

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
            player?.setNowPlaying(
                    artist = null,
                    title = Stations.HeartUK.label,
                    source = "DAB",
            )
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // DAB-Z mode: defence-in-depth — never expose a session even if Media3 binds us.
        if (isDabzMode) return null
        return session
    }

    override fun onDestroy() {
        audioJob?.cancel()
        metaJob?.cancel()
        scope.cancel()
        audioPump.release()
        session?.run {
            player?.release()
            release()
        }
        session = null
        player = null
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
                    radioSource
                            .nowPlaying
                            .distinctUntilChangedBy { it.artist to it.title }
                            .debounce(METADATA_DEBOUNCE_MS)
                            .collectLatest { np ->
                                if (isDabzMode) {
                                    refreshDabzNotification(np)
                                } else {
                                    player?.setNowPlaying(
                                            artist = np.artist,
                                            title = np.title,
                                            source = np.source.name,
                                    )
                                }
                            }
                }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun refreshDabzNotification(np: com.example.radiolyric.data.radio.NowPlaying) {
        val notification = PlaybackNotification.buildBridge(this, currentNp = np)
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        // POST_NOTIFICATIONS is requested in MainActivity; missing-permission only causes a no-op
        // notify() rather than a crash, so the lint warning is intentionally suppressed.
        runCatching { nm.notify(PlaybackNotification.FOREGROUND_NOTIFICATION_ID, notification) }
                .onFailure { Log.w(TAG, "DAB-Z notification refresh failed", it) }
    }

    private companion object {
        private const val TAG = "PlaybackService"
        private const val METADATA_DEBOUNCE_MS = 2_000L
    }
}

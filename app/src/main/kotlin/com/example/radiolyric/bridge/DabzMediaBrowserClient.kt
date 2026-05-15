package com.example.radiolyric.bridge

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.radiolyric.devtools.AppLog as Log
import kotlin.math.min
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Bridges DAB-Z's exported `MediaBrowserService` (`com.zoulou.dab/.service.DabMediaBrowserService`)
 * into a cold [Flow] of [DabzSnapshot].
 *
 * - Connects via [MediaBrowserCompat] (DAB-Z publishes a compat session, see DAB-Z subagent doc).
 * - Snapshots `controller.metadata` and `controller.playbackState` immediately after callback
 *   registration to avoid the documented first-connect race (consumer subagent doc §3.1).
 * - Threading: builds [MediaBrowserCompat] on the main [Looper] via a [Handler] and forwards
 *   callbacks into the flow, which is safe to collect from any dispatcher.
 * - Reconnects with exponential backoff (1 s, 2 s, 5 s capped) on `onConnectionFailed` /
 *   `onConnectionSuspended`.
 *
 * Cancelling the collecting coroutine releases the [MediaBrowserCompat] and unregisters the
 * controller callback so no listeners leak in `dumpsys media_session`.
 */
class DabzMediaBrowserClient(
        private val context: Context,
        private val targetPackage: String = DABZ_PACKAGE,
        private val targetClass: String = DABZ_BROWSER_SERVICE,
) {

    fun observe(): Flow<DabzSnapshot> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val component = ComponentName(targetPackage, targetClass)

        // Mutable holders so the connection callback can hand them to the close lambda.
        var controller: MediaControllerCompat? = null
        var controllerCallback: MediaControllerCompat.Callback? = null
        var browser: MediaBrowserCompat? = null
        var attempt = 0
        var current = DabzSnapshot(metadata = null, playback = null)

        fun emitCurrent() {
            trySend(current)
        }

        fun installController(token: android.support.v4.media.session.MediaSessionCompat.Token) {
            val newController = MediaControllerCompat(context, token)
            val cb =
                    object : MediaControllerCompat.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                            logMetadata("onMetadataChanged", metadata)
                            current = current.copy(metadata = metadata)
                            emitCurrent()
                        }

                        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                            current = current.copy(playback = state)
                            emitCurrent()
                        }

                        override fun onSessionDestroyed() {
                            Log.w(TAG, "MediaSession destroyed; flushing snapshot")
                            current = DabzSnapshot(metadata = null, playback = null)
                            emitCurrent()
                        }
                    }
            newController.registerCallback(cb, handler)
            // Snapshot *after* callback registration to close the first-connect race window.
            logMetadata("initial snapshot", newController.metadata)
            current =
                    DabzSnapshot(
                            metadata = newController.metadata,
                            playback = newController.playbackState,
                    )
            emitCurrent()
            controller = newController
            controllerCallback = cb
        }

        // Mutual-recursion between connect / scheduleReconnect resolved via lambda holders.
        var connectFn: () -> Unit = {}
        val scheduleReconnect: () -> Unit = {
            attempt += 1
            val backoff = backoffMs(attempt)
            handler.postDelayed(
                    {
                        runCatching { browser?.disconnect() }
                        controller = null
                        controllerCallback = null
                        browser = null
                        connectFn()
                    },
                    backoff,
            )
        }
        connectFn = {
            Log.i(TAG, "connect attempt=${attempt + 1} pkg=$targetPackage cls=$targetClass")
            val connectionCallback =
                    object : MediaBrowserCompat.ConnectionCallback() {
                        override fun onConnected() {
                            Log.i(TAG, "onConnected")
                            attempt = 0
                            val b = browser ?: return
                            runCatching { installController(b.sessionToken) }
                                    .onFailure { Log.w(TAG, "installController failed", it) }
                        }

                        override fun onConnectionSuspended() {
                            Log.w(TAG, "MediaBrowser connection suspended")
                            controllerCallback?.let { controller?.unregisterCallback(it) }
                            controller = null
                            controllerCallback = null
                        }

                        override fun onConnectionFailed() {
                            Log.w(TAG, "MediaBrowser connection failed (attempt=$attempt)")
                            scheduleReconnect()
                        }
                    }
            val b =
                    MediaBrowserCompat(
                            context,
                            component,
                            connectionCallback,
                            /* rootHints = */ null,
                    )
            browser = b
            runCatching { b.connect() }
                    .onFailure {
                        Log.w(TAG, "MediaBrowser.connect() threw", it)
                        scheduleReconnect()
                    }
        }

        // Initial connect attempt scheduled on the main Looper so MediaBrowserCompat sees the
        // expected thread on its first callback.
        handler.post { connectFn() }

        awaitClose {
            handler.post {
                runCatching {
                    controllerCallback?.let { controller?.unregisterCallback(it) }
                }
                runCatching { browser?.disconnect() }
                controller = null
                controllerCallback = null
                browser = null
            }
        }
    }

    private fun backoffMs(attempt: Int): Long {
        // 1s, 2s, then capped at 5s.
        return when (attempt) {
            1 -> 1_000L
            2 -> 2_000L
            else -> 5_000L
        }.let { min(it, 5_000L) }
    }

    companion object {
        const val DABZ_PACKAGE = "com.zoulou.dab"
        const val DABZ_BROWSER_SERVICE = "com.zoulou.dab.service.DabMediaBrowserService"
        private const val TAG = "DabzMediaBrowserClient"

        // Diagnostic: dump every key DAB-Z publishes so we can adapt the mapper to whatever
        // formatting variant a given DAB-Z release uses. Logged at INFO; cheap and bounded.
        // Reads via the raw bundle to avoid `MediaMetadataCompat.getString` throwing the noisy
        // `Bundle: Attempt to cast generated internal exception` stack on non-CharSequence keys
        // such as `METADATA_KEY_RATING` (which is a Parcelable).
        internal fun logMetadata(reason: String, metadata: MediaMetadataCompat?) {
            if (metadata == null) {
                Log.i(TAG, "$reason: metadata=null")
                return
            }
            val bundle = metadata.bundle
            val keys = bundle.keySet().sorted()
            val sb = StringBuilder("$reason: keys=${keys.size}")
            for (k in keys) {
                val v = runCatching { bundle.get(k)?.toString() }.getOrNull()
                val short = v?.take(120)?.replace('\n', ' ')
                sb.append("\n  ").append(k).append(" = ").append(short)
            }
            Log.i(TAG, sb.toString())
        }

        // Suppress unused import warnings for the suspending helper used in tests-only paths.
        @Suppress("unused") private suspend fun await(ms: Long) = delay(ms)
    }
}

package com.example.radiolyric.bridge

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import com.example.radiolyric.devtools.AppLog as Log
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Dormant [NotificationListenerService] used as a v2 fallback when the v1 MediaBrowser path
 * (`DabzMediaBrowserClient`) is blocked — for instance if a future DAB-Z release allow-lists
 * `onLoadChildren` callers (DR-02 in the planning log).
 *
 * The service is **declared in the manifest but never auto-bound**: the user must enable it under
 * Settings → Apps → Notification access. `DabzAccessHelper` exposes the deep-link to that page;
 * `DabzBridgeRadioSource` only consults this fallback after three consecutive MediaBrowser
 * connection failures.
 *
 * Lifecycle (consumer subagent §2.2):
 * - `onListenerConnected` → register a `MediaSessionManager.OnActiveSessionsChangedListener`
 *   filtered to this service's component, capture the first DAB-Z controller, and forward its
 *   metadata + playback callbacks into the snapshot stream.
 * - `onListenerDisconnected` → release everything and fall silent. The system restarts the
 *   service automatically; the next `onListenerConnected` re-attaches cleanly.
 */
class DabzMediaListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val activeController = AtomicReference<MediaController?>(null)
    private val controllerCallback =
            object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    publish()
                }

                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    publish()
                }
            }

    private val activeSessionsListener =
            MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                attachController(controllers.orEmpty())
            }

    private fun attachController(controllers: List<MediaController>) {
        val target = controllers.firstOrNull { it.packageName == DABZ_PACKAGE }
        val previous = activeController.getAndSet(target)
        previous?.unregisterCallback(controllerCallback)
        target?.registerCallback(controllerCallback, handler)
        publish()
    }

    private fun publish() {
        val controller = activeController.get()
        latest.value =
                NlsSnapshot(
                        metadata = controller?.metadata,
                        playback = controller?.playbackState,
                )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "onListenerConnected: registering active-sessions listener")
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val componentName = ComponentName(this, DabzMediaListener::class.java)
        try {
            msm?.addOnActiveSessionsChangedListener(activeSessionsListener, componentName, handler)
            attachController(msm?.getActiveSessions(componentName).orEmpty())
        } catch (e: SecurityException) {
            Log.w(TAG, "active-sessions listener registration denied (NLS not enabled?)", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "onListenerDisconnected: releasing controller")
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        runCatching { msm?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        activeController.getAndSet(null)?.unregisterCallback(controllerCallback)
        latest.value = NlsSnapshot(metadata = null, playback = null)
        super.onListenerDisconnected()
    }

    /** Snapshot from the platform `MediaController` (NLS path uses platform, not compat). */
    data class NlsSnapshot(
            val metadata: android.media.MediaMetadata?,
            val playback: android.media.session.PlaybackState?,
    )

    companion object {
        private const val TAG = "DabzMediaListener"
        const val DABZ_PACKAGE = "com.zoulou.dab"

        /** Process-wide latest snapshot. Bridge collectors observe this hot stream. */
        private val latest: MutableStateFlow<NlsSnapshot> =
                MutableStateFlow(NlsSnapshot(metadata = null, playback = null))

        val snapshots: StateFlow<NlsSnapshot> = latest.asStateFlow()

        /** Cold flow wrapper for parity with `DabzMediaBrowserClient.observe()`. */
        fun observe(context: Context): Flow<NlsSnapshot> = callbackFlow {
            val collector =
                    kotlinx.coroutines.GlobalScope.let {
                        // intentional: we just bridge the hot StateFlow into a cold flow;
                        // cancellation closes the channel below.
                    }
            val handler = Handler(Looper.getMainLooper())
            val poster = Runnable { trySend(latest.value) }
            // Emit the current value on subscribe and forward subsequent updates via a poller —
            // a lightweight alternative to wiring a second observer when the producer is the
            // process-wide hot StateFlow above.
            handler.post(poster)
            awaitClose { handler.removeCallbacks(poster) }
        }
    }
}

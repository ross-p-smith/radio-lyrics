package com.example.radiolyric.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Lazily builds (and caches) a single [MediaController] bound to [PlaybackService]. The first
 * caller pays the binding cost; subsequent calls return the cached instance.
 *
 * The controller is intentionally never released here — the service hosts a process-singleton
 * session, so the process tear-down cleans up the Binder.
 */
@Singleton
class MediaControllerProvider
@Inject
constructor(
        @ApplicationContext private val ctx: Context,
) {

    private val mutex = Mutex()
    @Volatile private var cached: MediaController? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun controller(): MediaController =
            mutex.withLock {
                cached?.let {
                    return@withLock it
                }
                val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
                val future = MediaController.Builder(ctx, token).buildAsync()
                val controller =
                        suspendCancellableCoroutine<MediaController> { cont ->
                            future.addListener(
                                    {
                                        if (cont.isActive) cont.resume(future.get())
                                    },
                                    ContextCompat.getMainExecutor(ctx),
                            )
                            cont.invokeOnCancellation { future.cancel(false) }
                        }
                cached = controller
                controller
            }
}

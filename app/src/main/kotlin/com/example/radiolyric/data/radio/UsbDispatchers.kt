package com.example.radiolyric.data.radio

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Dedicated dispatchers for USB DAB I/O.
 *
 * Both PCM frames and DAB metadata callbacks arrive on JNI threads owned by `omri-usb`. We funnel
 * them onto a single named worker so backpressure is observable in profilers (`dab-usb-reader`)
 * and so the audio pump never contends with the UI thread or `Dispatchers.IO`.
 */
internal object UsbDispatchers {
    val Reader: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                        Thread(runnable, "dab-usb-reader").apply {
                            priority = Thread.NORM_PRIORITY + 1
                            isDaemon = true
                        }
                    }
                    .asCoroutineDispatcher()
}

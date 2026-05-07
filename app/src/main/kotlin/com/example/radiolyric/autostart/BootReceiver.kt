package com.example.radiolyric.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.example.radiolyric.devtools.AppLog as Log
import com.example.radiolyric.playback.PlaybackService

/**
 * Wakes the foreground [PlaybackService] on a wide range of OS-/OEM-defined boot signals.
 *
 * Why so many actions? Mekede / FYT / Microntek head-units don't always emit `BOOT_COMPLETED`
 * (especially on warm-boot via ACC). They each ship slightly different ACC-on broadcasts. We
 * register every plausible one to maximise reliability across firmware variants.
 *
 * The intent-filter list is in `AndroidManifest.xml` (so the static check is build-time).
 */
class BootReceiver : BroadcastReceiver() {
    @UnstableApi
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Autostart trigger: $action")
        val svc =
                Intent(context, PlaybackService::class.java).apply {
                    putExtra(EXTRA_TRIGGER, action)
                }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, svc)
            } else {
                context.startService(svc)
            }
        } catch (t: Throwable) {
            // Some OEM firmwares deliver these broadcasts before user-unlock; if the service
            // can't start yet we just swallow — USB attach or app launch will retry.
            Log.w(TAG, "Could not start PlaybackService for $action", t)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        /**
         * Extra carrying the originating broadcast action so [PlaybackService] can record telemetry
         * / trigger source for diagnostics.
         */
        const val EXTRA_TRIGGER: String = "trigger"
    }
}

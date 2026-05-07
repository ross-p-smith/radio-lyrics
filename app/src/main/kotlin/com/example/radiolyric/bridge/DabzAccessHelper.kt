package com.example.radiolyric.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Helper for detecting whether the user has granted us Notification access (required to bind
 * `DabzMediaListener`) and for opening the Settings page where they can flip it on.
 *
 * Stateless — never starts an Activity itself; UI surfaces (e.g. `MainActivity`) launch the
 * returned [Intent] when appropriate.
 */
object DabzAccessHelper {

    /** True when our [DabzMediaListener] is in the system's enabled-listeners list. */
    fun isNlsEnabled(context: Context): Boolean {
        val pkg = context.packageName
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
    }

    /**
     * Intent to deep-link the user into Settings → Apps → Notification access. Caller must add
     * `Intent.FLAG_ACTIVITY_NEW_TASK` if launching from a non-Activity context.
     */
    fun notificationListenerSettingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

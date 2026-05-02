package com.example.radiolyric.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

internal object PlaybackNotification {
    const val CHANNEL_ID = "playback"
    const val CHANNEL_NAME = "DAB playback"
    const val FOREGROUND_NOTIFICATION_ID = 0xDA8

    /**
     * Creates the playback notification channel on API 26+. No-op on older API levels.
     *
     * [androidx.media3.session.MediaSessionService] generates the foreground notification itself
     * via [androidx.media3.session.DefaultMediaNotificationProvider]; we just have to ensure the
     * channel exists.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannelImpl(context)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannelImpl(context: Context) {
        val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW,
                )
                        .apply {
                            description = "Now-playing controls for the DAB radio."
                            setShowBadge(false)
                        }
        manager.createNotificationChannel(channel)
    }
}

package com.example.radiolyric.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

internal object PlaybackNotification {
    const val CHANNEL_ID = "playback"
    const val CHANNEL_NAME = "DAB playback"
    const val FOREGROUND_NOTIFICATION_ID = 0xDA8

    /**
     * Builds the placeholder "preparing" notification used to satisfy Android's foreground-service
     * contract (`startForeground` must be called within ~10s of `startForegroundService`). The
     * Media3 framework will replace this with the proper now-playing notification once the
     * `MediaSession` becomes active, so this is only ever shown transiently while the radio source
     * is opening + tuning.
     */
    fun buildPreparing(context: Context): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Radio Lyric")
                .setContentText("Preparing DAB tuner\u2026")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .build()
    }

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
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

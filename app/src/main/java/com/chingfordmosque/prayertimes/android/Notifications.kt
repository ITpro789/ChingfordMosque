package com.chingfordmosque.prayertimes.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Notification plumbing shared by the [PrayerTimesApp] and [AdhanReceiver].
 */
object Notifications {

    /** Channel id for adhan / prayer-time alerts. */
    const val ADHAN_CHANNEL_ID = "adhan"
    
    /** Channel id for the live azaan media playback foreground service. */
    const val LIVE_AZAAN_CHANNEL_ID = "live_azaan_playback"

    /**
     * Create the high-importance adhan [NotificationChannel] on API 26+. Safe to call
     * repeatedly — creating a channel that already exists is a no-op.
     */
    fun createAdhanChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ADHAN_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            ADHAN_CHANNEL_ID,
            "Adhan & Prayer Times",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts at the start of each prayer time"
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Create the low-importance playback [NotificationChannel] on API 26+ for the Foreground Service.
     */
    fun createPlaybackChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(LIVE_AZAAN_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            LIVE_AZAAN_CHANNEL_ID,
            "Live Azaan Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing notification while Live Azaan is playing"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}

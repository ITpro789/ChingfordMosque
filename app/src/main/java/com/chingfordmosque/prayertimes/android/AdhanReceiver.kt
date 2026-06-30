package com.chingfordmosque.prayertimes.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chingfordmosque.prayertimes.android.platform.AlarmManagerAdhanPort

/**
 * Fires when a scheduled adhan alarm goes off (armed by [AlarmManagerAdhanPort]).
 *
 * It posts a high-priority notification on the [Notifications.ADHAN_CHANNEL_ID] channel that
 * names the prayer, and — when the alert requested it — plays the adhan audio.
 *
 * The adhan sound is resolved by NAME at runtime so the project needs no bundled binary:
 * drop a real adhan recording into `app/src/main/res/raw/adhan.mp3` and it will be used
 * automatically; otherwise we fall back to the system default notification tone.
 */
class AdhanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AlarmManagerAdhanPort.EXTRA_PRAYER_NAME) ?: "Prayer"
        val playSound = intent.getBooleanExtra(AlarmManagerAdhanPort.EXTRA_PLAY_SOUND, false)

        Notifications.createAdhanChannel(context)

        val notification = NotificationCompat.Builder(context, Notifications.ADHAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$prayerName \u2014 Adhan")
            .setContentText("It's time for $prayerName at Chingford Mosque")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val notificationId = prayerName.hashCode()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+); nothing more we can do here.
        }

        if (playSound) {
            playAdhan(context)
        }
    }

    private fun playAdhan(context: Context) {
        // Prefer a bundled raw adhan resource resolved by name; fall back to the default tone.
        val rawId = context.resources.getIdentifier("adhan", "raw", context.packageName)
        val soundUri: Uri = if (rawId != 0) {
            Uri.parse("android.resource://${context.packageName}/$rawId")
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(context, soundUri)
            player.setOnCompletionListener { mp -> mp.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            // Audio is best-effort; the visible notification is the primary alert.
        }
    }
}

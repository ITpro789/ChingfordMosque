package com.chingfordmosque.prayertimes.android.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.chingfordmosque.prayertimes.android.AdhanReceiver
import com.chingfordmosque.prayertimes.notify.AdhanAlarmPort
import com.chingfordmosque.prayertimes.notify.AlertId
import com.chingfordmosque.prayertimes.notify.ScheduledAdhanAlert
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Android binding of the core [AdhanAlarmPort] seam, backed by [AlarmManager].
 *
 * Each [ScheduledAdhanAlert] is armed as an exact, allow-while-idle alarm whose [PendingIntent]
 * targets [AdhanReceiver], carrying the alert id, prayer name, and whether to play the adhan
 * sound. The request code is derived deterministically from [AlertId.value]
 * (`id.value.hashCode()`), and a unique data [Uri] per id keeps the intents filter-distinct, so
 * re-arming the same `(prayer, date)` replaces the previous alarm rather than stacking a
 * duplicate — honouring the port's idempotency contract.
 *
 * The [ScheduledAdhanAlert.firesAt] local instant is interpreted in the mosque's timezone
 * (`Europe/London`) to compute the epoch-millis trigger. Active alert ids are tracked in
 * [SharedPreferences] so that [cancel]/[cancelAll] can rebuild and cancel the matching
 * [PendingIntent], even across process restarts.
 */
class AlarmManagerAdhanPort(
    context: Context,
) : AdhanAlarmPort {

    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun schedule(alert: ScheduledAdhanAlert) {
        val triggerAtMillis = toEpochMillis(alert)
        val pendingIntent = buildPendingIntent(alert.id, alert, create = true)
            ?: return

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            // Exact alarms not permitted by the user: degrade gracefully to a plain alarm
            // rather than risk a SecurityException.
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }

        rememberActive(alert.id)
    }

    override fun cancel(id: AlertId) {
        val pendingIntent = buildPendingIntent(id, alert = null, create = false)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        forgetActive(id)
    }

    override fun cancelAll() {
        val active = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toList()
        for (value in active) {
            cancel(AlertId(value))
        }
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    // --- helpers -----------------------------------------------------------------------

    private fun buildPendingIntent(
        id: AlertId,
        alert: ScheduledAdhanAlert?,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(appContext, AdhanReceiver::class.java).apply {
            action = ACTION_ADHAN_ALERT
            // A unique data URI per id guarantees the PendingIntent is filter-distinct, so
            // alarms for different prayers/days never collide.
            data = Uri.parse("chingfordmosque://adhan/${id.value}")
            putExtra(EXTRA_ALERT_ID, id.value)
            if (alert != null) {
                putExtra(EXTRA_PRAYER_NAME, alert.label)
                putExtra(EXTRA_PLAY_SOUND, alert.playAdhanSound)
                putExtra(EXTRA_DURATION_SECONDS, alert.durationSeconds)
            }
        }

        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }

        return PendingIntent.getBroadcast(appContext, requestCode(id), intent, flags)
    }

    private fun requestCode(id: AlertId): Int = id.value.hashCode()

    private fun rememberActive(id: AlertId) {
        val current = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toMutableSet()
        current.add(id.value)
        prefs.edit().putStringSet(KEY_ACTIVE, current).apply()
    }

    private fun forgetActive(id: AlertId) {
        val current = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toMutableSet()
        if (current.remove(id.value)) {
            prefs.edit().putStringSet(KEY_ACTIVE, current).apply()
        }
    }

    private fun toEpochMillis(alert: ScheduledAdhanAlert): Long {
        val f = alert.firesAt
        return ZonedDateTime.of(
            f.date.year,
            f.date.month,
            f.date.day,
            f.hour,
            f.minute,
            f.second,
            0,
            MOSQUE_ZONE,
        ).toInstant().toEpochMilli()
    }

    companion object {
        private const val PREFS_NAME = "adhan_alarms"
        private const val KEY_ACTIVE = "active_alert_ids"
        private val MOSQUE_ZONE: ZoneId = ZoneId.of("Europe/London")

        const val ACTION_ADHAN_ALERT = "com.chingfordmosque.prayertimes.action.ADHAN_ALERT"
        const val EXTRA_ALERT_ID = "com.chingfordmosque.prayertimes.extra.ALERT_ID"
        const val EXTRA_PRAYER_NAME = "com.chingfordmosque.prayertimes.extra.PRAYER_NAME"
        const val EXTRA_PLAY_SOUND = "com.chingfordmosque.prayertimes.extra.PLAY_SOUND"
        const val EXTRA_DURATION_SECONDS = "com.chingfordmosque.prayertimes.extra.DURATION_SECONDS"
    }
}

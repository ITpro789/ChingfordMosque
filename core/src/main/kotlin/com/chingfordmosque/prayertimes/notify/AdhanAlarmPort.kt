package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.Prayer

/**
 * Deterministic identity for a scheduled adhan alert, derived purely from the prayer and the
 * calendar date it fires on (design Property 5; Requirement 5.5).
 *
 * Because the id is a pure function of `(prayer, date)`, re-arming the same prayer on the same
 * day produces the same id — so the platform binding can *replace* an existing pending alert
 * rather than stacking a duplicate. This is what guarantees "at most one pending alert per
 * (prayer, date)" even if [NotificationScheduler.reschedule] runs many times.
 */
data class AlertId(val value: String) {
    companion object {
        /** The canonical id for the adhan of [prayer] on [date], e.g. "Fajr@2026-06-30". */
        fun of(prayer: Prayer, date: Date): AlertId = AlertId("${prayer.name}@$date")

        /** A custom id for custom adhan alerts, e.g. "Jummah 1@2026-06-30". */
        fun ofCustom(label: String, date: Date): AlertId = AlertId("$label@$date")
    }
}

/**
 * The payload describing one armed adhan alert. It carries the [prayer] so that, when the
 * scheduled [firesAt] instant is reached, the platform can present an azaan/adhan notification
 * that *identifies* the prayer (Requirement 5.2), and [playAdhanSound] so the binding knows
 * whether to play the adhan audio or show a silent/standard notification (Requirement 5.3).
 *
 * This is a plain domain value: it contains no Android types. The real Android binding turns it
 * into an `AlarmManager`/`NotificationManager` request; the JVM build uses an in-memory binding.
 */
data class ScheduledAdhanAlert(
    val id: AlertId,
    val prayer: Prayer,
    val date: Date,
    val firesAt: DateTime,
    val playAdhanSound: Boolean,
    val playDua: Boolean = false,
    val label: String = prayer.name,
)

/**
 * The single platform seam the [NotificationScheduler] arms alerts through (design,
 * Component 4). It is deliberately free of any Android API so the scheduling *logic* compiles
 * and is testable on the JVM; the real Android binding (AlarmManager / NotificationManager /
 * WorkManager) implements this interface later, and tests / the JVM build use
 * [InMemoryAdhanAlarmPort].
 *
 * Contract: [schedule] is idempotent per [ScheduledAdhanAlert.id] — scheduling an alert whose
 * id matches an already-pending alert MUST replace it, never add a second. This mirrors how an
 * Android `PendingIntent` with a stable request code replaces its predecessor, and is the
 * mechanism behind the no-duplicate-alerts guarantee (Requirement 5.5).
 */
interface AdhanAlarmPort {

    /**
     * Arm [alert] to fire at [ScheduledAdhanAlert.firesAt]. If a pending alert with the same
     * [ScheduledAdhanAlert.id] already exists it is replaced (not duplicated).
     */
    fun schedule(alert: ScheduledAdhanAlert)

    /** Cancel the single pending alert identified by [id], if any. */
    fun cancel(id: AlertId)

    /** Cancel every pending alert. */
    fun cancelAll()
}

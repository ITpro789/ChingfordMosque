package com.chingfordmosque.prayertimes.notify

/**
 * In-memory [AdhanAlarmPort] binding used by the JVM build and by tests in place of the
 * (deferred) Android AlarmManager/NotificationManager binding.
 *
 * Pending alerts are keyed by [AlertId] in an insertion-ordered map, which makes the
 * replace-on-same-id contract fall out naturally: scheduling an alert whose id already exists
 * overwrites the entry instead of adding a second one. This is exactly the behaviour the
 * no-duplicate-alerts guarantee (Requirement 5.5; design Property 5) relies on.
 *
 * [pending] exposes the currently armed alerts so the scheduler can be verified end-to-end
 * (e.g. one alert per remaining alerting prayer, never Sunrise, no duplicates after re-arming).
 */
class InMemoryAdhanAlarmPort : AdhanAlarmPort {

    private val alerts = LinkedHashMap<AlertId, ScheduledAdhanAlert>()

    override fun schedule(alert: ScheduledAdhanAlert) {
        // Keyed by id, so an equal id replaces rather than duplicates.
        alerts[alert.id] = alert
    }

    override fun cancel(id: AlertId) {
        alerts.remove(id)
    }

    override fun cancelAll() {
        alerts.clear()
    }

    /** A snapshot of the currently armed alerts, in the order they were first scheduled. */
    fun pending(): List<ScheduledAdhanAlert> = alerts.values.toList()
}

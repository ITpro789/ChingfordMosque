package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.NotificationPreferences

/**
 * A [NotificationScheduler] decorator that gates alert arming on notification permission
 * (Requirement 5.7; design Error Scenario 4).
 *
 * The rest of the app (today's times, the next-prayer countdown) depends only on the schedule
 * and is unaffected by permission state — this wrapper sits purely on the *notification* path.
 * It is composed around an existing [NotificationScheduler] (e.g. [AdhanNotificationScheduler])
 * rather than modifying it, so the underlying scheduling logic is reused untouched:
 *
 * - **Permission granted**: every call is delegated straight through, so behaviour is identical
 *   to the wrapped scheduler.
 * - **Permission not granted**: [reschedule] arms nothing (it cancels any alerts left over from
 *   a previously granted state so none can fire) and prompts the user to enable notifications
 *   exactly once until the situation changes — never repeatedly.
 *
 * The "once" tracking resets whenever permission is observed as granted, so if the user later
 * revokes permission a fresh single prompt is shown for that new episode.
 */
class PermissionGatedNotificationScheduler(
    private val delegate: NotificationScheduler,
    private val permission: NotificationPermission,
    private val prompt: NotificationPermissionPrompt,
) : NotificationScheduler {

    /**
     * Whether the enable-notifications prompt has already been shown for the current ungranted
     * episode. Reset to false once permission is seen granted so a later revoke can prompt again.
     */
    private var promptShownForCurrentDenial: Boolean = false

    override fun reschedule(schedule: DaySchedule, now: DateTime) {
        if (permission.isGranted()) {
            // Granted: behave exactly like the wrapped scheduler, and allow a future revoke to
            // prompt again by clearing the one-shot guard.
            promptShownForCurrentDenial = false
            delegate.reschedule(schedule, now)
            return
        }

        // Not granted: skip arming entirely. Cancel any alerts that may have been armed while
        // permission was previously granted so nothing can fire without permission, leaving the
        // rest of the app (times/countdown) unaffected.
        delegate.cancelAll()

        // Prompt the user to enable notifications once per ungranted episode (Requirement 5.7).
        if (!promptShownForCurrentDenial) {
            prompt.promptToEnableNotifications()
            promptShownForCurrentDenial = true
        }
    }

    override fun cancelAll() {
        // Cancelling is always safe regardless of permission state.
        delegate.cancelAll()
    }

    override fun setPreferences(prefs: NotificationPreferences) {
        // Preferences are independent of permission; just pass them through.
        delegate.setPreferences(prefs)
    }
}

package com.chingfordmosque.prayertimes.notify

/**
 * Pure-Kotlin abstraction over the OS notification-permission state (design, Error Scenario 4;
 * Requirement 5.7).
 *
 * The scheduling layer must never read an Android permission API directly: it only asks this
 * seam whether notifications are currently allowed. On device the real binding answers from the
 * runtime permission grant; the JVM build and tests use [InMemoryNotificationPermission].
 */
interface NotificationPermission {

    /** Whether the app currently has permission to present notifications. */
    fun isGranted(): Boolean
}

/**
 * In-memory [NotificationPermission] for the JVM build and tests. The granted state is mutable
 * via [setGranted] so a test can model the user granting/revoking permission between
 * reschedules without any Android dependency.
 */
class InMemoryNotificationPermission(
    private var granted: Boolean = false,
) : NotificationPermission {

    override fun isGranted(): Boolean = granted

    /** Model the permission being granted (true) or revoked (false). */
    fun setGranted(value: Boolean) {
        granted = value
    }
}

/**
 * Seam used to ask the user to enable notifications when permission has not been granted
 * (Requirement 5.7; design Error Scenario 4). It is intentionally side-effect-only and
 * platform-free: the real Android binding surfaces a prompt / deep-links to system settings,
 * while tests use [RecordingNotificationPermissionPrompt].
 *
 * The "prompt only once" policy lives in [PermissionGatedNotificationScheduler], not here, so
 * this seam stays a dumb sink that simply shows the prompt when told to.
 */
interface NotificationPermissionPrompt {

    /** Ask the user to enable notifications (e.g. show a rationale / open settings). */
    fun promptToEnableNotifications()
}

/**
 * A [NotificationPermissionPrompt] that records how many times it was asked to prompt, so the
 * "prompt the user once" behaviour (Requirement 5.7) can be verified in tests.
 */
class RecordingNotificationPermissionPrompt : NotificationPermissionPrompt {

    var promptCount: Int = 0
        private set

    override fun promptToEnableNotifications() {
        promptCount++
    }
}

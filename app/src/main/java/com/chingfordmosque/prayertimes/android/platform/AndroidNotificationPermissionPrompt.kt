package com.chingfordmosque.prayertimes.android.platform

import com.chingfordmosque.prayertimes.notify.NotificationPermissionPrompt
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android binding of the core [NotificationPermissionPrompt] seam.
 *
 * The core scheduling layer calls [promptToEnableNotifications] when notifications are not yet
 * granted. The actual runtime permission request must be launched from an
 * [android.app.Activity] (see [com.chingfordmosque.prayertimes.android.ui.MainActivity]), so
 * this binding simply records that a prompt is pending and exposes a flag the Activity observes
 * and clears once it has shown the system permission dialog. This keeps the seam side-effect
 * only and free of any Activity/UI coupling.
 */
class AndroidNotificationPermissionPrompt : NotificationPermissionPrompt {

    private val pending = AtomicBoolean(false)

    /** True when the core requested a prompt that the UI has not yet acted on. */
    val isPromptPending: Boolean get() = pending.get()

    override fun promptToEnableNotifications() {
        pending.set(true)
    }

    /** Called by the Activity after it has shown the system permission request; clears the flag. */
    fun consumePrompt(): Boolean = pending.getAndSet(false)
}

package com.chingfordmosque.prayertimes.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Re-arms adhan alarms after a device reboot (alarms do not survive a restart).
 *
 * On `BOOT_COMPLETED` we drive the application container's launch entry point on a background
 * thread, which refreshes from cache and reschedules notifications. Kept deliberately minimal
 * and defensive — any failure here must never crash the boot broadcast.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as? PrayerTimesApp ?: return
        // Use a goAsync allowance window via a short-lived thread; onAppOpened() renders from
        // cache and reschedules alarms without blocking the main thread.
        thread(start = true, name = "adhan-boot-reschedule") {
            runCatching { app.container.onAppOpened() }
        }
    }
}

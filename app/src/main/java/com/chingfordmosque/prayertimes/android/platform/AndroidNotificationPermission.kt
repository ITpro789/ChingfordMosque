package com.chingfordmosque.prayertimes.android.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.chingfordmosque.prayertimes.notify.NotificationPermission

/**
 * Android binding of the core [NotificationPermission] seam.
 *
 * On API 33+ (Tiramisu) the runtime `POST_NOTIFICATIONS` permission is required, so we report
 * the live grant state via [ContextCompat.checkSelfPermission]. Below 33 notifications do not
 * require a runtime grant, so permission is always considered granted.
 */
class AndroidNotificationPermission(
    context: Context,
) : NotificationPermission {

    private val appContext = context.applicationContext

    override fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

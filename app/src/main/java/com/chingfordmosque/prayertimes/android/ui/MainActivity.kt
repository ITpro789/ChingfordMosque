package com.chingfordmosque.prayertimes.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.chingfordmosque.prayertimes.android.ui.theme.ChingfordMosqueTheme

/**
 * The single activity hosting the Compose UI.
 *
 * On API 33+ it requests the runtime `POST_NOTIFICATIONS` permission at launch so adhan alerts
 * can be presented. On resume it drives [PrayerViewModel.onAppResumed] so the daily-rollover
 * tick runs and the countdown re-syncs to the current instant.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        maybeRequestNotificationPermission()

        setContent {
            ChingfordMosqueTheme {
                PrayerTimesRoute(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

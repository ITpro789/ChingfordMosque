package com.chingfordmosque.prayertimes.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** A bottom-navigation destination. */
private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.AccessTime),
    Qibla("Qibla", Icons.Outlined.Explore),
    Settings("Settings", Icons.Outlined.Settings),
    About("About", Icons.Outlined.Info),
}

/**
 * The top-level app shell: a Material 3 [NavigationBar] with four destinations, switching the
 * rendered screen via simple saved state (no navigation-compose dependency).
 */
@Composable
fun MainScaffold(prayerViewModel: PrayerViewModel) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val destinations = Destination.entries

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (destinations[selectedIndex]) {
                Destination.Home -> PrayerTimesRoute(viewModel = prayerViewModel)
                Destination.Qibla -> QiblaScreen()
                Destination.Settings -> SettingsRoute()
                Destination.About -> AboutScreen()
            }
        }
    }
}

package com.chingfordmosque.prayertimes.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chingfordmosque.prayertimes.android.settings.AppSettings
import com.chingfordmosque.prayertimes.android.settings.SettingsRepository
import com.chingfordmosque.prayertimes.android.settings.ThemeMode
import com.chingfordmosque.prayertimes.android.ui.theme.ChingfordMosqueTheme
import com.chingfordmosque.prayertimes.domain.Prayer

/** Route wiring the Settings screen to its [SettingsViewModel]. */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    SettingsScreen(
        settings = settings,
        onPrayerToggle = viewModel::setPrayerEnabled,
        onAdhanSoundToggle = viewModel::setAdhanSoundEnabled,
        onThemeSelected = viewModel::setThemeMode,
        onLocalAdhanToggle = viewModel::setLocalAdhan,
        onIqamahOffsetChange = viewModel::setIqamahOffset,
        onPlayDuaToggle = viewModel::setPlayDua,
        onAzaanVolumeChange = viewModel::setAzaanVolume,
        onShortAzaanToggle = viewModel::setShortAzaan,
    )
}

/**
 * Settings screen: per-prayer notification switches, an adhan-sound switch, and a theme
 * selector, laid out as clean grouped Material 3 cards.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onPrayerToggle: (Prayer, Boolean) -> Unit,
    onAdhanSoundToggle: (Boolean) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onLocalAdhanToggle: (Boolean) -> Unit = {},
    onIqamahOffsetChange: (Int) -> Unit = {},
    onPlayDuaToggle: (Boolean) -> Unit = {},
    onAzaanVolumeChange: (Int) -> Unit = {},
    onShortAzaanToggle: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        SectionCard(title = "Azaan") {
            RadioRow(
                label = "Salah Beginning Time",
                selected = settings.isLocalAdhan,
                onSelect = { onLocalAdhanToggle(true) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            RadioRow(
                label = "Salah Jammat Time",
                selected = !settings.isLocalAdhan,
                onSelect = { onLocalAdhanToggle(false) }
            )
            
            if (!settings.isLocalAdhan) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                NumberRow(
                    label = "Minutes before Jammat (Iqama)",
                    supporting = "Choose 10 to 30 minutes before Jammat",
                    value = settings.iqamahOffset,
                    onValueChange = onIqamahOffsetChange,
                    valueRange = 10f..30f
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            SwitchRow(
                label = "Short Azaan",
                supporting = "Plays only 'Allahuakbar Allahuakbar'",
                checked = settings.shortAzaan,
                onCheckedChange = onShortAzaanToggle,
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            SwitchRow(
                label = "Dua after azaan",
                supporting = "Plays an authentic dua MP3 right after the azaan",
                checked = settings.playDua,
                onCheckedChange = onPlayDuaToggle,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            NumberRow(
                label = "Azaan Volume (%)",
                supporting = "Override the phone's physical volume for the Azaan",
                value = settings.azaanVolume,
                onValueChange = onAzaanVolumeChange,
                valueRange = 0f..100f,
                steps = 9 // 0, 10, 20... 100
            )
        }

        SectionCard(title = "Prayer notifications") {
            SettingsRepository.ALERTING_PRAYERS.forEachIndexed { index, prayer ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                SwitchRow(
                    label = prayer.name,
                    checked = prayer in settings.enabledPrayers,
                    onCheckedChange = { onPrayerToggle(prayer, it) },
                )
            }
        }

        SectionCard(title = "Adhan sound") {
            SwitchRow(
                label = "Play adhan sound",
                supporting = "Plays the adhan audio with each alert",
                checked = settings.adhanSoundEnabled,
                onCheckedChange = onAdhanSoundToggle,
            )
        }

        SectionCard(title = "Appearance") {
            ThemeMode.values().forEachIndexed { index, mode ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                RadioRow(
                    label = mode.displayName(),
                    selected = settings.themeMode == mode,
                    onSelect = { onThemeSelected(mode) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NumberRow(
    label: String,
    supporting: String?,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..300f,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ChingfordMosqueTheme {
        SettingsScreen(
            settings = AppSettings.DEFAULT,
            onPrayerToggle = { _, _ -> },
            onAdhanSoundToggle = {},
            onThemeSelected = {},
        )
    }
}

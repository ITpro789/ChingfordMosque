package com.chingfordmosque.prayertimes.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.chingfordmosque.prayertimes.android.ui.theme.ChingfordMosqueTheme
import com.chingfordmosque.prayertimes.android.ui.theme.CountdownTextStyle
import com.chingfordmosque.prayertimes.ui.DayScheduleViewState
import com.chingfordmosque.prayertimes.ui.ErrorBannerViewState
import com.chingfordmosque.prayertimes.ui.JummahSectionViewState
import com.chingfordmosque.prayertimes.ui.NextPrayerViewState
import com.chingfordmosque.prayertimes.ui.PrayerRowViewState

/**
 * Composable entry point wired to [PrayerViewModel]. Collects the UI state and renders
 * [PrayerTimesScreen], forwarding the manual-refresh action.
 */
@Composable
fun PrayerTimesRoute(
    viewModel: PrayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    PrayerTimesScreen(state = state, onRefresh = viewModel::refresh)
}

/**
 * The single, sleek prayer-times screen: a gradient hero with a live countdown, a freshness /
 * error strip, the day's prayer cards (highlighting the next prayer), and the Jummah card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    state: PrayerUiState,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chingford Mosque", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(22.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh times")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroHeader(today = state.today, next = state.next)

            FreshnessStrip(next = state.next)

            state.next.errorBanner?.let { banner ->
                ErrorBanner(banner = banner, onRetry = onRefresh)
            }

            val today = state.today
            if (today == null) {
                EmptyState(isRefreshing = state.isRefreshing)
            } else {
                Text(
                    text = "Today's prayers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                today.rows.forEach { row ->
                    PrayerCard(
                        row = row,
                        isNext = row.prayerName == state.next.nextPrayerName,
                    )
                }
            }

            JummahCard(jummah = state.jummah)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeroHeader(
    today: DayScheduleViewState?,
    next: NextPrayerViewState,
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
        ),
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Chingford Mosque",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                today?.date?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val name = next.nextPrayerName
                val countdown = next.countdown
                if (name != null && countdown != null) {
                    Text(
                        text = "$name in",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                    Text(
                        text = countdown,
                        style = CountdownTextStyle,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "No upcoming prayer",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FreshnessStrip(next: NextPrayerViewState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = next.lastUpdatedText ?: "Not yet updated",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (next.isStale) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Stale") },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    banner: ErrorBannerViewState,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = banner.message, style = MaterialTheme.typography.bodyMedium)
            if (banner.showRetry) {
                FilledTonalButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun PrayerCard(
    row: PrayerRowViewState,
    isNext: Boolean,
) {
    val containerColor = when {
        isNext -> MaterialTheme.colorScheme.primaryContainer
        row.isInformational -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isNext -> MaterialTheme.colorScheme.onPrimaryContainer
        row.isInformational -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isNext) 4.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isInformational) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = row.prayerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (isNext) {
                        Text(
                            text = "Next prayer",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else if (row.isInformational) {
                        Text(
                            text = "Informational",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeColumn(label = "Begins", value = row.begins)
                Spacer(modifier = Modifier.width(24.dp))
                TimeColumn(label = "Iqamah", value = row.iqamah ?: "\u2014")
            }
        }
    }
}

@Composable
private fun TimeColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun JummahCard(jummah: JummahSectionViewState) {
    if (jummah !is JummahSectionViewState.Visible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Jummah (Friday)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                jummah.times.forEach { time ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(time) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isRefreshing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator()
                Text("Loading prayer times\u2026", textAlign = TextAlign.Center)
            } else {
                Text(
                    text = "No prayer times yet. Pull refresh to load the latest schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// --- Preview -------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun PrayerTimesScreenPreview() {
    val sample = PrayerUiState(
        today = DayScheduleViewState(
            date = "2026-06-30",
            rows = listOf(
                PrayerRowViewState("Fajr", "03:03", "03:30", isInformational = false),
                PrayerRowViewState("Sunrise", "04:42", null, isInformational = true),
                PrayerRowViewState("Zuhr", "13:05", "13:30", isInformational = false),
                PrayerRowViewState("Asr", "18:30", "19:00", isInformational = false),
                PrayerRowViewState("Maghrib", "21:21", "21:26", isInformational = false),
                PrayerRowViewState("Isha", "22:45", "23:00", isInformational = false),
            ),
        ),
        jummah = JummahSectionViewState.Visible(listOf("13:00", "13:30")),
        next = NextPrayerViewState(
            nextPrayerName = "Maghrib",
            countdown = "01:23:45",
            lastUpdatedText = "Last updated 2026-06-30 07:12",
            isStale = false,
            errorBanner = null,
            showManualRefresh = true,
        ),
        isRefreshing = false,
    )
    ChingfordMosqueTheme {
        PrayerTimesScreen(state = sample, onRefresh = {})
    }
}

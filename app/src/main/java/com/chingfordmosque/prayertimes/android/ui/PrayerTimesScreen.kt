package com.chingfordmosque.prayertimes.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * The single, sleek prayer-times screen: a hero built around a circular countdown timer, a
 * freshness / error strip, the day's prayer cards (highlighting the active prayer), and the
 * Jummah card — over a subtle vertical gradient background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    state: PrayerUiState,
    onRefresh: () -> Unit,
) {
    val appBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(appBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CountdownHero(today = state.today, next = state.next)

            FreshnessStrip(next = state.next)

            if (state.next.isEstimated) {
                EstimatedNotice()
            }

            state.next.errorBanner?.let { banner ->
                ErrorBanner(banner = banner, onRetry = onRefresh)
            }

            val today = state.today
            if (today == null) {
                EmptyState(isRefreshing = state.isRefreshing)
            } else {
                JummahCard(jummah = state.jummah)

                Text(
                    text = "Today's prayers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val activeName = if (state.next.ringIsActive) state.next.ringPrayerName else null
                today.rows.forEach { row ->
                    PrayerCard(
                        row = row,
                        isActive = row.prayerName == activeName,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * The hero card: a large circular countdown ring with the current/upcoming prayer at its
 * centre, over a soft brand gradient.
 */
@Composable
private fun CountdownHero(
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
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = today?.date ?: "Chingford Mosque",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                )

                val hijri = remember { HijriDate.today() }
                if (hijri != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hijri,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                CircularCountdownRing(next = next)
            }
        }
    }
}

/**
 * The circular progress ring (Canvas drawArc) with the prayer name, big countdown and caption
 * at its centre. Falls back to a graceful placeholder when there is no ring data.
 */
@Composable
private fun CircularCountdownRing(next: NextPrayerViewState) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val accent = MaterialTheme.colorScheme.tertiary
    val trackColor = onPrimary.copy(alpha = 0.18f)
    val sweepBrush = Brush.sweepGradient(
        colors = listOf(accent, onPrimary, accent),
    )

    val targetProgress = next.ringProgress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 600),
        label = "ringProgress",
    )

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Soft track behind the sweep.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            // Gradient progress sweep, starting from the top (12 o'clock).
            if (next.ringPrayerName != null) {
                drawArc(
                    brush = sweepBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        RingCenter(next = next)
    }
}

@Composable
private fun RingCenter(next: NextPrayerViewState) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val name = next.ringPrayerName
    val countdown = next.ringCountdown

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (name != null && countdown != null) {
            if (next.ringIsActive) {
                NowBadge()
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = onPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = countdown,
                style = CountdownTextStyle,
                color = onPrimary,
            )
            next.ringCaption?.let { caption ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = onPrimary.copy(alpha = 0.85f),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No prayer times yet",
                style = MaterialTheme.typography.titleMedium,
                color = onPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NowBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onTertiary),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Now",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiary,
            fontWeight = FontWeight.Bold,
        )
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

/**
 * A tasteful amber notice shown when the displayed schedule is an on-device astronomical
 * estimate (the calculated fallback) because the mosque site couldn't be reached or parsed.
 * Sits just under the freshness strip; iqamah times read "—" in this mode since calculation
 * cannot know the mosque's congregational times.
 */
@Composable
private fun EstimatedNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Estimated times \u2014 couldn't reach the mosque site",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
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
        shape = RoundedCornerShape(20.dp),
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
    isActive: Boolean,
) {
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        row.isInformational -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        row.isInformational -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 1.dp,
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
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else if (row.isInformational) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = row.prayerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (isActive) {
                        Text(
                            text = "In progress",
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

    val activeColor = Color(0xFF6366F1)
    val activeContainerColor = Color(0x226366F1)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Jummah Congregations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    jummah.times.forEachIndexed { index, time ->
                        val isHighlighted = index == jummah.activeIndex
                        val label = when (index) {
                            0 -> "1st"
                            1 -> "2nd"
                            2 -> "3rd"
                            else -> "${index + 1}th"
                        }
                        Card(
                            modifier = Modifier.weight(1f).height(95.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHighlighted) {
                                    activeContainerColor
                                } else {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                                }
                            ),
                            border = if (isHighlighted) {
                                BorderStroke(2.dp, activeColor)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$label Jamā'ah",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isHighlighted) activeColor else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = time,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHighlighted) activeColor else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isHighlighted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = activeColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = activeColor
                                        )
                                    } else {
                                        val activeIdx = jummah.activeIndex
                                        val statusText = if (activeIdx != null && index < activeIdx) "Done" else "Upcoming"
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Friday Sunnahs & Preparation Guide
        var isGuideExpanded by remember { mutableStateOf(false) }
        val sunnahs = remember {
            listOf(
                "🚿 Ghusl (Ritual Bath) before Jummah prayer" to "It is highly recommended to perform Ghusl on Friday to cleanse oneself.",
                "🧴 Clean Clothes & Perfume" to "Wear clean or your best clothes, and apply attar/perfume.",
                "📖 Recite Surah Al-Kahf" to "Reciting Surah Al-Kahf on Friday illuminates light for the reader until the next Friday.",
                "📿 Increase Salawat upon the Prophet ﷺ" to "Send abundant blessings upon the Prophet Muhammad ﷺ.",
                "🕌 Arrive Early to the Masjid" to "Walk calmly to the mosque early to listen attentively to the Khutbah.",
                "🤲 Seek the hour of answered Dua" to "Make earnest supplication in the last hour before Maghrib."
            )
        }
        val checkboxStates = remember { List(sunnahs.size) { mutableStateOf(false) } }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isGuideExpanded = !isGuideExpanded }
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨ Friday Sunnahs & Prep Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (isGuideExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isGuideExpanded) "Collapse" else "Expand"
                    )
                }

                if (isGuideExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    sunnahs.forEachIndexed { index, (title, description) ->
                        var checked by checkboxStates[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checked = !checked }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { checked = it },
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = description,
                                    style = Modifier.align(Alignment.Start).let { MaterialTheme.typography.bodySmall },
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isRefreshing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    text = "No prayer times yet. Tap refresh to load the latest schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// --- Previews ------------------------------------------------------------------------------

private fun sampleState(
    next: NextPrayerViewState,
): PrayerUiState = PrayerUiState(
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
    next = next,
    isRefreshing = false,
)

@Preview(showBackground = true, name = "Active prayer")
@Composable
private fun PrayerTimesScreenActivePreview() {
    val sample = sampleState(
        next = NextPrayerViewState(
            nextPrayerName = "Isha",
            countdown = "01:23:45",
            lastUpdatedText = "Last updated 2026-06-30 07:12",
            isStale = false,
            errorBanner = null,
            showManualRefresh = true,
            ringPrayerName = "Maghrib",
            ringIsActive = true,
            ringCaption = "Maghrib ends in",
            ringCountdown = "01:23:45",
            ringProgress = 0.42f,
        ),
    )
    ChingfordMosqueTheme {
        PrayerTimesScreen(state = sample, onRefresh = {})
    }
}

@Preview(showBackground = true, name = "Upcoming prayer")
@Composable
private fun PrayerTimesScreenUpcomingPreview() {
    val sample = sampleState(
        next = NextPrayerViewState(
            nextPrayerName = "Zuhr",
            countdown = "04:00:00",
            lastUpdatedText = "Last updated 2026-06-30 07:12",
            isStale = true,
            errorBanner = null,
            showManualRefresh = true,
            ringPrayerName = "Zuhr",
            ringIsActive = false,
            ringCaption = "Zuhr begins in",
            ringCountdown = "04:00:00",
            ringProgress = 0.6f,
        ),
    )
    ChingfordMosqueTheme {
        PrayerTimesScreen(state = sample, onRefresh = {})
    }
}

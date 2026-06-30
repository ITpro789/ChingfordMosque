package com.chingfordmosque.prayertimes.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * App typography. Built on the Material 3 defaults with a few display/headline roles tuned
 * for the hero countdown so the "next prayer" panel reads big, clean, and confident.
 */
val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 57.sp,
            letterSpacing = (-0.5).sp,
        ),
        displayMedium = displayMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineMedium = headlineMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = titleLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        labelLarge = labelLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        ),
    )
}

/** A monospaced-feel style for the live countdown so digits do not jitter as they tick. */
val CountdownTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 48.sp,
    letterSpacing = 1.sp,
)

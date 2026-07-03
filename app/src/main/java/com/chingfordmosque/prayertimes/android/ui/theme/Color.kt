package com.chingfordmosque.prayertimes.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette for the Chingford Mosque app.
 *
 * The identity is a modern "Indigo/Navy" pairing: a deep royal-indigo as the primary colour,
 * cool slate as the secondary, and a warm amber/gold as the tertiary accent — balanced for
 * both a light ("day") and a dark ("night") Material 3 scheme.
 *
 * Colours are derived from an HTML mockup with the following key tokens:
 *   Dark bg: #070B19–#0D111E · Primary: #6366F1 · Accent: #F59E0B
 *   Light bg: #F8FAFC–#E2E8F0 · Primary: #4F46E5 · Accent: #D97706
 */

// --- Light scheme -------------------------------------------------------------------------
val IndigoPrimaryLight         = Color(0xFF4F46E5)   // Indigo-600
val IndigoOnPrimaryLight       = Color(0xFFFFFFFF)
val IndigoPrimaryContainerLight = Color(0xFFE0E0FF)  // Light indigo container
val IndigoOnPrimaryContainerLight = Color(0xFF1A1065) // Deep indigo on-container

val SlateSecondaryLight         = Color(0xFF475569)   // Slate-600
val SlateOnSecondaryLight       = Color(0xFFFFFFFF)
val SlateSecondaryContainerLight = Color(0xFFE2E8F0)  // Slate-200
val SlateOnSecondaryContainerLight = Color(0xFF0F172A) // Slate-900

val AmberTertiaryLight          = Color(0xFFD97706)   // Amber-600
val AmberOnTertiaryLight        = Color(0xFFFFFFFF)
val AmberTertiaryContainerLight = Color(0xFFFDE68A)   // Amber-200
val AmberOnTertiaryContainerLight = Color(0xFF451A03) // Amber-950

val BackgroundLight             = Color(0xFFF8FAFC)   // Slate-50
val OnBackgroundLight           = Color(0xFF0F172A)   // Slate-900
val SurfaceLight                = Color(0xFFF8FAFC)   // Slate-50
val OnSurfaceLight              = Color(0xFF0F172A)   // Slate-900
val SurfaceVariantLight         = Color(0xFFE2E8F0)   // Slate-200
val OnSurfaceVariantLight       = Color(0xFF475569)   // Slate-600
val OutlineLight                = Color(0xFF94A3B8)   // Slate-400

val ErrorLight                  = Color(0xFFBA1A1A)
val OnErrorLight                = Color(0xFFFFFFFF)
val ErrorContainerLight         = Color(0xFFFFDAD6)
val OnErrorContainerLight       = Color(0xFF410002)

// --- Dark scheme --------------------------------------------------------------------------
val IndigoPrimaryDark          = Color(0xFF6366F1)   // Royal Indigo (mockup primary)
val IndigoOnPrimaryDark        = Color(0xFFFFFFFF)
val IndigoPrimaryContainerDark = Color(0xFF312E81)   // Indigo-900
val IndigoOnPrimaryContainerDark = Color(0xFFE0E0FF)

val SlateSecondaryDark         = Color(0xFF9CA3AF)   // Text-muted / cool grey
val SlateOnSecondaryDark       = Color(0xFF0F172A)
val SlateSecondaryContainerDark = Color(0xFF161E31)  // Card bg (approx rgba(22,30,49))
val SlateOnSecondaryContainerDark = Color(0xFFE2E8F0)

val AmberTertiaryDark          = Color(0xFFF59E0B)   // Gold/Amber accent
val AmberOnTertiaryDark        = Color(0xFF451A03)
val AmberTertiaryContainerDark = Color(0xFF78350F)   // Amber-800
val AmberOnTertiaryContainerDark = Color(0xFFFDE68A)

val BackgroundDark             = Color(0xFF070B19)    // Deep navy (mockup bg start)
val OnBackgroundDark           = Color(0xFFF3F4F6)    // Text main
val SurfaceDark                = Color(0xFF0D111E)    // Deep navy (mockup bg end)
val OnSurfaceDark              = Color(0xFFF3F4F6)    // Text main
val SurfaceVariantDark         = Color(0xFF161E31)    // Card background
val OnSurfaceVariantDark       = Color(0xFF9CA3AF)    // Text muted
val OutlineDark                = Color(0xFF334155)    // Card border (Slate-700)

val ErrorDark                  = Color(0xFFFFB4AB)
val OnErrorDark                = Color(0xFF690005)
val ErrorContainerDark         = Color(0xFF93000A)
val OnErrorContainerDark       = Color(0xFFFFDAD6)

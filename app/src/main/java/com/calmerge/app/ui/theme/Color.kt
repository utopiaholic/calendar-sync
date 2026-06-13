package com.calmerge.app.ui.theme

import androidx.compose.ui.graphics.Color

// Base surfaces — deep slate-blue range
val SlateDark = Color(0xFF0D1117)
val SlateDark2 = Color(0xFF161B22)
val SlateDark3 = Color(0xFF1C2128)
val SlateSurface = Color(0xFF21262D)
val SlateSurfaceVariant = Color(0xFF2D333B)

// Light surfaces
val LightBackground = Color(0xFFF7F9FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7ECF2)
val LightOnSurface = Color(0xFF17212B)
val LightOnSurfaceVariant = Color(0xFF596575)
val LightOutline = Color(0x33606C80)
val LightOutlineVariant = Color(0xFFD6DEE8)

// Accent — teal
val TealAccent = Color(0xFF39D0C8)
internal val DefaultAccountColor = 0xFF39D0C8.toInt()
val TealContainer = Color(0xFF003A38)
val TealDark = Color(0xFF00796F)
val TealLightContainer = Color(0xFFD6F6F2)
val ColorOnAccent = Color(0xFFFFFFFF)

// Accent — soft purple
val PurpleAccent = Color(0xFFB388FF)
val PurpleContainer = Color(0xFF2D1B69)
val PurpleDark = Color(0xFF6850B8)
val PurpleLightContainer = Color(0xFFECE5FF)

// Conflict signal. Calendar/account colors intentionally avoid nearby hues.
val ConflictRed = Color(0xFFFF4F5E)
val ConflictRedContainer = Color(0xFF4A1016)
val ConflictRedLightContainer = Color(0xFFFFDAD6)

internal val AccountColorPalette = listOf(
    0xFF1A73E8.toInt(), // blue
    0xFF29B6F6.toInt(), // sky
    0xFF00B8D9.toInt(), // cyan
    0xFF39D0C8.toInt(), // teal
    0xFF12A4AF.toInt(), // deep teal
    0xFF188038.toInt(), // green
    0xFF57D9A3.toInt(), // mint
    0xFF7CB342.toInt(), // leaf
    0xFF5F6368.toInt(), // grey
    0xFF78909C.toInt(), // blue grey
)

internal val ConflictReservedAccountColors = setOf(
    0xFFD93025.toInt(), // old red
    0xFFF9AB00.toInt(), // old amber
    0xFF9334E6.toInt(), // old purple
    0xFFE8710A.toInt(), // old orange
    0xFFB80672.toInt(), // old magenta
)

// Text hierarchy
val OnSlatePrimary = Color(0xFFE6EDF3)
val OnSlateSecondary = Color(0xFF8B949E)

// Glass border — 1dp gradient border on frosted surfaces
val GlassBorder = Color(0x26FFFFFF) // white at ~15% alpha

// On-dark semantic
val ErrorRed = Color(0xFFFF6B6B)
val ErrorRedLight = Color(0xFFBA1A1A)
val WarningAmber = Color(0xFFD29922)
val WarningAmberLight = Color(0xFF8A6500)

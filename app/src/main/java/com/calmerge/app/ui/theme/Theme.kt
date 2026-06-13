package com.calmerge.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = SlateDark,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealAccent,

    secondary = PurpleAccent,
    onSecondary = SlateDark,
    secondaryContainer = PurpleContainer,
    onSecondaryContainer = PurpleAccent,

    tertiary = WarningAmber,
    onTertiary = SlateDark,

    background = SlateDark,
    onBackground = OnSlatePrimary,

    surface = SlateDark2,
    onSurface = OnSlatePrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = OnSlateSecondary,

    error = ErrorRed,
    onError = SlateDark,
    errorContainer = ConflictRedContainer,
    onErrorContainer = ConflictRed,

    outline = GlassBorder,
    outlineVariant = SlateSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = TealDark,
    onPrimary = ColorOnAccent,
    primaryContainer = TealLightContainer,
    onPrimaryContainer = TealDark,

    secondary = PurpleDark,
    onSecondary = ColorOnAccent,
    secondaryContainer = PurpleLightContainer,
    onSecondaryContainer = PurpleDark,

    tertiary = WarningAmberLight,
    onTertiary = ColorOnAccent,

    background = LightBackground,
    onBackground = LightOnSurface,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,

    error = ErrorRedLight,
    onError = ColorOnAccent,
    errorContainer = ConflictRedLightContainer,
    onErrorContainer = ErrorRedLight,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

// CompositionLocal so every composable can gate animations on reduce-motion.
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun CalMergeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion(context)

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = CalMergeTypography,
            shapes = CalMergeShapes,
            content = content,
        )
    }
}

@Composable
private fun rememberReduceMotion(context: Context): Boolean {
    val scale = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    return scale == 0f
}

val isReduceMotion: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalReduceMotion.current

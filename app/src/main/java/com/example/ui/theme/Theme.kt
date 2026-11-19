package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode

private val LightScheme = lightColorScheme(
    primary = PopColors.Light.strong,
    onPrimary = PopColors.Light.onStrong,
    primaryContainer = PopColors.Light.soft,
    onPrimaryContainer = PopInk,
    secondary = PopYellow,
    onSecondary = PopInk,
    secondaryContainer = PopColors.Light.soft,
    onSecondaryContainer = PopInk,
    background = PopColors.Light.bg,
    onBackground = PopColors.Light.text,
    surface = PopColors.Light.bg,
    onSurface = PopColors.Light.text,
    surfaceVariant = PopColors.Light.card,
    onSurfaceVariant = PopColors.Light.muted,
    surfaceContainer = PopColors.Light.card,
    surfaceContainerHigh = PopColors.Light.field,
    outline = PopColors.Light.track,
    error = PopError,
    errorContainer = PopColors.Light.errorSoft,
)

private val DarkScheme = darkColorScheme(
    primary = PopColors.Dark.strong,
    onPrimary = PopColors.Dark.onStrong,
    primaryContainer = PopColors.Dark.soft,
    onPrimaryContainer = PopColors.Dark.text,
    secondary = PopYellow,
    onSecondary = PopInk,
    secondaryContainer = PopColors.Dark.soft,
    onSecondaryContainer = PopColors.Dark.text,
    background = PopColors.Dark.bg,
    onBackground = PopColors.Dark.text,
    surface = PopColors.Dark.bg,
    onSurface = PopColors.Dark.text,
    surfaceVariant = PopColors.Dark.card,
    onSurfaceVariant = PopColors.Dark.muted,
    surfaceContainer = PopColors.Dark.card,
    surfaceContainerHigh = PopColors.Dark.field,
    outline = PopColors.Dark.track,
    error = PopError,
    errorContainer = PopColors.Dark.errorSoft,
)

val GrabShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object GrabThemeTokens {
    val pop: PopColors
        @Composable get() = LocalPopColors.current
}

@Composable
fun GrabTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val pop = if (dark) PopColors.Dark else PopColors.Light
    val colorScheme = if (dark) DarkScheme else LightScheme

    CompositionLocalProvider(LocalPopColors provides pop) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GrabTypography,
            shapes = GrabShapes,
            content = content,
        )
    }
}

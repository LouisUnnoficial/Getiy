package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode

private val LightScheme = lightColorScheme(
    primary = PopColors.Light.strong,
    onPrimary = PopColors.Light.onStrong,
    primaryContainer = PopColors.Light.soft,
    onPrimaryContainer = GetiyNavy,
    secondary = Color(0xFF4768A0),
    onSecondary = Color.White,
    secondaryContainer = PopColors.Light.soft,
    onSecondaryContainer = GetiyNavy,
    tertiary = Color(0xFF5D7BB0),
    onTertiary = Color.White,
    background = PopColors.Light.bg,
    onBackground = PopColors.Light.text,
    surface = PopColors.Light.bg,
    onSurface = PopColors.Light.text,
    surfaceVariant = PopColors.Light.card,
    onSurfaceVariant = PopColors.Light.muted,
    surfaceContainer = PopColors.Light.card,
    surfaceContainerHigh = PopColors.Light.field,
    outline = PopColors.Light.track,
    error = Color(0xFFC43B4B),
    onError = Color.White,
    errorContainer = PopColors.Light.errorSoft,
    onErrorContainer = Color(0xFF661A24),
)

private val DarkScheme = darkColorScheme(
    primary = PopColors.Dark.strong,
    onPrimary = PopColors.Dark.onStrong,
    primaryContainer = PopColors.Dark.soft,
    onPrimaryContainer = GetiyIce,
    secondary = GetiyAzure,
    onSecondary = GetiyNavyDeep,
    secondaryContainer = PopColors.Dark.soft,
    onSecondaryContainer = GetiyIce,
    tertiary = GetiyPeriwinkle,
    onTertiary = GetiyNavyDeep,
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
    onError = GetiyNavyDeep,
    errorContainer = PopColors.Dark.errorSoft,
    onErrorContainer = Color(0xFFF6D4D9),
)

val GetiyShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object GetiyThemeTokens {
    val pop: PopColors
        @Composable get() = LocalPopColors.current
}

@Composable
fun GetiyTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
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
            typography = GetiyTypography,
            shapes = GetiyShapes,
            content = content,
        )
    }
}

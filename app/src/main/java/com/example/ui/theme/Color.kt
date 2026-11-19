package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Brand tokens matching Grab's Pop design system
val PopYellow = Color(0xFFFFD84D)
val PopInk = Color(0xFF141414)
val PopNavIdle = Color(0xFFBDBDBD)
val PopError = Color(0xFFB3261E)
val PopSkyBlue = Color(0xFF9FD8FF)
val PopRosePink = Color(0xFFFFB2C8)

val PopThumbPalette = listOf(
    PopYellow,
    PopSkyBlue,
    PopRosePink,
)

fun popTintFor(seed: String): Color =
    PopThumbPalette[abs(seed.hashCode()) % PopThumbPalette.size]

@Immutable
data class PopColors(
    val bg: Color,
    val text: Color,
    val muted: Color,
    val card: Color,
    val field: Color,
    val track: Color,
    val soft: Color,
    val trackOnSoft: Color,
    val strong: Color,
    val onStrong: Color,
    val bar: Color,
    val disabled: Color,
    val errorSoft: Color,
    val isDark: Boolean,
) {
    companion object {
        val Light = PopColors(
            bg = Color(0xFFFFFFFF),
            text = PopInk,
            muted = Color(0xFF555555),
            card = Color(0xFFF7F7F7),
            field = Color(0xFFF4F4F4),
            track = Color(0xFFEFEFEF),
            soft = Color(0xFFFFF1B8),
            trackOnSoft = Color(0xFFFFFFFF),
            strong = PopInk,
            onStrong = PopYellow,
            bar = PopInk,
            disabled = Color(0xFF3A3A3A),
            errorSoft = Color(0xFFFFE1E1),
            isDark = false,
        )

        val Dark = PopColors(
            bg = Color(0xFF121212),
            text = Color(0xFFF4F4F4),
            muted = Color(0xFFA8A8A8),
            card = Color(0xFF1E1E1E),
            field = Color(0xFF262626),
            track = Color(0xFF333333),
            soft = Color(0xFF3A3215),
            trackOnSoft = Color(0xFF1E1A0B),
            strong = PopYellow,
            onStrong = PopInk,
            bar = Color(0xFF242424),
            disabled = Color(0xFF3A3A3A),
            errorSoft = Color(0xFF4A2020),
            isDark = true,
        )
    }
}

val LocalPopColors = staticCompositionLocalOf { PopColors.Light }

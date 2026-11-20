package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Getiy refined matte brand tokens — anchored to #0E1935 Midnight Navy & calm silver-slate tones (no neon)
val GetiyNavy = Color(0xFF0E1935)
val GetiyNavyDeep = Color(0xFF091124)
val GetiyMint = Color(0xFFDCE6F7)        // Matte cool silver-ice primary surface
val GetiyCyan = Color(0xFFCDDCEF)        // Soft mist-slate
val GetiyAzure = Color(0xFF7B9CD0)       // Calm matte steel-blue accent
val GetiyPeriwinkle = Color(0xFF93A8D1)  // Muted dusty slate-indigo
val GetiyIce = Color(0xFFF0F4FC)         // Crisp off-white text

val GetiyAccentBrush: Brush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFE5EDF9), Color(0xFFD2DFF4)),
)

// Semantic aliases aligned with Getiy's calm matte palette
val PopYellow = GetiyMint
val PopInk = GetiyNavy
val PopNavIdle = Color(0xFF8C9EC2)
val PopError = Color(0xFFD65A68)
val PopSkyBlue = GetiyAzure
val PopRosePink = GetiyPeriwinkle

val PopThumbPalette = listOf(
    Color(0xFFC8D7EE),
    Color(0xFFB5C7E4),
    Color(0xFFD5DFF0),
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
            bg = Color(0xFFF4F7FC),
            text = GetiyNavy,
            muted = Color(0xFF56688A),
            card = Color(0xFFFFFFFF),
            field = Color(0xFFE7EEF8),
            track = Color(0xFFD2DDF0),
            soft = Color(0xFFE2EAF7),
            trackOnSoft = Color(0xFFFFFFFF),
            strong = GetiyNavy,
            onStrong = Color(0xFFF4F7FC),
            bar = GetiyNavy,
            disabled = Color(0xFF8E9FBE),
            errorSoft = Color(0xFFFCE6E9),
            isDark = false,
        )

        val Dark = PopColors(
            bg = GetiyNavy,
            text = GetiyIce,
            muted = Color(0xFF8F9FC0),
            card = Color(0xFF152345),
            field = Color(0xFF1B2C54),
            track = Color(0xFF263A6A),
            soft = Color(0xFF1D305B),
            trackOnSoft = Color(0xFF121F3D),
            strong = GetiyMint,
            onStrong = GetiyNavyDeep,
            bar = Color(0xFF132142),
            disabled = Color(0xFF2B3C64),
            errorSoft = Color(0xFF3A1F2D),
            isDark = true,
        )
    }
}

val LocalPopColors = staticCompositionLocalOf { PopColors.Dark }

package com.example.core

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Crisp, iOS-style tactile feedback using Android haptic primitives (API 30+)
 * with clean predefined fallbacks for older devices for Getiy.
 */
object GetiyHaptics {
    @Volatile
    var enabled: Boolean = true

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun selection() = play("selection")
    fun soft() = play("soft")
    fun light() = play("light")
    fun medium() = play("medium")
    fun heavy() = play("heavy")
    fun success() = play("success")
    fun warning() = play("warning")
    fun error() = play("error")

    private fun supportsPrimitives(v: Vibrator): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            )
        }.getOrDefault(false)
    }

    private fun play(type: String) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!runCatching { v.hasVibrator() }.getOrDefault(false)) return
        val effect = if (supportsPrimitives(v)) composed(type) else predefined(type)
        effect ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect)
            }
        }
    }

    private fun composed(type: String): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val c = VibrationEffect.startComposition()
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val low = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        when (type) {
            "selection" -> c.addPrimitive(tick, 0.45f)
            "soft" -> c.addPrimitive(low, 0.6f)
            "light" -> c.addPrimitive(click, 0.45f)
            "medium" -> c.addPrimitive(click, 0.75f)
            "heavy" -> c.addPrimitive(click, 1.0f)
            "success" -> c.addPrimitive(click, 0.5f).addPrimitive(click, 1.0f, 70)
            "warning" -> c.addPrimitive(click, 1.0f).addPrimitive(click, 0.5f, 90)
            "error" -> c.addPrimitive(click, 0.8f).addPrimitive(click, 0.8f, 60).addPrimitive(click, 1.0f, 60)
            else -> c.addPrimitive(click, 0.5f)
        }
        return c.compose()
    }

    private fun predefined(type: String): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id = when (type) {
                "selection", "soft" -> VibrationEffect.EFFECT_TICK
                "light", "medium" -> VibrationEffect.EFFECT_CLICK
                "heavy" -> VibrationEffect.EFFECT_HEAVY_CLICK
                "success", "warning", "error" -> VibrationEffect.EFFECT_DOUBLE_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            return VibrationEffect.createPredefined(id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ms = when (type) {
                "selection", "soft" -> 8L
                "heavy" -> 25L
                "success", "warning", "error" -> 30L
                else -> 14L
            }
            return VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        return null
    }
}

package com.example.core

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Hardware capability helper that detects low-RAM / entry-level Android devices
 * and provides lean memory/buffer profiles so Getiy stays smooth without OOM crashes.
 */
object DeviceTier {

    private const val LOW_END_RAM_THRESHOLD_BYTES = 4L * 1024L * 1024L * 1024L // 4 GB

    @Volatile
    private var cachedIsLowEnd: Boolean? = null

    fun totalRamMb(context: Context): Int {
        val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 4096
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(512)
    }

    /**
     * Returns true for Android Go / low-RAM devices, phones with <= 4 GB RAM,
     * or devices with <= 4 CPU cores.
     */
    fun isLowEndDevice(context: Context): Boolean {
        cachedIsLowEnd?.let { return it }
        val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRamFlag = am?.isLowRamDevice ?: false
        val totalMem = am?.let {
            val info = ActivityManager.MemoryInfo()
            it.getMemoryInfo(info)
            info.totalMem
        } ?: Long.MAX_VALUE
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val result = lowRamFlag || totalMem <= LOW_END_RAM_THRESHOLD_BYTES || cpuCores <= 4
        cachedIsLowEnd = result
        return result
    }

    /**
     * Builds a memory-capped Media3 [LoadControl] instead of ExoPlayer's default 50s / 64+ MB buffer.
     * Prevents OOM crashes on low-end devices during 4K / high-bitrate playback while keeping seek fast.
     */
    @OptIn(UnstableApi::class)
    fun buildLeanLoadControl(context: Context, isInlinePreview: Boolean = false): LoadControl {
        val lowEnd = isLowEndDevice(context)
        val targetBytes = when {
            isInlinePreview -> 8 * 1024 * 1024 // 8 MB for inline trim preview
            lowEnd -> 12 * 1024 * 1024 // 12 MB for low-end 4K/HD playback
            else -> 24 * 1024 * 1024 // 24 MB for standard devices
        }
        val minBufferMs = if (isInlinePreview) 2_000 else 2_500
        val maxBufferMs = when {
            isInlinePreview -> 8_000
            lowEnd -> 10_000
            else -> 15_000
        }
        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                500, // bufferForPlaybackMs
                1_000, // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }
}

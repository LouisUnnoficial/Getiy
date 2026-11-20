package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.DeviceTier
import java.io.File

/**
 * Custom Application class configuring a low-memory Coil image pipeline
 * (RGB_565 bitmaps, capped RAM & disk caches) and aggressive memory trimming
 * so Getiy stays fast and reliable on low-end Android devices.
 */
class GetiyApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Clean up any leftover inline preview video cache from previous sessions
        runCatching {
            val previewDir = File(cacheDir, "preview")
            if (previewDir.exists()) previewDir.deleteRecursively()
        }
    }

    override fun newImageLoader(): ImageLoader {
        val lowEnd = DeviceTier.isLowEndDevice(this)
        return ImageLoader.Builder(this)
            .allowRgb565(true)
            .crossfade(!lowEnd)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Cap bitmap RAM to 10% on low-end devices (15% standard) instead of Coil's 25% default
                    .maxSizePercent(if (lowEnd) 0.10 else 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(if (lowEnd) 16L * 1024L * 1024L else 28L * 1024L * 1024L)
                    .build()
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            runCatching { Coil.imageLoader(this).memoryCache?.clear() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { Coil.imageLoader(this).memoryCache?.clear() }
    }
}

package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import com.example.data.model.LibraryItem
import com.example.service.AudioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlaybackState(
    val current: LibraryItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
)

/**
 * Background audio player controller with live progress updates, speed control, and notification sync.
 */
object AudioPlaybackManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null

    fun play(context: Context, item: LibraryItem) {
        val file = File(item.path)
        if (!file.exists()) return
        val appCtx = context.applicationContext

        if (_state.value.current?.path == item.path && player != null) {
            if (!_state.value.isPlaying) togglePlayPause(appCtx)
            return
        }

        releasePlayer()
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            setDataSource(file.absolutePath)
            prepare()
            start()
        }

        mp.setOnCompletionListener {
            _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
            AudioPlaybackService.sync(appCtx, item.title, item.site, isPlaying = false)
            tickerJob?.cancel()
        }

        player = mp
        _state.value = AudioPlaybackState(
            current = item,
            isPlaying = true,
            positionMs = 0L,
            durationMs = mp.duration.toLong().coerceAtLeast(0L),
            speed = 1.0f,
        )
        AudioPlaybackService.sync(appCtx, item.title, item.site, isPlaying = true)
        startTicker()
    }

    fun togglePlayPause(context: Context) {
        val mp = player ?: return
        val item = _state.value.current ?: return
        val appCtx = context.applicationContext
        if (mp.isPlaying) {
            mp.pause()
            tickerJob?.cancel()
            _state.update { it.copy(isPlaying = false, positionMs = mp.currentPosition.toLong()) }
            AudioPlaybackService.sync(appCtx, item.title, item.site, isPlaying = false)
        } else {
            mp.start()
            _state.update { it.copy(isPlaying = true) }
            AudioPlaybackService.sync(appCtx, item.title, item.site, isPlaying = true)
            startTicker()
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = player ?: return
        val clamped = positionMs.coerceIn(0L, mp.duration.toLong().coerceAtLeast(0L))
        runCatching { mp.seekTo(clamped.toInt()) }
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skipBy(deltaMs: Long) {
        val mp = player ?: return
        seekTo(mp.currentPosition.toLong() + deltaMs)
    }

    fun cycleSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        val cur = _state.value.speed
        val next = speeds[(speeds.indexOfFirst { it == cur } + 1) % speeds.size]
        player?.let { mp ->
            runCatching {
                val wasPlaying = mp.isPlaying
                mp.playbackParams = PlaybackParams().setSpeed(next)
                if (!wasPlaying && mp.isPlaying) mp.pause()
            }
        }
        _state.update { it.copy(speed = next) }
    }

    fun forgetIfPlaying(context: Context, path: String) {
        if (_state.value.current?.path == path) {
            stop(context)
        }
    }

    fun stop(context: Context) {
        releasePlayer()
        _state.value = AudioPlaybackState()
        AudioPlaybackService.sync(context.applicationContext, null, null, false)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val mp = player
                if (mp != null && mp.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = mp.currentPosition.toLong(),
                            durationMs = mp.duration.toLong().coerceAtLeast(it.durationMs),
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private fun releasePlayer() {
        tickerJob?.cancel()
        tickerJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}

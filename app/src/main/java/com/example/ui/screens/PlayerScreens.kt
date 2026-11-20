package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.AudioPlaybackState
import com.example.core.DeviceTier
import com.example.core.FileShareHelper
import com.example.core.GetiyHaptics
import com.example.data.model.LibraryItem
import com.example.data.model.MediaHelpers
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopThumb
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopYellow
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MiniPlayerBar(
    audioState: AudioPlaybackState,
    onOpenFullPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = audioState.current ?: return
    val pop = GetiyThemeTokens.pop

    Surface(
        onClick = onOpenFullPlayer,
        shape = PopPillShape,
        color = pop.card,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("mini_player_bar"),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopThumb(
                seed = item.path,
                audio = true,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = pop.text,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${MediaHelpers.clockLabel(audioState.positionMs / 1000.0)} / ${MediaHelpers.clockLabel(audioState.durationMs / 1000.0)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                )
            }
            PopIconButton(
                icon = if (audioState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (audioState.isPlaying) "Pause audio" else "Play audio",
                background = PopYellow,
                foreground = PopInk,
                size = 42.dp,
                onClick = onTogglePlayPause,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close audio player", tint = pop.muted)
            }
        }
    }
}

@Composable
fun AudioPlayerScreen(
    audioState: AudioPlaybackState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onMakeRingtone: (LibraryItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    val pop = GetiyThemeTokens.pop
    val context = LocalContext.current
    val item = audioState.current
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val duration = audioState.durationMs.coerceAtLeast(1L)
    val pos = audioState.positionMs.coerceIn(0L, duration)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            PopIconButton(
                icon = Icons.Rounded.RingVolume,
                contentDescription = "Make ringtone",
                onClick = { onMakeRingtone(item) },
            )
            Spacer(Modifier.width(8.dp))
            PopIconButton(
                icon = Icons.Rounded.IosShare,
                contentDescription = "Share audio",
                onClick = { FileShareHelper.shareFiles(context, listOf(item.path), item.title) },
            )
        }

        Spacer(Modifier.weight(1f))
        PopThumb(
            seed = item.path,
            audio = true,
            shape = PopCardShape,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(28.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.ExtraBold,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item.site.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.site,
                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
            )
        }

        Spacer(Modifier.height(24.dp))
        Slider(
            value = pos.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = PopYellow,
                activeTrackColor = pop.strong,
                inactiveTrackColor = pop.track,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = MediaHelpers.clockLabel(pos / 1000.0),
                style = MaterialTheme.typography.labelSmall.copy(color = pop.muted),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = MediaHelpers.clockLabel(duration / 1000.0),
                style = MaterialTheme.typography.labelSmall.copy(color = pop.muted),
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PopIconButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Back 10 seconds",
                size = 54.dp,
                onClick = { onSkip(-10_000L) },
            )
            Surface(
                onClick = {
                    GetiyHaptics.light()
                    onTogglePlayPause()
                },
                shape = CircleShape,
                color = pop.strong,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (audioState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (audioState.isPlaying) "Pause" else "Play",
                        tint = pop.onStrong,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            PopIconButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Forward 10 seconds",
                size = 54.dp,
                onClick = { onSkip(10_000L) },
            )
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            onClick = {
                GetiyHaptics.selection()
                onCycleSpeed()
            },
            shape = PopPillShape,
            color = pop.soft,
        ) {
            val label = if (audioState.speed == 1f) "1x" else "${audioState.speed}x"
            Text(
                text = "Speed · $label",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    item: LibraryItem,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var fillScreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var videoResolutionLabel by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(item.path) {
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
            )

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setForceHighestSupportedBitrate(true),
            )
        }

        androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(DeviceTier.buildLeanLoadControl(context, isInlinePreview = false))
            .build()
            .apply {
                val file = File(item.path)
                val mediaUri = if (file.exists()) Uri.fromFile(file) else Uri.parse(item.path)
                setMediaItem(androidx.media3.common.MediaItem.fromUri(mediaUri))
                playWhenReady = true
                prepare()
            }
    }

    // Listen for playback state, 4K/HD video size, and decoding errors
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                val dur = exoPlayer.duration
                if (dur > 0L) durationMs = dur
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    isPlaying = false
                    controlsVisible = true
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val h = videoSize.height
                val w = videoSize.width
                if (h > 0 && w > 0) {
                    val maxDim = maxOf(w, h)
                    val minDim = minOf(w, h)
                    videoResolutionLabel = when {
                        maxDim >= 7680 || minDim >= 4320 -> "8K · ${minDim}p"
                        maxDim >= 3840 || minDim >= 2160 -> "4K · ${minDim}p"
                        maxDim >= 2560 || minDim >= 1440 -> "2K · ${minDim}p"
                        else -> "${minDim}p"
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                isPlaying = false
                controlsVisible = true
                playbackError = error.localizedMessage ?: "Couldn't decode this video format on device"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause cleanly if the app moves to background
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        exoPlayer.pause()
    }

    // Poll smooth playhead position
    LaunchedEffect(exoPlayer, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                val dur = exoPlayer.duration
                if (dur > 0L) durationMs = dur
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(350)
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(controlsVisible, isPlaying, isScrubbing, playbackError) {
        if (controlsVisible && isPlaying && !isScrubbing && playbackError == null) {
            delay(3500)
            controlsVisible = false
        }
    }

    fun togglePlayPause() {
        GetiyHaptics.light()
        if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun skipBy(deltaMs: Long) {
        GetiyHaptics.selection()
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        positionMs = target
    }

    fun cycleSpeed() {
        GetiyHaptics.selection()
        val speeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
        val next = speeds[(speeds.indexOf(playbackSpeed) + 1) % speeds.size]
        playbackSpeed = next
        exoPlayer.setPlaybackSpeed(next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = if (fillScreen) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                view.keepScreenOn = isPlaying
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { controlsVisible = !controlsVisible },
        )

        if (isBuffering && playbackError == null) {
            androidx.compose.material3.CircularProgressIndicator(
                color = PopYellow,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
            )
        }

        if (playbackError != null) {
            Surface(
                shape = PopCardShape,
                color = PopInk.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Playback error",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = playbackError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.78f)),
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = {
                                playbackError = null
                                isBuffering = true
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            },
                            shape = PopPillShape,
                            color = PopYellow,
                        ) {
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = PopInk,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                        Surface(
                            onClick = { FileShareHelper.openFileExternally(context, item.path) },
                            shape = PopPillShape,
                            color = Color.White.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = "Open externally",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.56f))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PopIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        background = PopInk.copy(alpha = 0.75f),
                        foreground = Color.White,
                        onClick = onBack,
                        tag = "video_player_back_button",
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (videoResolutionLabel != null) {
                            Text(
                                text = videoResolutionLabel!!,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PopYellow,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            GetiyHaptics.selection()
                            fillScreen = !fillScreen
                        },
                        shape = PopPillShape,
                        color = PopInk.copy(alpha = 0.75f),
                        modifier = Modifier.testTag("video_player_fit_button"),
                    ) {
                        Text(
                            text = if (fillScreen) "Fill" else "Fit",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PopIconButton(
                        icon = Icons.Rounded.IosShare,
                        contentDescription = "Share video",
                        background = PopInk.copy(alpha = 0.75f),
                        foreground = Color.White,
                        onClick = { FileShareHelper.shareFiles(context, listOf(item.path), item.title) },
                        tag = "video_player_share_button",
                    )
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.68f))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    val displayedPos = if (isScrubbing) scrubPositionMs else positionMs
                    Slider(
                        value = displayedPos.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = {
                            isScrubbing = true
                            scrubPositionMs = it.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(scrubPositionMs)
                            positionMs = scrubPositionMs
                            isScrubbing = false
                        },
                        valueRange = 0f..durationMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = PopYellow,
                            activeTrackColor = PopYellow,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${MediaHelpers.clockLabel(displayedPos / 1000.0)} / ${MediaHelpers.clockLabel(durationMs / 1000.0)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PopIconButton(
                                icon = Icons.Rounded.Replay10,
                                contentDescription = "Back 10 seconds",
                                background = Color.White.copy(alpha = 0.14f),
                                foreground = Color.White,
                                size = 44.dp,
                                onClick = { skipBy(-10_000L) },
                            )
                            PopIconButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause video" else "Play video",
                                background = PopYellow,
                                foreground = PopInk,
                                size = 52.dp,
                                onClick = ::togglePlayPause,
                                tag = "video_player_play_pause_button",
                            )
                            PopIconButton(
                                icon = Icons.Rounded.Forward10,
                                contentDescription = "Forward 10 seconds",
                                background = Color.White.copy(alpha = 0.14f),
                                foreground = Color.White,
                                size = 44.dp,
                                onClick = { skipBy(10_000L) },
                            )
                        }
                        Surface(
                            onClick = ::cycleSpeed,
                            shape = PopPillShape,
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.testTag("video_player_speed_button"),
                        ) {
                            val speedLabel = if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x"
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.net.Uri
import android.widget.VideoView
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
import com.example.core.FileShareHelper
import com.example.core.GrabHaptics
import com.example.data.model.LibraryItem
import com.example.data.model.MediaHelpers
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopThumb
import com.example.ui.theme.GrabThemeTokens
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
    val pop = GrabThemeTokens.pop

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
    val pop = GrabThemeTokens.pop
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
                    GrabHaptics.light()
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
                GrabHaptics.selection()
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

@Composable
fun VideoPlayerScreen(
    item: LibraryItem,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }

    LaunchedEffect(videoViewRef) {
        while (true) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying) {
                positionMs = vv.currentPosition.toLong()
                durationMs = vv.duration.toLong().coerceAtLeast(1L)
            }
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { videoViewRef?.stopPlayback() } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.fromFile(File(item.path)))
                    setOnPreparedListener { mp ->
                        durationMs = mp.duration.toLong().coerceAtLeast(1L)
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    val vv = videoViewRef ?: return@clickable
                    if (vv.isPlaying) {
                        vv.pause()
                        isPlaying = false
                    } else {
                        vv.start()
                        isPlaying = true
                    }
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                background = PopInk.copy(alpha = 0.7f),
                foreground = Color.White,
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PopIconButton(
                icon = Icons.Rounded.IosShare,
                contentDescription = "Share video",
                background = PopInk.copy(alpha = 0.7f),
                foreground = Color.White,
                onClick = { FileShareHelper.shareFiles(context, listOf(item.path), item.title) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Slider(
                value = positionMs.coerceIn(0L, durationMs).toFloat(),
                onValueChange = {
                    positionMs = it.toLong()
                    videoViewRef?.seekTo(it.toInt())
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
                    text = MediaHelpers.clockLabel(positionMs / 1000.0),
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                )
                PopIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    background = PopYellow,
                    foreground = PopInk,
                    onClick = {
                        val vv = videoViewRef ?: return@PopIconButton
                        if (vv.isPlaying) {
                            vv.pause()
                            isPlaying = false
                        } else {
                            vv.start()
                            isPlaying = true
                        }
                    },
                )
                Text(
                    text = MediaHelpers.clockLabel(durationMs / 1000.0),
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                )
            }
        }
    }
}

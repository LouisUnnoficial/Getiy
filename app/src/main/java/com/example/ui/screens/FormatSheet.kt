package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.DeviceTier
import com.example.core.GetiyHaptics
import com.example.data.engine.GetiyEngine
import com.example.data.model.DownloadMode
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.data.model.TrimRange
import com.example.data.settings.AppSettings
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopOptionTile
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSection
import com.example.ui.components.PopSegmented
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopYellow
import com.example.ui.viewmodel.FetchUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatBottomSheet(
    fetchState: FetchUiState,
    settings: AppSettings,
    engine: GetiyEngine,
    onDismiss: () -> Unit,
    onModeChange: (DownloadMode) -> Unit,
    onOptionChange: (Int) -> Unit,
    onTrimToggle: (Boolean) -> Unit,
    onTrimChange: (Double, Double) -> Unit,
    onToggleSubLang: (String) -> Unit,
    onToggleSubAuto: (Boolean) -> Unit,
    onToggleSubEmbed: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    val info = fetchState.currentInfo ?: return
    val pop = GetiyThemeTokens.pop
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val modes = remember(info) {
        buildList {
            add(DownloadMode.VIDEO_AND_AUDIO)
            add(DownloadMode.VIDEO)
            add(DownloadMode.AUDIO_ONLY)
            if (info.subtitles.isNotEmpty()) add(DownloadMode.SUBTITLES_ONLY)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pop.bg,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = "Choose format",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = pop.text,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            ClipPreviewSection(
                info = info,
                trim = if (fetchState.mode == DownloadMode.SUBTITLES_ONLY) null else fetchState.trim,
                engine = engine,
                onTrimChange = onTrimChange,
            )

            if (fetchState.mode != DownloadMode.SUBTITLES_ONLY && (info.duration ?: 0.0) >= 2.0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PopCardShape)
                        .background(pop.card)
                        .clickable {
                            GetiyHaptics.selection()
                            onTrimToggle(fetchState.trim == null)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        tint = pop.text,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save only a part",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = pop.text,
                            ),
                        )
                        Text(
                            text = if (fetchState.trim == null) {
                                "Pick the part on the video above"
                            } else {
                                "Keeping ${MediaHelpers.clockLabel(fetchState.trim.start)} to ${MediaHelpers.clockLabel(fetchState.trim.end)}"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                        )
                    }
                    Switch(
                        checked = fetchState.trim != null,
                        onCheckedChange = {
                            GetiyHaptics.selection()
                            onTrimToggle(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pop.onStrong,
                            checkedTrackColor = pop.strong,
                        ),
                        modifier = Modifier.testTag("format_trim_switch"),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = pop.text,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                info.site,
                info.uploader,
                info.duration?.let { MediaHelpers.clockLabel(it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                )
            }

            Spacer(Modifier.height(18.dp))
            PopSegmented(
                values = modes,
                labels = modes.map { it.shortLabel },
                selected = fetchState.mode,
                onSelected = onModeChange,
            )

            Spacer(Modifier.height(18.dp))
            if (fetchState.mode != DownloadMode.SUBTITLES_ONLY) {
                PopSection(if (fetchState.mode == DownloadMode.AUDIO_ONLY) "Audio" else "Quality")
                val opts = fetchState.options
                val clamped = fetchState.optionIndex.coerceIn(0, (opts.size - 1).coerceAtLeast(0))
                opts.forEachIndexed { idx, opt ->
                    val ext = if (fetchState.mode == DownloadMode.AUDIO_ONLY) {
                        settings.audioFormat.uppercase()
                    } else {
                        settings.container.uppercase()
                    }
                    PopOptionTile(
                        label = "${opt.label} · $ext",
                        sizeLabel = MediaHelpers.formatBytes(opt.bytes),
                        selected = idx == clamped,
                        onClick = { onOptionChange(idx) },
                    )
                }
            }

            if (fetchState.mode != DownloadMode.AUDIO_ONLY && info.subtitles.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                PopSection("Subtitles")
                val manualSubs = info.subtitles.filter { !it.isAuto }.take(12)
                val autoSubs = info.subtitles.filter { it.isAuto }.take(12)
                val shownSubs = if (fetchState.subtitles.includeAuto || manualSubs.isEmpty()) {
                    (manualSubs + autoSubs).distinctBy { it.lang }.take(14)
                } else {
                    manualSubs
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    shownSubs.forEach { track ->
                        val selected = track.lang in fetchState.subtitles.langs
                        FilterChip(
                            selected = selected,
                            onClick = {
                                GetiyHaptics.selection()
                                onToggleSubLang(track.lang)
                            },
                            label = { Text(track.lang.uppercase()) },
                            shape = PopPillShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = pop.soft,
                                selectedContainerColor = pop.strong,
                                labelColor = pop.text,
                                selectedLabelColor = pop.onStrong,
                            ),
                            border = null,
                        )
                    }
                }
                if (autoSubs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Include auto-generated captions",
                            style = MaterialTheme.typography.bodyMedium.copy(color = pop.text),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = fetchState.subtitles.includeAuto,
                            onCheckedChange = onToggleSubAuto,
                        )
                    }
                }
                if (fetchState.mode != DownloadMode.SUBTITLES_ONLY && !fetchState.subtitles.isEmpty) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Embed subtitles in video",
                            style = MaterialTheme.typography.bodyMedium.copy(color = pop.text),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = fetchState.subtitles.embed,
                            onCheckedChange = onToggleSubEmbed,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            val ctaLabel = when (fetchState.mode) {
                DownloadMode.VIDEO_AND_AUDIO -> "Download video + audio"
                DownloadMode.VIDEO -> "Download video"
                DownloadMode.AUDIO_ONLY -> "Download audio"
                DownloadMode.SUBTITLES_ONLY -> "Download subtitles"
            }
            val canDownload = fetchState.mode != DownloadMode.SUBTITLES_ONLY || !fetchState.subtitles.isEmpty
            PopButton(
                label = ctaLabel,
                icon = Icons.Rounded.Download,
                useGradient = true,
                enabled = canDownload,
                onClick = onDownload,
                tag = "format_download_button",
            )
        }
    }
}

/**
 * Interactive video preview with CapCut-style draggable yellow trim handles.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ClipPreviewSection(
    info: MediaInfo,
    trim: TrimRange?,
    engine: GetiyEngine,
    onTrimChange: (Double, Double) -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val duration = info.duration ?: 0.0

    var playing by remember { mutableStateOf(false) }
    var loadingPreview by remember { mutableStateOf(false) }
    var previewUri by remember(info.url) { mutableStateOf<Uri?>(null) }
    var previewHeaders by remember(info.url) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var triedLocalFallback by remember(info.url) { mutableStateOf(false) }
    var currentSec by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(previewUri, previewHeaders) {
        val uri = previewUri ?: return@remember null
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (previewHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(previewHeaders)
                }
            }
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(DeviceTier.buildLeanLoadControl(context, isInlinePreview = true))
            .build()
            .apply {
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                if (trim != null) seekTo((trim.start * 1000).toLong())
                playWhenReady = true
                prepare()
            }
    }

    fun loadCachedPreviewFallback() {
        if (triedLocalFallback || loadingPreview || duration !in 1.0..600.0) return
        triedLocalFallback = true
        loadingPreview = true
        scope.launch {
            val cached = runCatching { engine.previewFile(info.url) }.getOrNull()
            loadingPreview = false
            if (cached != null && File(cached).exists()) {
                previewHeaders = emptyMap()
                previewUri = Uri.fromFile(File(cached))
                playing = true
            } else {
                playing = false
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playing = false
                    // If direct stream URL failed (e.g., signed CDN headers), fall back to cached preview
                    if (previewUri?.scheme != "file") {
                        previewUri = null
                        loadCachedPreviewFallback()
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    // Keep playback looped inside trim range
    LaunchedEffect(exoPlayer, playing, trim) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (playing) {
            if (player.isPlaying) {
                val posSec = player.currentPosition / 1000f
                currentSec = posSec
                if (trim != null && (posSec >= trim.end || posSec < trim.start - 0.5)) {
                    player.seekTo((trim.start * 1000).toLong())
                }
            }
            delay(150)
        }
    }

    fun startOrTogglePreview() {
        val player = exoPlayer
        if (previewUri != null && player != null) {
            if (player.isPlaying) {
                player.pause()
                playing = false
            } else {
                if (trim != null) player.seekTo((trim.start * 1000).toLong())
                player.play()
                playing = true
            }
            return
        }
        val stream = info.previewFormat
        val isTikTok = info.site.orEmpty().contains("tiktok", ignoreCase = true)
        if (stream?.url != null && !isTikTok) {
            previewHeaders = stream.headers
            previewUri = Uri.parse(stream.url)
            playing = true
            return
        }
        loadCachedPreviewFallback()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(PopCardShape)
                .background(PopInk)
                .clickable { startOrTogglePreview() },
            contentAlignment = Alignment.Center,
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PopThumb(
                    seed = info.url,
                    url = info.thumbnail,
                    modifier = Modifier.fillMaxSize(),
                    shape = PopCardShape,
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PopInk.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                if (loadingPreview) {
                    CircularProgressIndicator(
                        color = PopYellow,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause preview" else "Play preview",
                        tint = PopYellow,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        // CapCut-style draggable yellow trim bar
        if (trim != null && duration >= 2.0) {
            Spacer(Modifier.height(12.dp))
            PopTrimBar(
                duration = duration,
                trim = trim,
                playheadSec = currentSec.toDouble(),
                onTrimChange = { s, e ->
                    onTrimChange(s, e)
                    exoPlayer?.let { player ->
                        val target = if (kotlin.math.abs(s - trim.start) > 0.05) s else e
                        runCatching { player.seekTo((target * 1000).toLong()) }
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = MediaHelpers.clockLabel(trim.start),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = pop.text,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.weight(1f))
                PopTag("${(trim.end - trim.start).toInt().coerceAtLeast(1)}s clip")
                Spacer(Modifier.weight(1f))
                Text(
                    text = MediaHelpers.clockLabel(trim.end),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = pop.text,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PopTrimBar(
    duration: Double,
    trim: TrimRange,
    playheadSec: Double,
    onTrimChange: (Double, Double) -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    var widthPx by remember { mutableFloatStateOf(1f) }
    var draggingStart by remember { mutableStateOf<Boolean?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pop.field)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(duration, trim) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val startPx = (trim.start / duration).toFloat() * widthPx
                        val endPx = (trim.end / duration).toFloat() * widthPx
                        draggingStart = kotlin.math.abs(offset.x - startPx) <= kotlin.math.abs(offset.x - endPx)
                        GetiyHaptics.selection()
                    },
                    onDragEnd = { draggingStart = null },
                    onDragCancel = { draggingStart = null },
                    onHorizontalDrag = { _, dragAmount ->
                        val deltaSec = (dragAmount / widthPx) * duration
                        when (draggingStart) {
                            true -> {
                                val nextStart = (trim.start + deltaSec).coerceIn(0.0, (trim.end - 1.0).coerceAtLeast(0.0))
                                onTrimChange(nextStart, trim.end)
                            }
                            false -> {
                                val nextEnd = (trim.end + deltaSec).coerceIn((trim.start + 1.0).coerceAtMost(duration), duration)
                                onTrimChange(trim.start, nextEnd)
                            }
                            null -> Unit
                        }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val startX = ((trim.start / duration) * w).toFloat().coerceIn(0f, w)
            val endX = ((trim.end / duration) * w).toFloat().coerceIn(startX, w)
            val handleW = 14.dp.toPx()

            // Waveform / filmstrip ticks
            val barCount = 36
            val step = w / barCount
            for (i in 0 until barCount) {
                val x = i * step + step * 0.5f
                val frac = ((i * 7 + 3) % 10) / 10f
                val barH = h * (0.28f + frac * 0.42f)
                val inRange = x in startX..endX
                drawRoundRect(
                    color = if (inRange) pop.text.copy(alpha = 0.5f) else pop.muted.copy(alpha = 0.25f),
                    topLeft = Offset(x - 1.5.dp.toPx(), (h - barH) / 2f),
                    size = Size(3.dp.toPx(), barH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }

            // Dimmed outside regions
            drawRect(Color.Black.copy(alpha = 0.22f), Offset.Zero, Size(startX, h))
            drawRect(Color.Black.copy(alpha = 0.22f), Offset(endX, 0f), Size(w - endX, h))

            // Yellow top & bottom frame borders
            val borderH = 4.dp.toPx()
            drawRect(PopYellow, Offset(startX, 0f), Size((endX - startX).coerceAtLeast(handleW), borderH))
            drawRect(PopYellow, Offset(startX, h - borderH), Size((endX - startX).coerceAtLeast(handleW), borderH))

            // Left & right trim handles
            drawRoundRect(
                color = PopYellow,
                topLeft = Offset(startX, 0f),
                size = Size(handleW, h),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = PopYellow,
                topLeft = Offset((endX - handleW).coerceAtLeast(startX), 0f),
                size = Size(handleW, h),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            // Handle grip pips
            drawLine(PopInk, Offset(startX + handleW / 2f, h * 0.3f), Offset(startX + handleW / 2f, h * 0.7f), strokeWidth = 2.dp.toPx())
            val rightCenter = (endX - handleW).coerceAtLeast(startX) + handleW / 2f
            drawLine(PopInk, Offset(rightCenter, h * 0.3f), Offset(rightCenter, h * 0.7f), strokeWidth = 2.dp.toPx())

            // Playhead line
            if (playheadSec in trim.start..trim.end) {
                val px = ((playheadSec / duration) * w).toFloat()
                drawLine(Color.White, Offset(px, 0f), Offset(px, h), strokeWidth = 2.5.dp.toPx())
            }
        }
    }
}

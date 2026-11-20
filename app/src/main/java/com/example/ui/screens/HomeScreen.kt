package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileShareHelper
import com.example.core.GetiyHaptics
import com.example.core.Links
import com.example.data.db.DownloadJobEntity
import com.example.data.model.DownloadMode
import com.example.data.model.JobStatus
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopProgressBar
import com.example.ui.components.PopSection
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyAzure
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyNavyDeep
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.viewmodel.FetchResult
import com.example.ui.viewmodel.FetchUiState

@Composable
fun HomeScreen(
    fetchState: FetchUiState,
    recentJobs: List<DownloadJobEntity>,
    speeds: Map<String, String>,
    activeCount: Int,
    savedCount: Int,
    engineVersion: String?,
    extraBottomPadding: Int,
    onUrlChange: (String) -> Unit,
    onPasteAndFetch: () -> Unit,
    onFetch: () -> Unit,
    onReopenReady: () -> Unit,
    onOpenJobDetail: (String) -> Unit,
    onJumpToDownloads: () -> Unit,
    onJumpToLibrary: () -> Unit,
    onCopyError: (String) -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val detectedPlatform = remember(fetchState.urlText) {
        Links.platformOf(Links.extractLink(fetchState.urlText) ?: fetchState.urlText)
    }
    val isBusy = fetchState.result is FetchResult.Loading
    val activeJobs = remember(recentJobs) { recentJobs.filter { it.jobStatus.isActive } }
    val completedRecent = remember(recentJobs) {
        recentJobs.filter { it.jobStatus == JobStatus.DONE }.take(6)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg),
    ) {
        // Scrollable Studio Dashboard above the bottom Fetch Command Bar
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = (188 + extraBottomPadding).dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Bento Hero + Live Metrics Grid
            item(key = "home_bento_hero") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Featured asymmetric hero card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PopCardShape)
                            .background(pop.card)
                            .border(1.dp, pop.track, PopCardShape)
                            .padding(20.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(PopPillShape)
                                        .background(GetiyAccentBrush)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "1,800+ SITES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = GetiyNavyDeep,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.6.sp,
                                        ),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (engineVersion != null) {
                                    Text(
                                        text = "yt-dlp $engineVersion",
                                        style = MaterialTheme.typography.labelSmall.copy(color = pop.muted),
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Capture video,\naudio & clips.",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    color = pop.text,
                                    fontSize = 32.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Drop any link in the dock below or tap Quick Paste to fetch instantly.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                            )
                        }
                    }

                    // 2-Column Live Telemetry Bento Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BentoStatTile(
                            label = "Active Queue",
                            value = "$activeCount",
                            subtitle = if (activeCount == 1) "running now" else "in progress",
                            icon = Icons.Rounded.Download,
                            accent = GetiyMint,
                            onClick = onJumpToDownloads,
                            modifier = Modifier.weight(1f),
                        )
                        BentoStatTile(
                            label = "Saved Vault",
                            value = "$savedCount",
                            subtitle = if (savedCount == 1) "ready offline" else "files saved",
                            icon = Icons.Rounded.VideoLibrary,
                            accent = GetiyAzure,
                            onClick = onJumpToLibrary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Live Fetch Status Banner (Loading / Ready / Error)
            if (fetchState.result !is FetchResult.Idle) {
                item(key = "fetch_status") {
                    when (val res = fetchState.result) {
                        is FetchResult.Idle -> Unit
                        is FetchResult.Loading -> FetchingSkeletonCard(url = res.url)
                        is FetchResult.Ready -> ReadyMediaCard(info = res.info, onOpen = onReopenReady)
                        is FetchResult.Error -> FetchErrorCard(
                            message = res.message,
                            onRetry = onFetch,
                            onCopy = {
                                FileShareHelper.copyToClipboard(context, "Getiy error", res.message)
                                onCopyError(res.message)
                            },
                        )
                    }
                }
            }

            // Live Active Download Progress Card when engines are actively processing a download
            if (activeJobs.isNotEmpty()) {
                item(key = "home_active_transfer") {
                    ActiveDownloadProgressCard(
                        job = activeJobs.first(),
                        totalActive = activeJobs.size,
                        speed = speeds[activeJobs.first().id],
                        onClick = onJumpToDownloads,
                    )
                }
            }

            // Horizontal Scrollable Platform Strip
            item(key = "platforms_strip") {
                Column {
                    PopSection("Supported Sources")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Links.platforms.forEach { platform ->
                            PlatformPill(
                                label = platform,
                                active = detectedPlatform == platform,
                            )
                        }
                        PlatformPill(label = "+ 1,800 more", active = false)
                    }
                }
            }

            // Horizontal Recent Captures Carousel
            if (completedRecent.isNotEmpty()) {
                item(key = "recent_carousel") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PopSection("Recent Captures", modifier = Modifier.weight(1f))
                            Text(
                                text = "View all",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = pop.strong,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier
                                    .clickable {
                                        GetiyHaptics.selection()
                                        onJumpToLibrary()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(completedRecent, key = { it.id }) { job ->
                                RecentCaptureCard(
                                    job = job,
                                    onClick = { onOpenJobDetail(job.id) },
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "quick_tips_card") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PopCardShape)
                            .background(pop.card)
                            .border(1.dp, pop.track, PopCardShape)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(pop.soft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = pop.strong,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share from any app",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = pop.text,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Tap Share on any video or track and choose Getiy to jump straight to format selection.",
                                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                            )
                        }
                    }
                }
            }
        }

        // Bottom Thumb-Zone Fetch Command Dock (replaces top URL bar!)
        Surface(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = if (pop.isDark) Color(0xFA132245) else Color(0xFAFFFFFF),
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, pop.track),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("dock_fetch_progress_indicator"),
                        color = pop.strong,
                        trackColor = pop.track,
                        strokeCap = StrokeCap.Round,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                // Input field row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(pop.field)
                        .border(
                            width = 1.dp,
                            color = if (detectedPlatform != null) pop.strong else pop.track,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = if (detectedPlatform != null) pop.strong else pop.muted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (fetchState.urlText.isEmpty()) {
                            Text(
                                text = "Paste a video or audio URL…",
                                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = fetchState.urlText,
                            onValueChange = onUrlChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = pop.text),
                            cursorBrush = SolidColor(pop.strong),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    onFetch()
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_url_input"),
                        )
                    }
                    if (fetchState.urlText.isNotEmpty() && !isBusy) {
                        IconButton(
                            onClick = { onUrlChange("") },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("home_url_clear_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear link",
                                tint = pop.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Bottom Action Row: Quick Paste + Prominent Gradient Fetch CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = {
                            GetiyHaptics.selection()
                            onPasteAndFetch()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = pop.soft,
                        border = BorderStroke(1.dp, pop.track),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("home_paste_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste link and fetch",
                                tint = pop.text,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Paste",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = pop.text,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            )
                        }
                    }

                    PopButton(
                        label = if (detectedPlatform != null) "Fetch from $detectedPlatform" else "Fetch Media",
                        icon = Icons.Rounded.ArrowForward,
                        onClick = {
                            focusManager.clearFocus()
                            onFetch()
                        },
                        busy = isBusy,
                        enabled = fetchState.urlText.isNotBlank(),
                        expand = true,
                        height = 50.dp,
                        useGradient = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        tag = "home_fetch_button",
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun BentoStatTile(
    label: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = modifier
            .clip(PopCardShape)
            .background(pop.card)
            .border(1.dp, pop.track, PopCardShape)
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
        )
    }
}

@Composable
private fun PlatformPill(label: String, active: Boolean) {
    val pop = GetiyThemeTokens.pop
    val bg = if (active) pop.strong else pop.card
    val fg = if (active) pop.onStrong else pop.text
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (active) pop.strong else pop.track, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) pop.onStrong else GetiyMint),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = fg,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun FetchingSkeletonCard(url: String) {
    val pop = GetiyThemeTokens.pop
    val platform = remember(url) { Links.platformOf(url) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.card)
            .border(1.dp, pop.strong.copy(alpha = 0.4f), PopCardShape)
            .padding(16.dp)
            .testTag("home_fetching_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(pop.soft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = pop.strong,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (platform != null) "Processing $platform link…" else "Processing link…",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = pop.text,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Links.displayHostAndPath(url),
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(PopPillShape)
                .testTag("home_fetch_progress_indicator"),
            color = pop.strong,
            trackColor = pop.track,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Reading formats, quality & streams via yt-dlp…",
            style = MaterialTheme.typography.labelSmall.copy(
                color = pop.muted,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun ActiveDownloadProgressCard(
    job: DownloadJobEntity,
    totalActive: Int,
    speed: String?,
    onClick: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    val status = job.jobStatus
    val pct = job.progress.toInt().coerceIn(0, 100)
    val indeterminate = status == JobStatus.QUEUED ||
        status == JobStatus.WAITING ||
        (status == JobStatus.RUNNING && (job.progress <= 0.0 || job.progress >= 100.0))

    val subtitle = when (status) {
        JobStatus.QUEUED -> "Queued in transfer engine…"
        JobStatus.WAITING -> "Reconnecting…"
        JobStatus.PAUSED -> "Paused at $pct%"
        JobStatus.RUNNING -> if (job.progress >= 100.0) {
            "Muxing & finalizing with FFmpeg…"
        } else {
            listOfNotNull(
                speed,
                job.eta?.takeIf { it > 0 }?.let { "${MediaHelpers.etaLabel(it)} left" },
            ).ifEmpty { listOf("Downloading via yt-dlp…") }.joinToString(" · ")
        }
        else -> "Processing…"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.soft)
            .border(1.dp, pop.strong.copy(alpha = 0.45f), PopCardShape)
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(16.dp)
            .testTag("home_active_download_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (totalActive > 1) "PROCESSING ($totalActive ACTIVE)" else "PROCESSING DOWNLOAD",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = pop.strong,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            if (!indeterminate) {
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = pop.strong,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = job.title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        PopProgressBar(
            progress = if (indeterminate) null else (job.progress / 100.0).toFloat(),
            trackColor = pop.trackOnSoft,
            height = 6.dp,
            modifier = Modifier.testTag("home_active_download_progress"),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "View queue",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = pop.strong,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun ReadyMediaCard(info: MediaInfo, onOpen: () -> Unit) {
    val pop = GetiyThemeTokens.pop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.soft)
            .border(1.dp, pop.strong.copy(alpha = 0.5f), PopCardShape)
            .clickable {
                GetiyHaptics.light()
                onOpen()
            }
            .padding(14.dp)
            .testTag("home_ready_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopThumb(
            seed = info.url,
            url = info.thumbnail,
            modifier = Modifier.size(68.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "READY TO DOWNLOAD",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = pop.strong,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (info.isPlaylist) "Pick from ${info.entries.size} items" else "Tap to choose format & trim",
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ArrowForward,
            contentDescription = "Open options",
            tint = pop.strong,
        )
    }
}

@Composable
private fun FetchErrorCard(
    message: String,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.errorSoft)
            .padding(start = 18.dp, top = 16.dp, end = 10.dp, bottom = 8.dp),
    ) {
        Text(
            text = "Couldn't read that link",
            style = MaterialTheme.typography.titleMedium.copy(
                color = pop.text,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = pop.text),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Row {
            TextButton(onClick = onRetry) {
                Text("Try again", fontWeight = FontWeight.Bold, color = pop.text)
            }
            TextButton(onClick = onCopy) {
                Text("Copy error", color = pop.muted)
            }
        }
    }
}

@Composable
private fun RecentCaptureCard(job: DownloadJobEntity, onClick: () -> Unit) {
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = Modifier
            .width(196.dp)
            .clip(PopCardShape)
            .background(pop.card)
            .border(1.dp, pop.track, PopCardShape)
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
        ) {
            PopThumb(
                seed = job.id,
                url = job.thumbnail,
                audio = job.downloadMode == DownloadMode.AUDIO_ONLY,
                subtitles = job.downloadMode == DownloadMode.SUBTITLES_ONLY,
                modifier = Modifier.fillMaxSize(),
            )
            PopTag(
                label = job.formatBadge,
                background = GetiyNavy.copy(alpha = 0.85f),
                contentColor = GetiyMint,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = job.title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!job.site.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = job.site,
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

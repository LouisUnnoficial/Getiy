package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
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
import com.example.core.GrabHaptics
import com.example.core.Links
import com.example.data.db.DownloadJobEntity
import com.example.data.model.DownloadMode
import com.example.data.model.JobStatus
import com.example.data.model.MediaInfo
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSection
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopRosePink
import com.example.ui.theme.PopSkyBlue
import com.example.ui.theme.PopYellow
import com.example.ui.viewmodel.FetchResult
import com.example.ui.viewmodel.FetchUiState
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    fetchState: FetchUiState,
    recentJobs: List<DownloadJobEntity>,
    engineVersion: String?,
    extraBottomPadding: Int,
    onUrlChange: (String) -> Unit,
    onPasteAndFetch: () -> Unit,
    onFetch: () -> Unit,
    onReopenReady: () -> Unit,
    onOpenJobDetail: (String) -> Unit,
    onCopyError: (String) -> Unit,
) {
    val pop = GrabThemeTokens.pop
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val detectedPlatform = Links.platformOf(Links.extractLink(fetchState.urlText) ?: fetchState.urlText)
    val isBusy = fetchState.result is FetchResult.Loading
    val completedRecent = recentJobs.filter { it.jobStatus == JobStatus.DONE }.take(3)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 12.dp,
            bottom = (116 + extraBottomPadding).dp,
        ),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Grab",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = pop.text,
                    ),
                )
                Spacer(Modifier.weight(1f))
                PopIconButton(
                    icon = Icons.Rounded.ContentPaste,
                    contentDescription = "Paste link and fetch",
                    onClick = onPasteAndFetch,
                    tag = "home_paste_button",
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item(key = "hero") {
            PopHeroHeadline()
            Spacer(Modifier.height(24.dp))
        }

        item(key = "url_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PopPillShape)
                    .background(pop.field)
                    .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (fetchState.urlText.isEmpty()) {
                        Text(
                            text = "Paste a video link",
                            style = MaterialTheme.typography.bodyLarge.copy(color = pop.muted),
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = fetchState.urlText,
                        onValueChange = onUrlChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = pop.text),
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
                        modifier = Modifier.testTag("home_url_clear_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear link",
                            tint = pop.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                PopButton(
                    label = "Fetch",
                    onClick = {
                        focusManager.clearFocus()
                        onFetch()
                    },
                    busy = isBusy,
                    enabled = fetchState.urlText.isNotBlank(),
                    expand = false,
                    height = 48.dp,
                    tag = "home_fetch_button",
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "platforms") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Links.platforms.forEach { platform ->
                    PlatformChip(
                        label = platform,
                        active = detectedPlatform == platform,
                    )
                }
                PlatformChip(label = "+ 1,800 more", active = false)
            }
            Spacer(Modifier.height(22.dp))
        }

        item(key = "fetch_status") {
            when (val res = fetchState.result) {
                is FetchResult.Idle -> Unit
                is FetchResult.Loading -> {
                    FetchingSkeletonCard()
                    Spacer(Modifier.height(22.dp))
                }
                is FetchResult.Ready -> {
                    ReadyMediaCard(info = res.info, onOpen = onReopenReady)
                    Spacer(Modifier.height(22.dp))
                }
                is FetchResult.Error -> {
                    FetchErrorCard(
                        message = res.message,
                        onRetry = onFetch,
                        onCopy = {
                            FileShareHelper.copyToClipboard(context, "Grab error", res.message)
                            onCopyError(res.message)
                        },
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }
        }

        if (completedRecent.isNotEmpty()) {
            item(key = "recent_header") {
                PopSection("Recent")
            }
            items(completedRecent, key = { it.id }) { job ->
                RecentJobRow(job = job, onClick = { onOpenJobDetail(job.id) })
            }
        }

        item(key = "engine_footer") {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (engineVersion != null) "yt-dlp $engineVersion · ready" else "Starting yt-dlp engine…",
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun PopHeroHeadline() {
    val pop = GrabThemeTokens.pop
    val infinite = rememberInfiniteTransition(label = "heroBlobs")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blobPhase",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alpha = if (pop.isDark) 0.24f else 0.55f
            drawCircle(
                color = PopYellow.copy(alpha = alpha),
                radius = 48.dp.toPx(),
                center = Offset(
                    x = w * 0.78f + cos(phase) * 14.dp.toPx(),
                    y = h * 0.34f + sin(phase) * 10.dp.toPx(),
                ),
            )
            drawCircle(
                color = PopSkyBlue.copy(alpha = alpha),
                radius = 30.dp.toPx(),
                center = Offset(
                    x = w * 0.90f - sin(phase) * 10.dp.toPx(),
                    y = h * 0.76f + cos(phase) * 8.dp.toPx(),
                ),
            )
            drawCircle(
                color = PopRosePink.copy(alpha = alpha),
                radius = 22.dp.toPx(),
                center = Offset(
                    x = w * 0.64f + sin(phase) * 8.dp.toPx(),
                    y = h * 0.78f - cos(phase) * 8.dp.toPx(),
                ),
            )
        }
        Text(
            text = "Paste a link.\nGet the file.",
            style = MaterialTheme.typography.displayLarge.copy(
                color = pop.text,
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun PlatformChip(label: String, active: Boolean) {
    val pop = GrabThemeTokens.pop
    val bg by animateColorAsState(
        targetValue = if (active) pop.strong else pop.soft,
        animationSpec = tween(220),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) pop.onStrong else pop.text,
        animationSpec = tween(220),
        label = "chipFg",
    )
    Box(
        modifier = Modifier
            .clip(PopPillShape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = fg,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun FetchingSkeletonCard() {
    val pop = GrabThemeTokens.pop
    val pulse = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "skelAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.card)
            .padding(14.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(PopCardShape)
                .background(pop.track),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(PopPillShape)
                    .background(pop.track),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(12.dp)
                    .clip(PopPillShape)
                    .background(pop.track),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Reading the link…",
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
            )
        }
    }
}

@Composable
private fun ReadyMediaCard(info: MediaInfo, onOpen: () -> Unit) {
    val pop = GrabThemeTokens.pop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.soft)
            .clickable {
                GrabHaptics.light()
                onOpen()
            }
            .padding(14.dp)
            .testTag("home_ready_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopThumb(
            seed = info.url,
            url = info.thumbnail,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (info.isPlaylist) "Pick from ${info.entries.size} items" else "Choose format",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Open options",
            tint = pop.text,
        )
    }
}

@Composable
private fun FetchErrorCard(
    message: String,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    val pop = GrabThemeTokens.pop
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
private fun RecentJobRow(job: DownloadJobEntity, onClick: () -> Unit) {
    val pop = GrabThemeTokens.pop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(PopCardShape)
            .background(pop.card)
            .clickable {
                GrabHaptics.selection()
                onClick()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopThumb(
            seed = job.id,
            url = job.thumbnail,
            audio = job.downloadMode == DownloadMode.AUDIO_ONLY,
            subtitles = job.downloadMode == DownloadMode.SUBTITLES_ONLY,
            modifier = Modifier.size(92.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!job.site.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = job.site,
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            PopTag(job.formatBadge)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Open download details",
            tint = pop.text,
        )
    }
}

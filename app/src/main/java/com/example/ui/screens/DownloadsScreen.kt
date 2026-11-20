package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.GetiyHaptics
import com.example.data.db.DownloadJobEntity
import com.example.data.model.DownloadMode
import com.example.data.model.JobStatus
import com.example.data.model.MediaHelpers
import com.example.data.repository.DownloadRepository
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopProgressBar
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GetiyAzure
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.theme.PopError
import java.io.File
import kotlin.math.roundToInt

private enum class QueueFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    FINISHED("Finished"),
}

@Composable
fun DownloadsScreen(
    jobs: List<DownloadJobEntity>,
    speeds: Map<String, String>,
    extraBottomPadding: Int,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearFinished: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    var filter by remember { mutableStateOf(QueueFilter.ALL) }

    val activeTotal = jobs.count { it.jobStatus.isActive }
    val doneTotal = jobs.count { it.jobStatus == JobStatus.DONE }
    val failedTotal = jobs.count { it.jobStatus == JobStatus.FAILED || it.jobStatus == JobStatus.CANCELED }
    val hasFinished = doneTotal + failedTotal > 0

    val shownJobs = remember(jobs, filter) {
        when (filter) {
            QueueFilter.ALL -> jobs
            QueueFilter.ACTIVE -> jobs.filter { it.jobStatus.isActive }
            QueueFilter.FINISHED -> jobs.filter { !it.jobStatus.isActive }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = (36 + extraBottomPadding).dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 3-Column Telemetry Strip
        item(key = "downloads_summary_strip") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QueueMetricTile("Active", "$activeTotal", GetiyMint, Modifier.weight(1f))
                QueueMetricTile("Saved", "$doneTotal", GetiyAzure, Modifier.weight(1f))
                QueueMetricTile("Issues", "$failedTotal", PopError, Modifier.weight(1f))
            }
        }

        // Filter Chips + Clear Finished Pill
        item(key = "downloads_filter_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    containerColor = pop.card,
                    selectedContainerColor = pop.strong,
                    labelColor = pop.text,
                    selectedLabelColor = pop.onStrong,
                )
                QueueFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = {
                            GetiyHaptics.selection()
                            filter = f
                        },
                        label = { Text(f.label, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (filter == f) pop.strong else pop.track),
                        colors = chipColors,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (hasFinished) {
                    Surface(
                        onClick = {
                            GetiyHaptics.selection()
                            onClearFinished()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = pop.card,
                        border = BorderStroke(1.dp, pop.track),
                        modifier = Modifier.testTag("downloads_clear_button"),
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = pop.text,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        if (shownJobs.isEmpty()) {
            item(key = "empty_downloads") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp)
                        .clip(PopCardShape)
                        .background(pop.card)
                        .border(1.dp, pop.track, PopCardShape)
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(pop.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = pop.strong,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (jobs.isEmpty()) "Transfer queue is clear" else "Nothing in ${filter.label}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = pop.text,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Drop a link in the bottom Fetch bar on Home or share from any app.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                    )
                }
            }
        } else {
            items(shownJobs, key = { it.id }) { job ->
                TransferJobCard(
                    job = job,
                    speed = speeds[job.id],
                    onPause = { onPause(job.id) },
                    onResume = { onResume(job.id) },
                    onCancel = { onCancel(job.id) },
                    onRetry = { onRetry(job.id) },
                    onRemove = { onRemove(job.id) },
                    onOpenDetail = { onOpenDetail(job.id) },
                )
            }
        }
    }
}

@Composable
private fun QueueMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(pop.card)
            .border(1.dp, pop.track, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = pop.muted,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
    }
}

@Composable
private fun TransferJobCard(
    job: DownloadJobEntity,
    speed: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    val status = job.jobStatus
    val active = status.isActive
    val pct = job.progress.roundToInt().coerceIn(0, 100)
    val files = job.pathList

    val statusText = when (status) {
        JobStatus.QUEUED -> "Queued · waiting for engine slot…"
        JobStatus.RUNNING -> if (job.progress >= 100.0) {
            "Muxing & finalizing with FFmpeg…"
        } else {
            listOfNotNull(
                speed,
                job.eta?.takeIf { it > 0 }?.let { "${MediaHelpers.etaLabel(it)} left" },
            ).ifEmpty { listOf("Connecting with yt-dlp…") }.joinToString(" · ")
        }
        JobStatus.DONE -> if (files.isEmpty()) "Saved" else File(files.first()).name
        JobStatus.FAILED -> job.error ?: "Failed"
        JobStatus.CANCELED -> "Canceled"
        JobStatus.PAUSED -> if (job.progress > 0.0) "Paused at $pct%" else "Paused"
        JobStatus.WAITING -> "Reconnecting (${job.attempts}/${DownloadRepository.MAX_AUTO_RETRIES})"
    }

    val cardBg = if (status == JobStatus.RUNNING) pop.soft else pop.card
    val cardBorder = if (status == JobStatus.RUNNING) GetiyMint.copy(alpha = 0.55f) else pop.track

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, PopCardShape)
            .then(
                if (!active) {
                    Modifier.clickable {
                        GetiyHaptics.selection()
                        onOpenDetail()
                    }
                } else {
                    Modifier
                },
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopThumb(
                seed = job.id,
                url = job.thumbnail,
                audio = job.downloadMode == DownloadMode.AUDIO_ONLY,
                subtitles = job.downloadMode == DownloadMode.SUBTITLES_ONLY,
                modifier = Modifier.size(68.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = pop.text,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PopTag(
                        label = job.formatBadge,
                        background = if (status == JobStatus.RUNNING) pop.bg else pop.soft,
                    )
                    if (!job.site.isNullOrBlank()) {
                        Text(
                            text = job.site,
                            style = MaterialTheme.typography.labelSmall.copy(color = pop.muted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (status == JobStatus.RUNNING && job.progress > 0.0) {
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = pop.strong,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            AnimatedContent(targetState = status, label = "jobStatusBtn") { st ->
                when (st) {
                    JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.WAITING -> {
                        PopIconButton(
                            icon = Icons.Rounded.Pause,
                            contentDescription = "Pause download",
                            background = pop.bg,
                            size = 44.dp,
                            onClick = onPause,
                        )
                    }
                    JobStatus.PAUSED -> {
                        PopIconButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = "Resume download",
                            background = pop.strong,
                            foreground = pop.onStrong,
                            size = 44.dp,
                            onClick = onResume,
                        )
                    }
                    JobStatus.DONE -> {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(pop.strong),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Done",
                                tint = pop.onStrong,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    JobStatus.FAILED, JobStatus.CANCELED -> {
                        PopIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Try again",
                            background = pop.bg,
                            size = 44.dp,
                            onClick = onRetry,
                        )
                    }
                }
            }
        }

        if (active) {
            Spacer(Modifier.height(12.dp))
            val indeterminate = status == JobStatus.QUEUED ||
                status == JobStatus.WAITING ||
                (status == JobStatus.RUNNING && (job.progress <= 0.0 || job.progress >= 100.0))
            PopProgressBar(
                progress = if (indeterminate) null else (job.progress / 100.0).toFloat(),
                trackColor = if (status == JobStatus.RUNNING) pop.trackOnSoft else pop.track,
                height = 7.dp,
                modifier = Modifier.testTag("job_progress_indicator_${job.id}"),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (status == JobStatus.FAILED) PopError else pop.muted,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (active && status != JobStatus.RUNNING) {
                MiniActionPill(
                    icon = Icons.Rounded.Close,
                    label = "Cancel",
                    onClick = onCancel,
                )
            }
            if (!active) {
                MiniActionPill(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove",
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun MiniActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(pop.field)
            .border(1.dp, pop.track, RoundedCornerShape(10.dp))
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = pop.text, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

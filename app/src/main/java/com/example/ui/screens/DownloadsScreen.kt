package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.GrabHaptics
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
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopError
import java.io.File
import kotlin.math.roundToInt

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
    val pop = GrabThemeTokens.pop
    val hasFinished = jobs.any { !it.jobStatus.isActive }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 18.dp,
            bottom = (116 + extraBottomPadding).dp,
        ),
    ) {
        item(key = "downloads_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = pop.text,
                    ),
                )
                Spacer(Modifier.weight(1f))
                if (hasFinished) {
                    TextButton(
                        onClick = {
                            GrabHaptics.selection()
                            onClearFinished()
                        },
                        modifier = Modifier.testTag("downloads_clear_button"),
                    ) {
                        Text(
                            text = "Clear finished",
                            fontWeight = FontWeight.Bold,
                            color = pop.text,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (jobs.isEmpty()) {
            item(key = "empty_downloads") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(pop.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = pop.text,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No downloads yet",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = pop.text,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Paste a link on Home or share from any app.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                    )
                }
            }
        } else {
            items(jobs, key = { it.id }) { job ->
                DownloadJobCard(
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
private fun DownloadJobCard(
    job: DownloadJobEntity,
    speed: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val pop = GrabThemeTokens.pop
    val status = job.jobStatus
    val active = status.isActive
    val pct = job.progress.roundToInt().coerceIn(0, 100)
    val files = job.pathList

    val statusText = when (status) {
        JobStatus.QUEUED -> "Waiting"
        JobStatus.RUNNING -> if (job.progress >= 100.0) {
            "Finishing…"
        } else {
            listOfNotNull(
                if (job.progress <= 0.0) "Starting…" else "$pct%",
                speed,
                job.eta?.takeIf { it > 0 }?.let { "${MediaHelpers.etaLabel(it)} left" },
            ).joinToString(" · ")
        }
        JobStatus.DONE -> if (files.isEmpty()) "Saved" else files.joinToString("\n") { File(it).name }
        JobStatus.FAILED -> job.error ?: "Failed"
        JobStatus.CANCELED -> "Canceled"
        JobStatus.PAUSED -> if (job.progress > 0.0) "Paused at $pct%" else "Paused"
        JobStatus.WAITING -> "Connection lost · trying again by itself (${job.attempts} of ${DownloadRepository.MAX_AUTO_RETRIES})"
    }

    val cardBg = if (status == JobStatus.RUNNING) pop.soft else pop.card

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(PopCardShape)
            .background(cardBg)
            .then(
                if (!active) {
                    Modifier.clickable {
                        GrabHaptics.selection()
                        onOpenDetail()
                    }
                } else {
                    Modifier
                },
            )
            .padding(start = 12.dp, top = 12.dp, end = 10.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopThumb(
                seed = job.id,
                url = job.thumbnail,
                audio = job.downloadMode == DownloadMode.AUDIO_ONLY,
                subtitles = job.downloadMode == DownloadMode.SUBTITLES_ONLY,
                modifier = Modifier.size(92.dp),
            )
            Spacer(Modifier.width(14.dp))
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
                PopTag(
                    label = job.formatBadge,
                    background = if (status == JobStatus.RUNNING) pop.bg else pop.soft,
                )
            }
            Spacer(Modifier.width(6.dp))
            AnimatedContent(targetState = status, label = "jobStatusBtn") { st ->
                when (st) {
                    JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.WAITING -> {
                        PopIconButton(
                            icon = Icons.Rounded.Pause,
                            contentDescription = "Pause download",
                            background = pop.bg,
                            onClick = onPause,
                        )
                    }
                    JobStatus.PAUSED -> {
                        PopIconButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = "Resume download",
                            background = pop.strong,
                            foreground = pop.onStrong,
                            onClick = onResume,
                        )
                    }
                    JobStatus.DONE -> {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(pop.strong),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Done",
                                tint = pop.onStrong,
                            )
                        }
                    }
                    JobStatus.FAILED, JobStatus.CANCELED -> {
                        PopIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Try again",
                            background = pop.bg,
                            onClick = onRetry,
                        )
                    }
                }
            }
        }

        if (active) {
            Spacer(Modifier.height(14.dp))
            PopProgressBar(
                progress = if (status == JobStatus.QUEUED && job.progress <= 0.0) null else (job.progress / 100.0).toFloat(),
                trackColor = if (status == JobStatus.RUNNING) pop.trackOnSoft else pop.track,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (status == JobStatus.FAILED) PopError else pop.muted,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (active && status != JobStatus.RUNNING) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = pop.text,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.clickable {
                        GrabHaptics.selection()
                        onCancel()
                    },
                )
            }
            if (!active) {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = pop.text,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.clickable {
                        GrabHaptics.selection()
                        onRemove()
                    },
                )
            }
        }
    }
}

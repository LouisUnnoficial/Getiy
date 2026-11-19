package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.FileShareHelper
import com.example.core.GrabHaptics
import com.example.data.db.DownloadJobEntity
import com.example.data.model.LibraryItem
import com.example.data.model.LibraryKind
import com.example.data.model.MediaHelpers
import com.example.data.repository.ExtractionProgress
import com.example.data.settings.AppSettings
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopProgressBar
import com.example.ui.components.PopSection
import com.example.ui.components.PopThumb
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopError
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopYellow
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun DownloadDetailScreen(
    job: DownloadJobEntity?,
    extraction: ExtractionProgress?,
    onBack: () -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onDownloadAgain: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onExtractAudio: (DownloadJobEntity, String, String) -> Unit,
    onPlayVideo: (LibraryItem) -> Unit,
    onPlayAudio: (LibraryItem) -> Unit,
    onMakeRingtone: (LibraryItem) -> Unit,
    onDeleteFile: (String, String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val pop = GrabThemeTokens.pop
    val context = LocalContext.current

    if (job == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pop.bg),
            contentAlignment = Alignment.Center,
        ) {
            Text("This download was removed.", color = pop.muted)
        }
        return
    }

    val existingFiles = remember(job.paths) {
        job.pathList.filter { File(it).exists() }
    }
    val videos = remember(existingFiles) {
        existingFiles.filter { MediaHelpers.kindOf(it) == LibraryKind.VIDEO }
    }
    val previewPath = remember(existingFiles) {
        existingFiles.firstOrNull { MediaHelpers.kindOf(it) == LibraryKind.VIDEO }
            ?: existingFiles.firstOrNull { MediaHelpers.kindOf(it) == LibraryKind.AUDIO }
    }

    var filePendingDelete by remember { mutableStateOf<String?>(null) }

    if (filePendingDelete != null) {
        val target = filePendingDelete!!
        AlertDialog(
            onDismissRequest = { filePendingDelete = null },
            title = { Text("Delete file?") },
            text = { Text("${File(target).name} will be permanently removed from your phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        filePendingDelete = null
                        onDeleteFile(job.id, target)
                    },
                ) {
                    Text("Delete", color = PopError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { filePendingDelete = null }) {
                    Text("Keep")
                }
            },
        )
    }

    fun asLibraryItem(path: String): LibraryItem = LibraryItem(
        jobId = job.id,
        path = path,
        title = job.title,
        site = job.site,
        thumbnail = job.thumbnail,
        kind = MediaHelpers.kindOf(path),
        finishedAt = job.finishedAt ?: job.createdAt,
        sizeBytes = runCatching { File(path).length() }.getOrDefault(0L),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 48.dp),
    ) {
        item(key = "detail_topbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PopIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    tag = "detail_back_button",
                )
                Spacer(Modifier.weight(1f))
                PopIconButton(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = "Remove from history",
                    onClick = {
                        onRemoveFromHistory(job.id)
                        onBack()
                    },
                    tag = "detail_remove_history_button",
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "detail_preview") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(PopCardShape)
                    .clickable(enabled = previewPath != null) {
                        previewPath?.let { path ->
                            val item = asLibraryItem(path)
                            if (item.kind == LibraryKind.AUDIO) onPlayAudio(item) else onPlayVideo(item)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                PopThumb(
                    seed = job.id,
                    url = job.thumbnail,
                    audio = previewPath != null && MediaHelpers.kindOf(previewPath) == LibraryKind.AUDIO,
                    modifier = Modifier.fillMaxSize(),
                    shape = PopCardShape,
                )
                if (previewPath != null) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(PopInk.copy(alpha = 0.78f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play media",
                            tint = PopYellow,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "detail_title") {
            Text(
                text = job.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = pop.text,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Spacer(Modifier.height(6.dp))
            val dateStr = job.finishedAt?.let {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
            }
            val meta = listOfNotNull(job.site, job.downloadMode.label, dateStr).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
            )
            Spacer(Modifier.height(18.dp))
        }

        item(key = "detail_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PopButton(
                    label = "Download again",
                    icon = Icons.Rounded.Refresh,
                    height = 50.dp,
                    onClick = { onDownloadAgain(job.url) },
                    modifier = Modifier.weight(1f),
                    tag = "detail_download_again_button",
                )
                PopIconButton(
                    icon = Icons.Rounded.IosShare,
                    contentDescription = "Share all files",
                    onClick = if (existingFiles.isEmpty()) null else {
                        { FileShareHelper.shareFiles(context, existingFiles, job.title) }
                    },
                    tag = "detail_share_all_button",
                )
                PopIconButton(
                    icon = Icons.Rounded.Link,
                    contentDescription = "Copy link",
                    onClick = {
                        FileShareHelper.copyToClipboard(context, "Video link", job.url)
                        onCopyLink(job.url)
                    },
                    tag = "detail_copy_link_button",
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (videos.isNotEmpty()) {
            item(key = "detail_extract_audio") {
                PopSection("Extract audio")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PopCardShape)
                        .background(pop.card)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Pull an audio file straight from this video without downloading again.",
                        style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (extraction?.running == true) {
                        PopProgressBar(
                            progress = (extraction.progress / 100.0).toFloat().takeIf { it > 0f },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Extracting ${extraction.format.uppercase()} · ${extraction.progress.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(color = pop.text),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppSettings.audioFormats.forEach { fmt ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        GrabHaptics.light()
                                        onExtractAudio(job, videos.first(), fmt)
                                    },
                                    label = { Text(fmt.uppercase(), fontWeight = FontWeight.Bold) },
                                    shape = PopPillShape,
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = pop.soft,
                                        labelColor = pop.text,
                                    ),
                                )
                            }
                        }
                        if (!extraction?.error.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = extraction!!.error!!,
                                style = MaterialTheme.typography.bodySmall.copy(color = PopError),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item(key = "detail_files_header") {
            PopSection("Files (${existingFiles.size})")
        }

        if (existingFiles.isEmpty()) {
            item(key = "detail_no_files") {
                Text(
                    text = "No files found on disk for this download.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                )
            }
        } else {
            items(existingFiles, key = { it }) { path ->
                val f = File(path)
                val kind = MediaHelpers.kindOf(path)
                val libItem = asLibraryItem(path)
                val icon = when (kind) {
                    LibraryKind.VIDEO -> Icons.Rounded.Movie
                    LibraryKind.AUDIO -> Icons.Rounded.MusicNote
                    LibraryKind.SUBTITLES -> Icons.Rounded.Subtitles
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(PopCardShape)
                        .background(pop.card)
                        .clickable {
                            when (kind) {
                                LibraryKind.VIDEO -> onPlayVideo(libItem)
                                LibraryKind.AUDIO -> onPlayAudio(libItem)
                                LibraryKind.SUBTITLES -> FileShareHelper.shareFiles(context, listOf(path), job.title)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(pop.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = pop.text, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = f.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = pop.text,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MediaHelpers.formatBytes(libItem.sizeBytes)?.let { sz ->
                            Text(
                                text = "${kind.label} · $sz",
                                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                            )
                        }
                    }
                    if (kind != LibraryKind.SUBTITLES) {
                        IconButton(onClick = { onMakeRingtone(libItem) }) {
                            Icon(
                                imageVector = Icons.Rounded.RingVolume,
                                contentDescription = "Make ringtone",
                                tint = pop.text,
                            )
                        }
                    }
                    IconButton(onClick = { FileShareHelper.shareFiles(context, listOf(path), job.title) }) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = "Share file",
                            tint = pop.text,
                        )
                    }
                    IconButton(onClick = { filePendingDelete = path }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete file",
                            tint = PopError,
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.PopProgressBar
import com.example.ui.components.PopSection
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyIce
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyNavyDeep
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopError
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
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        // Full-bleed edge-to-edge hero media preview with floating glass controls
        item(key = "detail_hero_banner") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10.5f)
                    .clickable(enabled = previewPath != null) {
                        previewPath?.let { path ->
                            val item = asLibraryItem(path)
                            if (item.kind == LibraryKind.AUDIO) onPlayAudio(item) else onPlayVideo(item)
                        }
                    },
            ) {
                PopThumb(
                    seed = job.id,
                    url = job.thumbnail,
                    audio = previewPath != null && MediaHelpers.kindOf(previewPath) == LibraryKind.AUDIO,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to GetiyNavy.copy(alpha = 0.65f),
                                0.45f to Color.Transparent,
                                1.0f to pop.bg,
                            ),
                        ),
                )
                // Floating top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PopIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        background = GetiyNavy.copy(alpha = 0.72f),
                        foreground = GetiyIce,
                        onClick = onBack,
                        tag = "detail_back_button",
                    )
                    Spacer(Modifier.weight(1f))
                    PopIconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = "Remove from history",
                        background = GetiyNavy.copy(alpha = 0.72f),
                        foreground = GetiyIce,
                        onClick = {
                            onRemoveFromHistory(job.id)
                            onBack()
                        },
                        tag = "detail_remove_history_button",
                    )
                }

                if (previewPath != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(GetiyAccentBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play media",
                            tint = GetiyNavyDeep,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
        }

        // Overlapping Info & Actions Studio Card
        item(key = "detail_info_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .padding(horizontal = 20.dp)
                    .clip(PopCardShape)
                    .background(pop.card)
                    .border(1.dp, pop.track, PopCardShape)
                    .padding(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PopTag(job.formatBadge)
                    if (!job.site.isNullOrBlank()) {
                        Text(
                            text = job.site,
                            style = MaterialTheme.typography.labelMedium.copy(color = pop.muted, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = pop.text,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                val dateStr = job.finishedAt?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                }
                val meta = listOfNotNull(job.downloadMode.label, dateStr).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PopButton(
                        label = "Redownload",
                        icon = Icons.Rounded.Refresh,
                        useGradient = true,
                        height = 48.dp,
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
            }
        }

        if (videos.isNotEmpty()) {
            item(key = "detail_extract_audio") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PopSection("Extract Audio Track")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PopCardShape)
                            .background(pop.card)
                            .border(1.dp, pop.track, PopCardShape)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Convert this video locally via FFmpeg without redownloading.",
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
                                style = MaterialTheme.typography.labelMedium.copy(color = pop.strong, fontWeight = FontWeight.Bold),
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
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, pop.track),
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
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        item(key = "detail_files_header") {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                PopSection("Saved Files (${existingFiles.size})")
            }
        }

        if (existingFiles.isEmpty()) {
            item(key = "detail_no_files") {
                Text(
                    text = "No files found on disk for this download.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                    modifier = Modifier.padding(horizontal = 20.dp),
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
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .clip(PopCardShape)
                        .background(pop.card)
                        .border(1.dp, pop.track, PopCardShape)
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(pop.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = pop.strong, modifier = Modifier.size(20.dp))
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

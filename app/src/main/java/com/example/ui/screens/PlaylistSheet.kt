package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.GrabHaptics
import com.example.data.model.DownloadMode
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.data.settings.AppSettings
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSegmented
import com.example.ui.components.PopThumb
import com.example.ui.theme.GrabThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    info: MediaInfo,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onDownloadSelected: (Set<Int>, DownloadMode, Int?) -> Unit,
) {
    val pop = GrabThemeTokens.pop
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val modes = listOf(DownloadMode.VIDEO, DownloadMode.VIDEO_AND_AUDIO, DownloadMode.AUDIO_ONLY)
    val heights: List<Int?> = listOf(null, 1080, 720, 480)

    var selected by remember(info.url) { mutableStateOf(info.entries.indices.toSet()) }
    var mode by remember(info.url) {
        mutableStateOf(
            if (settings.defaultMode == DownloadMode.SUBTITLES_ONLY) DownloadMode.VIDEO else settings.defaultMode,
        )
    }
    var maxHeight by remember(info.url) { mutableStateOf(settings.maxHeight) }

    val allSelected = selected.size == info.entries.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pop.bg,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = pop.text,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(info.site, "${info.entries.size} items").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
            )
            Spacer(Modifier.height(14.dp))

            PopSegmented(
                values = modes,
                labels = modes.map { it.shortLabel },
                selected = mode,
                onSelected = { mode = it },
            )

            if (mode != DownloadMode.AUDIO_ONLY) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    heights.forEach { h ->
                        val active = maxHeight == h
                        FilterChip(
                            selected = active,
                            onClick = {
                                GrabHaptics.selection()
                                maxHeight = h
                            },
                            label = { Text(if (h == null) "Best" else "${h}p") },
                            shape = PopPillShape,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = pop.soft,
                                selectedContainerColor = pop.strong,
                                labelColor = pop.text,
                                selectedLabelColor = pop.onStrong,
                            ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selected.size} of ${info.entries.size} selected",
                    style = MaterialTheme.typography.labelLarge.copy(color = pop.muted),
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        GrabHaptics.selection()
                        selected = if (allSelected) emptySet() else info.entries.indices.toSet()
                    },
                ) {
                    Text(
                        text = if (allSelected) "Deselect all" else "Select all",
                        fontWeight = FontWeight.Bold,
                        color = pop.text,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(info.entries, key = { i, e -> "${e.id ?: e.url}-$i" }) { index, entry ->
                    val isPicked = index in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PopCardShape)
                            .background(if (isPicked) pop.soft else pop.card)
                            .clickable {
                                GrabHaptics.selection()
                                selected = if (isPicked) selected - index else selected + index
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PopThumb(
                            seed = entry.url,
                            url = entry.thumbnail,
                            modifier = Modifier.size(60.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = pop.text,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            entry.duration?.let { d ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = MediaHelpers.clockLabel(d),
                                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isPicked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isPicked) "Selected" else "Not selected",
                            tint = pop.text,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            PopButton(
                label = if (selected.size == 1) "Download 1 item" else "Download ${selected.size} items",
                icon = Icons.Rounded.Download,
                useGradient = true,
                enabled = selected.isNotEmpty(),
                onClick = { onDownloadSelected(selected, mode, maxHeight) },
                modifier = Modifier.padding(bottom = 24.dp),
                tag = "playlist_download_button",
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.FileShareHelper
import com.example.core.GrabHaptics
import com.example.data.model.LibraryItem
import com.example.data.model.LibraryKind
import com.example.data.model.LibrarySort
import com.example.data.model.MediaHelpers
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSection
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopError
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopYellow
import java.io.File

@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    sort: LibrarySort,
    groupBySite: Boolean,
    extraBottomPadding: Int,
    onSortChange: (LibrarySort) -> Unit,
    onGroupChange: (Boolean) -> Unit,
    onPlayVideo: (LibraryItem) -> Unit,
    onPlayAudio: (LibraryItem) -> Unit,
    onOpenDetail: (String) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
) {
    val pop = GrabThemeTokens.pop
    val context = LocalContext.current

    var selectedKind by remember { mutableStateOf<LibraryKind?>(null) }
    var query by remember { mutableStateOf("") }
    var pickedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val selecting = pickedPaths.isNotEmpty()

    val filtered = remember(items, selectedKind, query, sort) {
        val base = items.filter { item ->
            val kindOk = selectedKind == null || item.kind == selectedKind
            val q = query.trim()
            val qOk = q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.site.orEmpty().contains(q, ignoreCase = true) ||
                File(item.path).name.contains(q, ignoreCase = true)
            kindOk && qOk
        }
        when (sort) {
            LibrarySort.NEWEST -> base.sortedByDescending { it.finishedAt }
            LibrarySort.OLDEST -> base.sortedBy { it.finishedAt }
            LibrarySort.LARGEST -> base.sortedByDescending { it.sizeBytes }
            LibrarySort.BY_SITE -> base.sortedWith(
                compareBy<LibraryItem> { (it.site ?: "Other").lowercase() }
                    .thenByDescending { it.finishedAt },
            )
        }
    }

    val grouped = remember(filtered, groupBySite) {
        if (!groupBySite) {
            listOf("" to filtered)
        } else {
            filtered.groupBy { it.site?.ifBlank { "Other" } ?: "Other" }
                .entries
                .sortedBy { it.key.lowercase() }
                .map { it.key to it.value }
        }
    }

    fun toggleSelect(path: String) {
        GrabHaptics.selection()
        pickedPaths = if (path in pickedPaths) pickedPaths - path else pickedPaths + path
    }

    if (confirmDelete) {
        val n = pickedPaths.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (n == 1) "Delete 1 file?" else "Delete $n files?") },
            text = { Text("They will be removed from your phone. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = pickedPaths
                        pickedPaths = emptySet()
                        confirmDelete = false
                        onDeleteSelected(toDelete)
                    },
                ) {
                    Text("Delete", color = PopError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep")
                }
            },
        )
    }

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
        item(key = "library_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    PopIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cancel selection",
                        onClick = { pickedPaths = emptySet() },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${pickedPaths.size} selected",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = pop.text,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    PopIconButton(
                        icon = Icons.Rounded.IosShare,
                        contentDescription = "Share selected files",
                        onClick = {
                            FileShareHelper.shareFiles(context, pickedPaths.toList())
                        },
                        tag = "library_share_selected",
                    )
                    Spacer(Modifier.width(8.dp))
                    PopIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Delete selected files",
                        background = pop.errorSoft,
                        foreground = PopError,
                        onClick = { confirmDelete = true },
                        tag = "library_delete_selected",
                    )
                } else {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = pop.text,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Box {
                        PopIconButton(
                            icon = Icons.Rounded.Sort,
                            contentDescription = "Sort library",
                            onClick = { showSortMenu = true },
                            tag = "library_sort_button",
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            LibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (option == sort) "${option.label} ✓" else option.label,
                                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        GrabHaptics.selection()
                                        onSortChange(option)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (items.isNotEmpty()) {
            item(key = "library_search_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PopPillShape)
                        .background(pop.field)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = pop.muted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search saved files",
                                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = pop.text),
                            cursorBrush = SolidColor(pop.strong),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input"),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = pop.muted)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = pop.soft,
                        selectedContainerColor = pop.strong,
                        labelColor = pop.text,
                        selectedLabelColor = pop.onStrong,
                    )
                    FilterChip(
                        selected = selectedKind == null,
                        onClick = {
                            GrabHaptics.selection()
                            selectedKind = null
                        },
                        label = { Text("All") },
                        shape = PopPillShape,
                        border = null,
                        colors = chipColors,
                    )
                    LibraryKind.entries.forEach { k ->
                        FilterChip(
                            selected = selectedKind == k,
                            onClick = {
                                GrabHaptics.selection()
                                selectedKind = if (selectedKind == k) null else k
                            },
                            label = { Text(k.label) },
                            shape = PopPillShape,
                            border = null,
                            colors = chipColors,
                        )
                    }
                    FilterChip(
                        selected = groupBySite,
                        onClick = {
                            GrabHaptics.selection()
                            onGroupChange(!groupBySite)
                        },
                        label = { Text("Group by site") },
                        shape = PopPillShape,
                        border = null,
                        colors = chipColors,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (filtered.isEmpty()) {
            item(key = "library_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 72.dp),
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
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = pop.text,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (items.isEmpty()) "Your library is empty" else "No matching files",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = pop.text,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (items.isEmpty()) {
                            "Saved videos, audio tracks and subtitles appear here."
                        } else {
                            "Try clearing your search or filter."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                    )
                }
            }
        } else {
            grouped.forEach { (siteTitle, groupItems) ->
                if (siteTitle.isNotEmpty()) {
                    item(key = "section_$siteTitle") {
                        PopSection(siteTitle)
                    }
                }
                val rows = groupItems.chunked(2)
                items(rows, key = { pair -> pair.joinToString("|") { it.path } }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        pair.forEach { item ->
                            LibraryGridTile(
                                item = item,
                                selecting = selecting,
                                selected = item.path in pickedPaths,
                                onClick = {
                                    if (selecting) {
                                        toggleSelect(item.path)
                                    } else {
                                        when (item.kind) {
                                            LibraryKind.VIDEO -> onPlayVideo(item)
                                            LibraryKind.AUDIO -> onPlayAudio(item)
                                            LibraryKind.SUBTITLES -> onOpenDetail(item.jobId)
                                        }
                                    }
                                },
                                onLongClick = { toggleSelect(item.path) },
                                onOpenDetail = { onOpenDetail(item.jobId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridTile(
    item: LibraryItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GrabThemeTokens.pop
    val ext = File(item.path).extension.uppercase()

    Column(
        modifier = modifier
            .clip(PopCardShape)
            .background(if (selected) pop.soft else pop.card)
            .border(
                BorderStroke(if (selected) 2.dp else 0.dp, if (selected) pop.text else Color.Transparent),
                PopCardShape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            PopThumb(
                seed = item.path,
                url = item.thumbnail,
                audio = item.kind == LibraryKind.AUDIO,
                subtitles = item.kind == LibraryKind.SUBTITLES,
                modifier = Modifier.fillMaxSize(),
            )
            PopTag(
                label = ext.ifEmpty { item.kind.label },
                background = PopInk.copy(alpha = 0.82f),
                contentColor = PopYellow,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            if (selecting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (selected) PopYellow else PopInk.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = PopInk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = {
                        GrabHaptics.selection()
                        onOpenDetail()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(36.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PopInk.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Download details",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        val sub = listOfNotNull(
            item.site,
            MediaHelpers.formatBytes(item.sizeBytes),
        ).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

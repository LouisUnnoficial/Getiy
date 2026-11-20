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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewAgenda
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
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.PopSection
import com.example.ui.components.PopTag
import com.example.ui.components.PopThumb
import com.example.ui.theme.GetiyIce
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyNavyDeep
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopError
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
    var listMode by remember { mutableStateOf(false) }

    val selecting = pickedPaths.isNotEmpty()
    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }

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
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = (36 + extraBottomPadding).dp,
        ),
    ) {
        // Vault Control Bar: Storage Stats + Layout Switch + Sort
        item(key = "library_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(pop.card)
                    .border(1.dp, pop.track, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    PopIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cancel selection",
                        size = 42.dp,
                        onClick = { pickedPaths = emptySet() },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${pickedPaths.size} selected",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = pop.text,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    PopIconButton(
                        icon = Icons.Rounded.IosShare,
                        contentDescription = "Share selected files",
                        size = 42.dp,
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
                        size = 42.dp,
                        onClick = { confirmDelete = true },
                        tag = "library_delete_selected",
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Media Vault",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = pop.text,
                            ),
                        )
                        Text(
                            text = "${items.size} ${if (items.size == 1) "file" else "files"} · ${MediaHelpers.formatBytes(totalBytes) ?: "0 KB"}",
                            style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                        )
                    }
                    PopIconButton(
                        icon = if (listMode) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda,
                        contentDescription = if (listMode) "Switch to grid view" else "Switch to list view",
                        size = 42.dp,
                        onClick = { listMode = !listMode },
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        PopIconButton(
                            icon = Icons.Rounded.Sort,
                            contentDescription = "Sort library",
                            size = 42.dp,
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
            Spacer(Modifier.height(12.dp))
        }

        if (items.isNotEmpty()) {
            item(key = "library_search_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(pop.field)
                        .border(1.dp, pop.track, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = pop.muted,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Filter vault by title or site…",
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

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = pop.card,
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
                        label = { Text("All", fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selectedKind == null) pop.strong else pop.track),
                        colors = chipColors,
                    )
                    LibraryKind.entries.forEach { k ->
                        FilterChip(
                            selected = selectedKind == k,
                            onClick = {
                                GrabHaptics.selection()
                                selectedKind = if (selectedKind == k) null else k
                            },
                            label = { Text(k.label, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selectedKind == k) pop.strong else pop.track),
                            colors = chipColors,
                        )
                    }
                    FilterChip(
                        selected = groupBySite,
                        onClick = {
                            GrabHaptics.selection()
                            onGroupChange(!groupBySite)
                        },
                        label = { Text("By site", fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (groupBySite) pop.strong else pop.track),
                        colors = chipColors,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (filtered.isEmpty()) {
            item(key = "library_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
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
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = pop.strong,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (items.isEmpty()) "Your vault is empty" else "No matching files",
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
                            "Try clearing your search or category filter."
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
                if (listMode) {
                    items(groupItems, key = { it.path }) { item ->
                        VaultListRow(
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
                            onShare = { FileShareHelper.shareFiles(context, listOf(item.path), item.title) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    val rows = groupItems.chunked(2)
                    items(rows, key = { pair -> pair.joinToString("|") { it.path } }) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            pair.forEach { item ->
                                VaultPosterCard(
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultPosterCard(
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

    Box(
        modifier = modifier
            .aspectRatio(0.84f)
            .clip(PopCardShape)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) pop.strong else pop.track),
                PopCardShape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        PopThumb(
            seed = item.path,
            url = item.thumbnail,
            audio = item.kind == LibraryKind.AUDIO,
            subtitles = item.kind == LibraryKind.SUBTITLES,
            modifier = Modifier.fillMaxSize(),
            shape = PopCardShape,
        )

        // Deep midnight navy gradient scrim for crisp title overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to GetiyNavy.copy(alpha = 0.25f),
                        1.0f to GetiyNavy.copy(alpha = 0.94f),
                    ),
                ),
        )

        // Top-left format pill
        PopTag(
            label = ext.ifEmpty { item.kind.label },
            background = GetiyNavy.copy(alpha = 0.85f),
            contentColor = GetiyMint,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        )

        // Top-right info / selection badge
        if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) GetiyMint else GetiyNavy.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = GetiyNavyDeep,
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
                    .size(38.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GetiyNavy.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Download details",
                        tint = GetiyIce,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Bottom title & size overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = GetiyIce,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val sub = listOfNotNull(
                item.site,
                MediaHelpers.formatBytes(item.sizeBytes),
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall.copy(color = GetiyIce.copy(alpha = 0.72f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultListRow(
    item: LibraryItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenDetail: () -> Unit,
    onShare: () -> Unit,
) {
    val pop = GrabThemeTokens.pop
    val ext = File(item.path).extension.uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(if (selected) pop.soft else pop.card)
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) pop.strong else pop.track),
                PopCardShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopThumb(
            seed = item.path,
            url = item.thumbnail,
            audio = item.kind == LibraryKind.AUDIO,
            subtitles = item.kind == LibraryKind.SUBTITLES,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PopTag(label = ext.ifEmpty { item.kind.label })
                val sub = listOfNotNull(item.site, MediaHelpers.formatBytes(item.sizeBytes)).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall.copy(color = pop.muted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (selecting) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) pop.strong else pop.muted,
                modifier = Modifier.padding(end = 6.dp),
            )
        } else {
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.IosShare, contentDescription = "Share file", tint = pop.muted)
            }
            IconButton(onClick = onOpenDetail) {
                Icon(Icons.Rounded.Info, contentDescription = "Details", tint = pop.muted)
            }
        }
    }
}

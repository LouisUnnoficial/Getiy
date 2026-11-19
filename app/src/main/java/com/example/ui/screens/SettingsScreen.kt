package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.GrabHaptics
import com.example.data.model.AppThemeMode
import com.example.data.model.DownloadMode
import com.example.data.model.MediaHelpers
import com.example.data.settings.AppSettings
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopOptionTile
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSection
import com.example.ui.theme.GrabThemeTokens

private enum class SettingsPicker {
    DEFAULT_MODE,
    DEFAULT_QUALITY,
    VIDEO_CONTAINER,
    AUDIO_FORMAT,
    CONCURRENCY,
    THEME,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    engineVersion: String?,
    storageBytes: Long,
    failedCount: Int,
    extraBottomPadding: Int,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onClearFailed: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val pop = GrabThemeTokens.pop
    var activePicker by remember { mutableStateOf<SettingsPicker?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    if (showFolderDialog) {
        var base by remember { mutableStateOf(settings.saveBase) }
        var folder by remember { mutableStateOf(settings.saveFolder) }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Save folder", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettings.saveBases.forEach { (key, label) ->
                            FilterChip(
                                selected = base == key,
                                onClick = {
                                    GrabHaptics.selection()
                                    base = key
                                },
                                label = { Text(label) },
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
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateSettings { it.copy(saveBase = base, saveFolder = folder.ifBlank { "Grab" }) }
                        showFolderDialog = false
                    },
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showTemplateDialog) {
        var tpl by remember { mutableStateOf(settings.fileTemplate) }
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("File names", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tpl,
                        onValueChange = { tpl = it },
                        label = { Text("Template") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MediaHelpers.fileNameTokens.keys.forEach { token ->
                            FilterChip(
                                selected = tpl.contains(token),
                                onClick = {
                                    GrabHaptics.selection()
                                    tpl = if (tpl.isBlank()) token else "$tpl - $token"
                                },
                                label = { Text(token) },
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
                    Text(
                        text = "Preview: ${MediaHelpers.previewFileName(tpl, settings.container)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateSettings { it.copy(fileTemplate = tpl) }
                        showTemplateDialog = false
                    },
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) { Text("Cancel") }
            },
        )
    }

    activePicker?.let { picker ->
        ModalBottomSheet(
            onDismissRequest = { activePicker = null },
            containerColor = pop.bg,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                when (picker) {
                    SettingsPicker.DEFAULT_MODE -> {
                        PickerHeader("Default mode")
                        listOf(DownloadMode.VIDEO, DownloadMode.VIDEO_AND_AUDIO, DownloadMode.AUDIO_ONLY).forEach { m ->
                            PopOptionTile(
                                label = m.label,
                                sizeLabel = null,
                                selected = settings.defaultMode == m,
                                onClick = {
                                    onUpdateSettings { it.copy(defaultMode = m) }
                                    activePicker = null
                                },
                            )
                        }
                    }
                    SettingsPicker.DEFAULT_QUALITY -> {
                        PickerHeader("Default quality")
                        AppSettings.heights.forEach { h ->
                            PopOptionTile(
                                label = if (h == null) "Best available" else "${h}p or lower",
                                sizeLabel = null,
                                selected = settings.maxHeight == h,
                                onClick = {
                                    onUpdateSettings { it.copy(maxHeight = h) }
                                    activePicker = null
                                },
                            )
                        }
                    }
                    SettingsPicker.VIDEO_CONTAINER -> {
                        PickerHeader("Video format")
                        AppSettings.containers.forEach { c ->
                            val lbl = when (c) {
                                "mp4" -> "MP4 · plays everywhere"
                                "mkv" -> "MKV · keeps every track"
                                else -> "WEBM · smaller files"
                            }
                            PopOptionTile(
                                label = lbl,
                                sizeLabel = null,
                                selected = settings.container == c,
                                onClick = {
                                    onUpdateSettings { it.copy(container = c) }
                                    activePicker = null
                                },
                            )
                        }
                    }
                    SettingsPicker.AUDIO_FORMAT -> {
                        PickerHeader("Audio format")
                        AppSettings.audioFormats.forEach { f ->
                            val lbl = when (f) {
                                "mp3" -> "MP3 · plays everywhere"
                                "m4a" -> "M4A · good for Apple devices"
                                "opus" -> "Opus · smallest files"
                                else -> "FLAC · lossless, large"
                            }
                            PopOptionTile(
                                label = lbl,
                                sizeLabel = null,
                                selected = settings.audioFormat == f,
                                onClick = {
                                    onUpdateSettings { it.copy(audioFormat = f) }
                                    activePicker = null
                                },
                            )
                        }
                    }
                    SettingsPicker.CONCURRENCY -> {
                        PickerHeader("Downloads at once")
                        listOf(1, 2, 3).forEach { n ->
                            PopOptionTile(
                                label = if (n == 1) "1 at a time" else "$n at once",
                                sizeLabel = null,
                                selected = settings.concurrency == n,
                                onClick = {
                                    onUpdateSettings { it.copy(concurrency = n) }
                                    activePicker = null
                                },
                            )
                        }
                    }
                    SettingsPicker.THEME -> {
                        PickerHeader("Theme")
                        AppThemeMode.entries.forEach { t ->
                            PopOptionTile(
                                label = t.label,
                                sizeLabel = null,
                                selected = settings.themeMode == t,
                                onClick = {
                                    onUpdateSettings { it.copy(themeMode = t) }
                                    activePicker = null
                                },
                            )
                        }
                    }
                }
            }
        }
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
        item(key = "settings_title") {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = pop.text,
                ),
            )
            Spacer(Modifier.height(20.dp))
        }

        item(key = "settings_downloads") {
            PopSection("Downloads")
            SettingsGroup {
                SettingsRow(
                    label = "Save to",
                    value = settings.savePathLabel,
                    onClick = { showFolderDialog = true },
                )
                SettingsDivider()
                SettingsRow(
                    label = "File names",
                    value = MediaHelpers.previewFileName(settings.fileTemplate, settings.container),
                    onClick = { showTemplateDialog = true },
                )
                SettingsDivider()
                SettingsRow(
                    label = "Default mode",
                    value = settings.defaultMode.label,
                    onClick = { activePicker = SettingsPicker.DEFAULT_MODE },
                )
                SettingsDivider()
                SettingsRow(
                    label = "Default quality",
                    value = settings.maxHeight?.let { "${it}p" } ?: "Best available",
                    onClick = { activePicker = SettingsPicker.DEFAULT_QUALITY },
                )
                SettingsDivider()
                SettingsRow(
                    label = "Video format",
                    value = settings.container.uppercase(),
                    onClick = { activePicker = SettingsPicker.VIDEO_CONTAINER },
                )
                SettingsDivider()
                SettingsRow(
                    label = "Audio format",
                    value = settings.audioFormat.uppercase(),
                    onClick = { activePicker = SettingsPicker.AUDIO_FORMAT },
                )
                SettingsDivider()
                SettingsRow(
                    label = "Downloads at once",
                    value = "${settings.concurrency}",
                    onClick = { activePicker = SettingsPicker.CONCURRENCY },
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        item(key = "settings_appearance") {
            PopSection("Appearance & Feel")
            SettingsGroup {
                SettingsRow(
                    label = "Theme",
                    value = settings.themeMode.label,
                    onClick = { activePicker = SettingsPicker.THEME },
                    tag = "settings_theme_row",
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tactile haptics",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = pop.text,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = "Crisp taps for actions, success and alerts",
                            style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                        )
                    }
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { on ->
                            onUpdateSettings { it.copy(hapticsEnabled = on) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pop.onStrong,
                            checkedTrackColor = pop.strong,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        item(key = "settings_storage") {
            PopSection("Storage & Engine")
            SettingsGroup {
                SettingsRow(
                    label = "Saved files",
                    value = MediaHelpers.formatBytes(storageBytes) ?: "0 KB",
                    onClick = null,
                )
                SettingsDivider()
                SettingsRow(
                    label = "Bundled engine",
                    value = if (engineVersion != null) "yt-dlp $engineVersion · FFmpeg" else "Initializing…",
                    onClick = null,
                )
                if (failedCount > 0) {
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PopButton(
                            label = "Clear $failedCount failed",
                            height = 46.dp,
                            onClick = onClearFailed,
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        item(key = "settings_about") {
            PopSection("Grab")
            SettingsGroup {
                SettingsRow(
                    label = "About Grab & updates",
                    value = "v${BuildConfig.VERSION_NAME}",
                    onClick = onOpenAbout,
                    tag = "settings_about_row",
                )
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String) {
    val pop = GrabThemeTokens.pop
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            color = pop.text,
            fontWeight = FontWeight.ExtraBold,
        ),
        modifier = Modifier.padding(bottom = 14.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val pop = GrabThemeTokens.pop
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.card),
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = GrabThemeTokens.pop.track,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    tag: String? = null,
) {
    val pop = GrabThemeTokens.pop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        GrabHaptics.selection()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = pop.text,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = pop.muted,
            )
        }
    }
}

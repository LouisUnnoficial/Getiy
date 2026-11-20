package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.core.GetiyHaptics
import com.example.data.engine.EngineStatus
import com.example.data.model.AppThemeMode
import com.example.data.model.DownloadMode
import com.example.data.model.MediaHelpers
import com.example.data.settings.AppSettings
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopOptionTile
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSection
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyAzure
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyPeriwinkle
import com.example.ui.theme.GetiyThemeTokens

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
    engineStatus: EngineStatus,
    storageBytes: Long,
    failedCount: Int,
    extraBottomPadding: Int,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onReinitEngines: () -> Unit,
    onClearFailed: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
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
                                    GetiyHaptics.selection()
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
                        onUpdateSettings { it.copy(saveBase = base, saveFolder = folder.ifBlank { "Getiy" }) }
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
                                    GetiyHaptics.selection()
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
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = (36 + extraBottomPadding).dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 2x2 Quick Control Bento Grid
        item(key = "settings_bento_grid") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PopSection("Quick Controls")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickSettingBentoTile(
                        icon = Icons.Rounded.DarkMode,
                        title = "Theme",
                        value = settings.themeMode.label,
                        accent = GetiyMint,
                        onClick = { activePicker = SettingsPicker.THEME },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_theme_row"),
                    )
                    QuickSettingBentoTile(
                        icon = Icons.Rounded.Tune,
                        title = "Default Mode",
                        value = settings.defaultMode.shortLabel,
                        accent = GetiyAzure,
                        onClick = { activePicker = SettingsPicker.DEFAULT_MODE },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickSettingBentoTile(
                        icon = Icons.Rounded.HighQuality,
                        title = "Max Quality",
                        value = settings.maxHeight?.let { "${it}p" } ?: "Best",
                        accent = GetiyPeriwinkle,
                        onClick = { activePicker = SettingsPicker.DEFAULT_QUALITY },
                        modifier = Modifier.weight(1f),
                    )
                    QuickSettingBentoTile(
                        icon = Icons.Rounded.Vibration,
                        title = "Haptics",
                        value = if (settings.hapticsEnabled) "Enabled" else "Off",
                        accent = if (settings.hapticsEnabled) GetiyMint else pop.muted,
                        highlighted = settings.hapticsEnabled,
                        onClick = {
                            onUpdateSettings { it.copy(hapticsEnabled = !it.hapticsEnabled) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Output & Format Panel
        item(key = "settings_output") {
            Column {
                PopSection("Output & Formats")
                SettingsGroup {
                    SettingsRow(
                        label = "Save location",
                        value = settings.savePathLabel,
                        onClick = { showFolderDialog = true },
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = "File name pattern",
                        value = MediaHelpers.previewFileName(settings.fileTemplate, settings.container),
                        onClick = { showTemplateDialog = true },
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = "Video container",
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
                        label = "Parallel downloads",
                        value = if (settings.concurrency == 1) "1 at a time" else "${settings.concurrency} at once",
                        onClick = { activePicker = SettingsPicker.CONCURRENCY },
                    )
                }
            }
        }

        // Storage & Engine Diagnostics
        item(key = "settings_storage") {
            Column {
                PopSection("Storage & Native Engines")
                SettingsGroup {
                    SettingsRow(
                        label = "Vault footprint",
                        value = MediaHelpers.formatBytes(storageBytes) ?: "0 KB",
                        onClick = null,
                    )
                    SettingsDivider()
                    EngineStatusRow(
                        name = "yt-dlp",
                        detail = when {
                            engineStatus.initializing -> "Initializing Python & extractor bundle…"
                            engineStatus.ytDlpReady -> "Version ${engineStatus.ytDlpVersion ?: "bundled"} · 1,800+ sites"
                            else -> "Unavailable"
                        },
                        ready = engineStatus.ytDlpReady,
                        initializing = engineStatus.initializing,
                    )
                    SettingsDivider()
                    EngineStatusRow(
                        name = "FFmpeg",
                        detail = when {
                            engineStatus.initializing -> "Preparing native codecs & muxer…"
                            engineStatus.ffmpegReady -> {
                                val ver = engineStatus.ffmpegVersion?.let { "v$it · " } ?: ""
                                "${ver}Merging, audio extraction & ringtones"
                            }
                            else -> "Unavailable"
                        },
                        ready = engineStatus.ffmpegReady,
                        initializing = engineStatus.initializing,
                    )
                    SettingsDivider()
                    EngineStatusRow(
                        name = "youtubedl-android",
                        detail = "Runtime v${engineStatus.youtubedlAndroidVersion} · Bundled native APK libs",
                        ready = engineStatus.allCoreReady,
                        initializing = engineStatus.initializing,
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aria2c accelerator",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = pop.text,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (engineStatus.aria2cReady) {
                                    "Multi-connection downloads for full files"
                                } else if (engineStatus.initializing) {
                                    "Checking Aria2c module…"
                                } else {
                                    "Module not available on this device"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = settings.useAria2c && engineStatus.aria2cReady,
                            enabled = engineStatus.aria2cReady,
                            onCheckedChange = { enabled ->
                                GetiyHaptics.selection()
                                onUpdateSettings { it.copy(useAria2c = enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = pop.onStrong,
                                checkedTrackColor = pop.strong,
                                uncheckedThumbColor = pop.muted,
                                uncheckedTrackColor = pop.soft,
                            ),
                            modifier = Modifier.testTag("settings_aria2c_switch"),
                        )
                    }
                    if (!engineStatus.error.isNullOrBlank()) {
                        SettingsDivider()
                        Text(
                            text = engineStatus.error,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    SettingsDivider()
                    Box(modifier = Modifier.padding(14.dp)) {
                        PopButton(
                            label = if (engineStatus.initializing) "Verifying engines…" else "Verify engines",
                            icon = Icons.Rounded.Refresh,
                            height = 46.dp,
                            busy = engineStatus.initializing,
                            enabled = !engineStatus.initializing,
                            onClick = onReinitEngines,
                            tag = "settings_reinit_engines",
                        )
                    }
                    if (failedCount > 0) {
                        SettingsDivider()
                        Box(modifier = Modifier.padding(14.dp)) {
                            PopButton(
                                label = "Clear $failedCount failed jobs",
                                height = 46.dp,
                                onClick = onClearFailed,
                            )
                        }
                    }
                }
            }
        }

        // Getiy Brand About Card
        item(key = "settings_about") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PopCardShape)
                    .background(pop.card)
                    .border(BorderStroke(1.dp, pop.track), PopCardShape)
                    .testTag("settings_about_row")
                    .clickable {
                        GetiyHaptics.selection()
                        onOpenAbout()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_getiy_logo),
                    contentDescription = "Getiy logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .border(1.dp, pop.track, RoundedCornerShape(13.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "About Getiy",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = pop.text,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} · Updates & credits",
                        style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = pop.strong,
                )
            }
        }
    }
}

@Composable
private fun QuickSettingBentoTile(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = modifier
            .clip(PopCardShape)
            .background(if (highlighted) pop.soft else pop.card)
            .border(
                width = 1.dp,
                color = if (highlighted) accent.copy(alpha = 0.55f) else pop.track,
                shape = PopCardShape,
            )
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(15.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(color = pop.muted, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = pop.text,
                fontWeight = FontWeight.ExtraBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PickerHeader(title: String) {
    val pop = GetiyThemeTokens.pop
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
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.card)
            .border(1.dp, pop.track, PopCardShape),
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = GetiyThemeTokens.pop.track,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    tag: String? = null,
) {
    val pop = GetiyThemeTokens.pop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        GetiyHaptics.selection()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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

@Composable
private fun EngineStatusRow(
    name: String,
    detail: String,
    ready: Boolean,
    initializing: Boolean,
) {
    val pop = GetiyThemeTokens.pop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = pop.text,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = when {
                initializing -> Icons.Rounded.Sync
                ready -> Icons.Rounded.CheckCircle
                else -> Icons.Rounded.ErrorOutline
            },
            contentDescription = when {
                initializing -> "$name initializing"
                ready -> "$name ready"
                else -> "$name unavailable"
            },
            tint = when {
                initializing -> pop.muted
                ready -> pop.strong
                else -> MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

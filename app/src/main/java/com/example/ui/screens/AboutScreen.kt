package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.FileShareHelper
import com.example.core.GrabHaptics
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopSection
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopYellow
import com.example.ui.viewmodel.ReleaseCheckState

@Composable
fun AboutScreen(
    releaseState: ReleaseCheckState,
    onCheckUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val pop = GrabThemeTokens.pop
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "about_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(PopYellow)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                PopIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    background = PopInk.copy(alpha = 0.12f),
                    foreground = PopInk,
                    onClick = onBack,
                    tag = "about_back_button",
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(PopInk),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Grab emblem",
                        tint = PopYellow,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Grab",
                    style = MaterialTheme.typography.displayMedium.copy(
                        color = PopInk,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} · Paste a link. Get the file.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = PopInk.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(22.dp))
        }

        item(key = "about_updates") {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                UpdateCheckCard(
                    state = releaseState,
                    onCheck = onCheckUpdates,
                    onOpenRelease = { url -> FileShareHelper.openExternalUrl(context, url) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        item(key = "about_project_links") {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                PopSection("Project")
                LinkCard(
                    items = listOf(
                        Triple(Icons.Rounded.Code, "GitHub repository", "Source code & releases") to "https://github.com/Asta39/grab",
                        Triple(Icons.Rounded.BugReport, "Report an issue", "Found a broken site or bug?") to "https://github.com/Asta39/grab/issues",
                    ),
                    onOpen = { FileShareHelper.openExternalUrl(context, it) },
                )
                Spacer(Modifier.height(22.dp))
            }
        }

        item(key = "about_credits") {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                PopSection("Built with")
                LinkCard(
                    items = listOf(
                        Triple(Icons.Rounded.Terminal, "yt-dlp", "1,800+ supported sites") to "https://github.com/yt-dlp/yt-dlp",
                        Triple(Icons.Rounded.MovieFilter, "FFmpeg", "Merging, audio & clips") to "https://ffmpeg.org",
                        Triple(Icons.Rounded.Android, "youtubedl-android", "Runs both natively on Android") to "https://github.com/yausername/youtubedl-android",
                    ),
                    onOpen = { FileShareHelper.openExternalUrl(context, it) },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PopCardShape)
                        .background(pop.card)
                        .clickable {
                            FileShareHelper.openExternalUrl(context, "https://github.com/Asta39/grab/blob/main/LICENSE")
                        }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Gavel, contentDescription = null, tint = pop.text)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open-source license",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = pop.text,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = "Free software under GPL-3.0",
                            style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                        )
                    }
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = pop.muted, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Download only what you have the right to save. Respect creators and each site's rules. Grab isn't affiliated with any site it supports.",
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UpdateCheckCard(
    state: ReleaseCheckState,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val pop = GrabThemeTokens.pop
    val highlight = state is ReleaseCheckState.Available

    val icon: ImageVector = when (state) {
        ReleaseCheckState.Idle -> Icons.Rounded.SystemUpdate
        ReleaseCheckState.Checking -> Icons.Rounded.Sync
        is ReleaseCheckState.UpToDate -> Icons.Rounded.CheckCircle
        is ReleaseCheckState.Available -> Icons.Rounded.Celebration
        is ReleaseCheckState.Error -> Icons.Rounded.WifiOff
    }
    val title = when (state) {
        ReleaseCheckState.Idle -> "Check for updates"
        ReleaseCheckState.Checking -> "Checking…"
        is ReleaseCheckState.UpToDate -> "You're up to date"
        is ReleaseCheckState.Available -> "${state.name.ifBlank { state.tag }} is out"
        is ReleaseCheckState.Error -> "Something went wrong"
    }
    val body = when (state) {
        ReleaseCheckState.Idle -> "Looks for the latest release on GitHub."
        ReleaseCheckState.Checking -> null
        is ReleaseCheckState.UpToDate -> if (state.latestTag == null) {
            "No newer releases published yet."
        } else {
            "Latest release is ${state.latestTag}."
        }
        is ReleaseCheckState.Available -> "Open the GitHub release page to view notes and download."
        is ReleaseCheckState.Error -> state.message
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(if (highlight) pop.soft else pop.card)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = pop.text, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = pop.text,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted))
        }
        if (state is ReleaseCheckState.Available && state.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(pop.bg)
                    .padding(12.dp),
            ) {
                Text(
                    text = state.notes.take(400),
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.text),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state is ReleaseCheckState.Available) {
            PopButton(
                label = "Open release page",
                icon = Icons.Rounded.OpenInNew,
                height = 48.dp,
                onClick = { onOpenRelease(state.htmlUrl) },
            )
        } else {
            PopButton(
                label = if (state is ReleaseCheckState.Error) "Try again" else "Check now",
                icon = Icons.Rounded.Refresh,
                busy = state is ReleaseCheckState.Checking,
                enabled = state !is ReleaseCheckState.Checking,
                height = 48.dp,
                onClick = onCheck,
                tag = "about_check_updates_button",
            )
        }
    }
}

@Composable
private fun LinkCard(
    items: List<Pair<Triple<ImageVector, String, String>, String>>,
    onOpen: (String) -> Unit,
) {
    val pop = GrabThemeTokens.pop
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.card),
    ) {
        items.forEachIndexed { idx, (meta, url) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        GrabHaptics.selection()
                        onOpen(url)
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(meta.first, contentDescription = null, tint = pop.text)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.second,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = pop.text,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = meta.third,
                        style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Open link",
                    tint = pop.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (idx < items.lastIndex) {
                HorizontalDivider(color = pop.track, modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

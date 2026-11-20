package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.core.FileShareHelper
import com.example.core.GetiyHaptics
import com.example.data.engine.EngineStatus
import com.example.ui.components.PopButton
import com.example.ui.components.PopCardShape
import com.example.ui.components.PopIconButton
import com.example.ui.components.PopPillShape
import com.example.ui.components.PopSection
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyAzure
import com.example.ui.theme.GetiyIce
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.viewmodel.ReleaseCheckState

@Composable
fun AboutScreen(
    releaseState: ReleaseCheckState,
    engineStatus: EngineStatus,
    onCheckUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val pop = GetiyThemeTokens.pop
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pop.bg)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Centered Studio Identity Hero
        item(key = "about_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GetiyNavy)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                PopIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    background = Color.White.copy(alpha = 0.10f),
                    foreground = GetiyIce,
                    onClick = onBack,
                    tag = "about_back_button",
                    modifier = Modifier.align(Alignment.TopStart),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 42.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_getiy_logo),
                        contentDescription = "Getiy logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0xFF2B4173), RoundedCornerShape(24.dp)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Getiy",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = GetiyIce,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(PopPillShape)
                            .background(Color(0xFF1D305B))
                            .border(1.dp, Color(0xFF2E457B), PopPillShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} · Media Capture Studio",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = GetiyMint,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }

        // Release Check Card
        item(key = "about_updates") {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                UpdateCheckCard(
                    state = releaseState,
                    onCheck = onCheckUpdates,
                    onOpenRelease = { url -> FileShareHelper.openExternalUrl(context, url) },
                )
            }
        }

        // 2-Column Project Bento Links
        item(key = "about_project_links") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                PopSection("Project")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AboutBentoLinkTile(
                        icon = Icons.Rounded.Code,
                        title = "GitHub Repo",
                        subtitle = "Source & releases",
                        accent = GetiyMint,
                        onClick = { FileShareHelper.openExternalUrl(context, "https://github.com/tpxlegend/Getiy") },
                        modifier = Modifier.weight(1f),
                    )
                    AboutBentoLinkTile(
                        icon = Icons.Rounded.BugReport,
                        title = "Report Issue",
                        subtitle = "Broken site or bug",
                        accent = GetiyAzure,
                        onClick = { FileShareHelper.openExternalUrl(context, "https://github.com/tpxlegend/Getiy/issues") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Engine Credits & License
        item(key = "about_credits") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                PopSection("Powered By")
                val ytSub = engineStatus.ytDlpVersion?.let { "v$it · 1,800+ supported sites" } ?: "1,800+ supported sites"
                val ffSub = engineStatus.ffmpegVersion?.let { "v$it · Merging, audio & ringtones" } ?: "Merging, audio & ringtones"
                val ydlSub = "v${engineStatus.youtubedlAndroidVersion} · Native Android runtime + Aria2c"
                LinkCard(
                    items = listOf(
                        Triple(Icons.Rounded.Terminal, "yt-dlp", ytSub) to "https://github.com/yt-dlp/yt-dlp",
                        Triple(Icons.Rounded.MovieFilter, "FFmpeg", ffSub) to "https://github.com/FFmpeg/FFmpeg",
                        Triple(Icons.Rounded.Android, "youtubedl-android", ydlSub) to "https://github.com/yausername/youtubedl-android",
                        Triple(Icons.Rounded.Gavel, "GPL-3.0 License", "Free & open-source software") to "https://github.com/tpxlegend/Getiy",
                    ),
                    onOpen = { FileShareHelper.openExternalUrl(context, it) },
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Download only what you have the right to save. Respect creators and each site's rules. Getiy isn't affiliated with any site it supports.",
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AboutBentoLinkTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = modifier
            .clip(PopCardShape)
            .background(pop.card)
            .border(1.dp, pop.track, PopCardShape)
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = pop.muted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = pop.text,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
        )
    }
}

@Composable
private fun UpdateCheckCard(
    state: ReleaseCheckState,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val pop = GetiyThemeTokens.pop
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
            .border(BorderStroke(1.dp, if (highlight) GetiyMint else pop.track), PopCardShape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = pop.strong, modifier = Modifier.size(26.dp))
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(pop.bg)
                    .padding(12.dp),
            ) {
                Text(
                    text = state.notes.take(400),
                    style = MaterialTheme.typography.bodySmall.copy(color = pop.text),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (state is ReleaseCheckState.Available) {
            PopButton(
                label = "Open release page",
                icon = Icons.Rounded.OpenInNew,
                useGradient = true,
                height = 48.dp,
                onClick = { onOpenRelease(state.htmlUrl) },
            )
        } else {
            PopButton(
                label = if (state is ReleaseCheckState.Error) "Try again" else "Check now",
                icon = Icons.Rounded.Refresh,
                useGradient = true,
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
    val pop = GetiyThemeTokens.pop
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PopCardShape)
            .background(pop.card)
            .border(1.dp, pop.track, PopCardShape),
    ) {
        items.forEachIndexed { idx, (meta, url) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        GetiyHaptics.selection()
                        onOpen(url)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(meta.first, contentDescription = null, tint = pop.strong, modifier = Modifier.size(22.dp))
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
                HorizontalDivider(color = pop.track, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

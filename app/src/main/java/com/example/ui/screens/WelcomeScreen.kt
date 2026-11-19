package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.GrabHaptics
import com.example.ui.components.PopPillShape
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopYellow
import kotlinx.coroutines.launch

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val steps: List<String>,
)

private val welcomePages = listOf(
    WelcomePage(
        icon = Icons.Rounded.Download,
        title = "Paste a link.\nGet the file.",
        body = "Video, audio or subtitles from TikTok, YouTube, Instagram, Pinterest and 1,800+ more sites. Free, no account, no ads.",
        steps = emptyList(),
    ),
    WelcomePage(
        icon = Icons.Rounded.IosShare,
        title = "Share straight\nto Grab",
        body = "The fastest way in, from any app:",
        steps = listOf(
            "Tap Share on a video",
            "Pick Grab",
            "Choose a format, done",
        ),
    ),
    WelcomePage(
        icon = Icons.Rounded.Bolt,
        title = "One tap from\nanywhere",
        body = "Copy a link, then tap the Grab tile in Quick Settings. Add it once:",
        steps = listOf(
            "Swipe down twice from the top",
            "Tap the pencil (Edit)",
            "Drag \"Grab link\" into your tiles",
        ),
    ),
    WelcomePage(
        icon = Icons.Rounded.NotificationsActive,
        title = "Know when\nit's done",
        body = "Grab keeps downloading when you leave the app. Allow notifications to see progress and a \"Saved\" alert.",
        steps = listOf(
            "Only about your downloads",
            "No ads, no tracking, ever",
            "Change it any time in Settings",
        ),
    ),
)

@Composable
fun WelcomeScreen(
    onDone: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { welcomePages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == welcomePages.lastIndex

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { onDone() },
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0) GrabHaptics.selection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PopYellow)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    GrabHaptics.light()
                    onDone()
                },
                modifier = Modifier.testTag("welcome_skip_button"),
            ) {
                Text(
                    text = if (isLast) "Not now" else "Skip",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = PopInk,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { idx ->
            val page = welcomePages[idx]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(44.dp))
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(PopInk),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = page.title,
                            tint = PopYellow,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        color = PopInk,
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = PopInk,
                        lineHeight = 22.sp,
                    ),
                )
                if (page.steps.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    page.steps.forEachIndexed { n, step ->
                        Row(
                            modifier = Modifier.padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(PopInk),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${n + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = PopYellow,
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = PopInk,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = 24.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            welcomePages.indices.forEach { i ->
                val active = i == pagerState.currentPage
                val dotW by animateDpAsState(
                    targetValue = if (active) 24.dp else 8.dp,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                    label = "dotW",
                )
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .width(dotW)
                        .height(8.dp)
                        .clip(PopPillShape)
                        .background(if (active) PopInk else PopInk.copy(alpha = 0.25f)),
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = {
                    GrabHaptics.light()
                    if (isLast) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onDone()
                        }
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                shape = PopPillShape,
                color = PopInk,
                modifier = Modifier.testTag("welcome_next_button"),
            ) {
                Text(
                    text = if (isLast) "Allow & start" else "Next",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = PopYellow,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 16.dp),
                )
            }
        }
    }
}

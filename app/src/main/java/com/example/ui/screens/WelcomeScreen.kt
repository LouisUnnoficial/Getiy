package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.GrabHaptics
import com.example.ui.components.PopButton
import com.example.ui.components.PopPillShape
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyIce
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyNavyDeep
import kotlinx.coroutines.launch

private data class WelcomePage(
    val badge: String,
    val icon: ImageVector,
    val title: String,
    val body: String,
    val steps: List<String>,
    val showBrandLogo: Boolean = false,
)

private val welcomePages = listOf(
    WelcomePage(
        badge = "WELCOME TO GETIY",
        icon = Icons.Rounded.Download,
        title = "Paste a link.\nGet the file.",
        body = "Save video, audio, clips or subtitles from 1,800+ sites with zero ads and zero accounts.",
        steps = listOf(
            "4K video, MP3 / M4A / FLAC audio & captions",
            "Built-in trim editor & ringtone maker",
            "Runs privately & natively on your phone",
        ),
        showBrandLogo = true,
    ),
    WelcomePage(
        badge = "INSTANT SHARE",
        icon = Icons.Rounded.IosShare,
        title = "Share straight\nto Getiy",
        body = "Send media straight from any social or video app:",
        steps = listOf(
            "Tap Share on a video or post",
            "Pick Getiy from the share sheet",
            "Choose your format and save",
        ),
    ),
    WelcomePage(
        badge = "QUICK TILE",
        icon = Icons.Rounded.Bolt,
        title = "One tap from\nanywhere",
        body = "Copy any link, then tap the Getiy Quick Settings tile:",
        steps = listOf(
            "Swipe down twice from the top",
            "Tap Edit on your Quick Settings panel",
            "Drag \"Getiy link\" into your active tiles",
        ),
    ),
    WelcomePage(
        badge = "BACKGROUND ENGINE",
        icon = Icons.Rounded.NotificationsActive,
        title = "Know when\nit's saved",
        body = "Getiy keeps working while you switch apps. Allow notifications for live progress & alerts:",
        steps = listOf(
            "Live speed & completion notifications",
            "Zero tracking, zero promo spam",
            "Customizable any time in Settings",
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
    val stepProgress by animateFloatAsState(
        targetValue = (pagerState.currentPage + 1f) / welcomePages.size.toFloat(),
        animationSpec = tween(260),
        label = "welcomeProgress",
    )

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
            .background(GetiyNavy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // Top Header: Brand Identity + Skip Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_getiy_logo),
                    contentDescription = "Getiy logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, Color(0xFF263A6A), RoundedCornerShape(9.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Getiy",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = GetiyIce,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
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
                        color = GetiyIce.copy(alpha = 0.80f),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        // Sleek linear step progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(PopPillShape)
                .background(Color(0xFF1C2F5C)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stepProgress)
                    .height(4.dp)
                    .clip(PopPillShape)
                    .background(GetiyAccentBrush),
            )
        }

        // Open Full-Screen Content (no enclosing card layout)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { idx ->
            val page = welcomePages[idx]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 40.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                if (page.showBrandLogo) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_getiy_logo),
                        contentDescription = "Getiy logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, Color(0xFF2B4173), RoundedCornerShape(22.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(GetiyMint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = page.title,
                            tint = GetiyNavyDeep,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = page.badge,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF8F9FC0),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        color = GetiyIce,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF9DB5DF),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                )

                if (page.steps.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        page.steps.forEachIndexed { n, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(GetiyMint),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${n + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = GetiyNavyDeep,
                                            fontWeight = FontWeight.ExtraBold,
                                        ),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = GetiyIce,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Footer: Back + Full-Width Continue CTA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage > 0) {
                Surface(
                    onClick = {
                        GrabHaptics.selection()
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF152345),
                    border = BorderStroke(1.dp, Color(0xFF263A6A)),
                    modifier = Modifier.size(54.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Previous step",
                            tint = GetiyIce,
                        )
                    }
                }
            }

            PopButton(
                label = if (isLast) "Allow & Launch Getiy" else "Continue",
                icon = Icons.Rounded.ArrowForward,
                useGradient = true,
                height = 54.dp,
                shape = RoundedCornerShape(16.dp),
                onClick = {
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
                modifier = Modifier.weight(1f),
                tag = "welcome_next_button",
            )
        }
    }
}

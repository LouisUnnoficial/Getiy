package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.GetiyHaptics
import com.example.core.Links
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyAzure
import com.example.ui.theme.GetiyIce
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyNavyDeep
import com.example.ui.theme.GetiyPeriwinkle
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.theme.PopError
import com.example.ui.viewmodel.ClipboardOfferState
import com.example.ui.viewmodel.PopToastMessage
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class PopTabItem(
    val icon: ImageVector,
    val label: String,
    val badge: Int = 0,
    val testTag: String,
)

/**
 * Top Studio Header + Segmented Pill Navigation Bar (replaces the old bottom dock).
 * Keeps Getiy's #0E1935 Navy & Mint-to-Azure palette while relocating navigation to the top.
 */
@Composable
fun GetiyTopNavBar(
    tabs: List<PopTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    val containerBg = if (pop.isDark) Color(0xF50E1935) else Color(0xF5F4F7FC)
    val railBg = if (pop.isDark) Color(0xFF152345) else Color(0xFFE7EEF8)
    val railBorder = if (pop.isDark) Color(0xFF263A6A) else Color(0xFFD2DDF0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerBg)
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
    ) {
        // Clean brand identity row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_getiy_logo),
                contentDescription = "Getiy logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, railBorder, RoundedCornerShape(9.dp)),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Getiy",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = pop.text,
                    letterSpacing = (-0.4).sp,
                ),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Top segmented sliding tab strip
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(railBg)
                .border(1.dp, railBorder, RoundedCornerShape(16.dp))
                .padding(4.dp),
        ) {
            val count = tabs.size.coerceAtLeast(1)
            val segW = maxWidth / count
            val indicatorX by animateDpAsState(
                targetValue = segW * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "topNavIndicator",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorX)
                    .width(segW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GetiyAccentBrush),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { idx, tab ->
                    val active = idx == selectedIndex
                    val fg by animateColorAsState(
                        targetValue = if (active) GetiyNavyDeep else pop.muted,
                        animationSpec = tween(160),
                        label = "topNavFg",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .testTag(tab.testTag)
                            .clickable {
                                if (!active) {
                                    GetiyHaptics.selection()
                                    onSelect(idx)
                                }
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BadgedBox(
                            badge = {
                                if (tab.badge > 0) {
                                    Badge(
                                        containerColor = if (active) GetiyNavy else GetiyMint,
                                        contentColor = if (active) GetiyMint else GetiyNavyDeep,
                                    ) {
                                        Text(
                                            text = "${tab.badge}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = fg,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = fg,
                                fontSize = 11.5.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Left Navigation Rail for Expanded screens (tablets / foldables).
 */
@Composable
fun GetiySideRail(
    tabs: List<PopTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    val railBg = if (pop.isDark) Color(0xFF132245) else Color(0xFFE9F1FC)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(railBg)
            .statusBarsPadding()
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_getiy_logo),
            contentDescription = "Getiy logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .border(1.dp, pop.track, RoundedCornerShape(13.dp)),
        )
        Spacer(Modifier.height(12.dp))
        tabs.forEachIndexed { idx, tab ->
            val active = idx == selectedIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (active) Modifier.background(GetiyAccentBrush) else Modifier,
                    )
                    .testTag(tab.testTag)
                    .clickable {
                        if (!active) {
                            GetiyHaptics.selection()
                            onSelect(idx)
                        }
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BadgedBox(
                    badge = {
                        if (tab.badge > 0) {
                            Badge(
                                containerColor = if (active) GetiyNavy else GetiyMint,
                                contentColor = if (active) GetiyMint else GetiyNavyDeep,
                            ) {
                                Text("${tab.badge}", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) GetiyNavyDeep else pop.muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (active) GetiyNavyDeep else pop.muted,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Floating "Link copied" banner with pulsing Mint-to-Azure "Get it" button.
 */
@Composable
fun ClipboardOfferCard(
    offer: ClipboardOfferState,
    onFetchOffer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    liteMode: Boolean = false,
) {
    val scale = if (liteMode) {
        1f
    } else {
        val pulse = rememberInfiniteTransition(label = "getiyPulse")
        val animatedScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )
        animatedScale
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF152345),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFF2F457A)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("clipboard_offer_card"),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${offer.platform} link detected",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = GetiyIce,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Links.displayHostAndPath(offer.link),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GetiyIce.copy(alpha = 0.68f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_offer_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = GetiyIce.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Surface(
                onClick = {
                    GetiyHaptics.light()
                    onFetchOffer()
                },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .scale(scale)
                    .testTag("getiy_offer_button"),
            ) {
                Row(
                    modifier = Modifier
                        .background(GetiyAccentBrush, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = GetiyNavyDeep,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Get it",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = GetiyNavyDeep,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun PopToastHost(
    message: PopToastMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 2 },
        modifier = modifier,
    ) {
        if (message != null) {
            val bg = if (message.isError) PopError else Color(0xFF152345)
            val fg = if (message.isError) Color.White else GetiyMint
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                color = bg,
                border = if (message.isError) null else BorderStroke(1.dp, Color(0xFF2F457A)),
                shadowElevation = 10.dp,
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (message.isError) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = message.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!message.detail.isNullOrBlank()) {
                            Text(
                                text = message.detail,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.78f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val spin: Float,
)

@Composable
fun PopConfettiOverlay(
    triggerCount: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled || triggerCount <= 0) return

    var progress by remember { mutableFloatStateOf(1f) }
    val particles = remember(triggerCount) {
        val colors = listOf(GetiyMint, GetiyAzure, GetiyPeriwinkle, GetiyIce)
        val rng = Random(triggerCount * 31 + 7)
        List(20) {
            ConfettiParticle(
                angle = rng.nextFloat() * 360f,
                speed = 240f + rng.nextFloat() * 440f,
                color = colors[it % colors.size],
                width = 12f + rng.nextFloat() * 14f,
                height = 8f + rng.nextFloat() * 10f,
                spin = (rng.nextFloat() - 0.5f) * 720f,
            )
        }
    }

    LaunchedEffect(triggerCount) {
        progress = 0f
        val start = System.currentTimeMillis()
        val duration = 1000L
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            progress = t
            if (t >= 1f) break
            delay(20)
        }
    }

    if (progress < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val originX = size.width / 2f
            val originY = size.height * 0.14f
            val alpha = (1f - progress).coerceIn(0f, 1f)
            val gravity = 650f

            for (p in particles) {
                val rad = Math.toRadians(p.angle.toDouble())
                val vx = cos(rad).toFloat() * p.speed
                val vy = sin(rad).toFloat() * p.speed
                val x = originX + vx * progress
                val y = originY + vy * progress + 0.5f * gravity * progress * progress
                rotate(degrees = p.spin * progress, pivot = Offset(x, y)) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                        size = Size(p.width, p.height),
                    )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.GrabHaptics
import com.example.core.Links
import com.example.ui.theme.GrabThemeTokens
import com.example.ui.theme.PopError
import com.example.ui.theme.PopInk
import com.example.ui.theme.PopRosePink
import com.example.ui.theme.PopSkyBlue
import com.example.ui.theme.PopYellow
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
 * Floating frosted-glass pill dock with a gooey yellow blob indicator that stretches
 * liquidly between tabs (leading and trailing edges spring at distinct rates).
 */
@Composable
fun PopTabBar(
    tabs: List<PopTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GrabThemeTokens.pop
    var prevIndex by remember { mutableIntStateOf(selectedIndex) }
    val movingRight = selectedIndex >= prevIndex
    LaunchedEffect(selectedIndex) { prevIndex = selectedIndex }

    val glassSurface = if (pop.isDark) {
        Color(0xE0222222)
    } else {
        Color(0xEBFFFFFF)
    }
    val glassBorder = if (pop.isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        PopInk.copy(alpha = 0.10f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(elevation = 14.dp, shape = PopPillShape, ambientColor = Color.Black.copy(alpha = 0.25f))
                .clip(PopPillShape)
                .background(glassSurface)
                .border(1.dp, glassBorder, PopPillShape)
                .padding(6.dp),
        ) {
            // Active tab takes flex 16, inactive takes flex 10 — matching Grab's expanding active pill!
            val totalFlex = 16f + (tabs.size - 1) * 10f
            fun leftFractionFor(idx: Int): Float = (idx * 10f) / totalFlex
            fun rightFractionFor(idx: Int): Float = (idx * 10f + 16f) / totalFlex

            val targetLeft = maxWidth * leftFractionFor(selectedIndex)
            val targetRight = maxWidth * rightFractionFor(selectedIndex)

            // Asymmetric spring stiffness produces the signature liquid "gooey stretch" between tabs
            val leftX by animateDpAsState(
                targetValue = targetLeft,
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = if (movingRight) Spring.StiffnessLow else Spring.StiffnessMediumLow,
                ),
                label = "gooeyLeft",
            )
            val rightX by animateDpAsState(
                targetValue = targetRight,
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = if (movingRight) Spring.StiffnessMediumLow else Spring.StiffnessLow,
                ),
                label = "gooeyRight",
            )

            // Gooey yellow selection blob
            Box(
                modifier = Modifier
                    .offset(x = leftX)
                    .width((rightX - leftX).coerceAtLeast(48.dp))
                    .fillMaxHeight()
                    .clip(PopPillShape)
                    .background(PopYellow),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val idleTint = if (pop.isDark) Color(0xFFBDBDBD) else Color(0xFF3A3A3A)
                tabs.forEachIndexed { i, tab ->
                    val active = i == selectedIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "tabScale",
                    )
                    Row(
                        modifier = Modifier
                            .weight(if (active) 16f else 10f)
                            .fillMaxHeight()
                            .clip(PopPillShape)
                            .testTag(tab.testTag)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!active) {
                                    GrabHaptics.selection()
                                    onSelect(i)
                                }
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BadgedBox(
                            badge = {
                                if (tab.badge > 0) {
                                    Badge(
                                        containerColor = PopInk,
                                        contentColor = PopYellow,
                                    ) {
                                        Text(
                                            text = "${tab.badge}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (active) PopInk else idleTint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale),
                            )
                        }
                        AnimatedVisibility(visible = active) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PopInk,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating "You copied a link" ink pill card with pulsing yellow "Grab it" button.
 */
@Composable
fun ClipboardOfferCard(
    offer: ClipboardOfferState,
    onGrab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GrabThemeTokens.pop
    val pulse = rememberInfiniteTransition(label = "grabPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Surface(
        shape = PopCardShape,
        color = PopInk,
        shadowElevation = 10.dp,
        border = if (pop.isDark) BorderStroke(1.5.dp, PopYellow) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("clipboard_offer_card"),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${offer.platform} link copied",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Links.displayHostAndPath(offer.link),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f),
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
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Surface(
                onClick = {
                    GrabHaptics.light()
                    onGrab()
                },
                shape = PopPillShape,
                color = PopYellow,
                modifier = Modifier
                    .scale(scale)
                    .testTag("grab_offer_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = PopInk,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Grab it",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = PopInk,
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
            val bg = if (message.isError) PopError else PopInk
            val fg = if (message.isError) Color.White else PopYellow
            Surface(
                onClick = onDismiss,
                shape = PopPillShape,
                color = bg,
                shadowElevation = 10.dp,
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (message.isError) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(20.dp),
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

/**
 * Explosive Pop confetti burst when a download completes.
 */
@Composable
fun PopConfettiOverlay(
    triggerCount: Int,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(1f) }
    val particles = remember(triggerCount) {
        val colors = listOf(PopYellow, PopInk, PopSkyBlue, PopRosePink)
        val rng = Random(triggerCount * 31 + 7)
        List(30) {
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
        if (triggerCount <= 0) return@LaunchedEffect
        progress = 0f
        val start = System.currentTimeMillis()
        val duration = 1200L
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            progress = t
            if (t >= 1f) break
            delay(16)
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

package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.FileShareHelper
import com.example.core.GetiyHaptics
import com.example.ui.theme.GetiyAccentBrush
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.theme.PopInk
import com.example.ui.theme.popTintFor

val PopCardShape = RoundedCornerShape(22.dp)
val PopThumbShape = RoundedCornerShape(16.dp)
val PopPillShape = RoundedCornerShape(999.dp)

@Composable
fun PopButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    expand: Boolean = true,
    height: Dp = 52.dp,
    containerColor: Color? = null,
    contentColor: Color? = null,
    useGradient: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    tag: String? = null,
) {
    val pop = GetiyThemeTokens.pop
    val bg = containerColor ?: pop.strong
    val fg = contentColor ?: if (useGradient) Color(0xFF081024) else pop.onStrong
    val active = enabled && !busy

    Surface(
        onClick = {
            if (active) {
                GetiyHaptics.light()
                onClick()
            }
        },
        enabled = active,
        shape = shape,
        color = when {
            useGradient && active -> Color.Transparent
            useGradient -> bg.copy(alpha = 0.35f)
            active -> bg
            else -> bg.copy(alpha = 0.40f)
        },
        contentColor = fg,
        modifier = modifier
            .then(if (expand) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (useGradient && active) {
                        Modifier.background(GetiyAccentBrush, shape)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = fg,
                )
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = fg,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = fg,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    background: Color? = null,
    foreground: Color? = null,
    size: Dp = 48.dp,
    tag: String? = null,
) {
    val pop = GetiyThemeTokens.pop
    val bg = background ?: pop.field
    val fg = foreground ?: pop.text
    Surface(
        onClick = {
            if (onClick != null) {
                GetiyHaptics.selection()
                onClick()
            }
        },
        enabled = onClick != null,
        shape = CircleShape,
        color = if (onClick != null) bg else bg.copy(alpha = 0.45f),
        contentColor = fg,
        modifier = modifier
            .size(size)
            .minimumInteractiveComponentSize()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (onClick != null) fg else fg.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun PopTag(
    label: String,
    modifier: Modifier = Modifier,
    background: Color? = null,
    contentColor: Color? = null,
) {
    val pop = GetiyThemeTokens.pop
    Box(
        modifier = modifier
            .clip(PopPillShape)
            .background(background ?: pop.soft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = contentColor ?: pop.text,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun PopSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            color = pop.muted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
        ),
        modifier = modifier.padding(bottom = 10.dp, start = 4.dp),
    )
}

/**
 * Thumbnail card that displays Coil media preview, or Pop's minimalist line-art covers for audio files,
 * or a deterministic pastel Pop swatch with crisp iconography.
 */
@Composable
fun PopThumb(
    seed: String,
    url: String? = null,
    audio: Boolean = false,
    subtitles: Boolean = false,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = PopThumbShape,
) {
    val tint = popTintFor(seed)
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        when {
            audio -> {
                Image(
                    painter = painterResource(id = FileShareHelper.audioCoverRes(seed)),
                    contentDescription = "Audio cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            subtitles -> {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = "Subtitles file",
                    tint = PopInk,
                    modifier = Modifier.size(32.dp),
                )
            }
            !url.isNullOrBlank() -> {
                val context = LocalContext.current
                val request = remember(url) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(360, 240)
                        .allowRgb565(true)
                        .crossfade(false)
                        .build()
                }
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = PopInk,
                    modifier = Modifier.size(28.dp),
                )
                AsyncImage(
                    model = request,
                    contentDescription = "Media thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Icon(
                    imageVector = if (audio) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = PopInk,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
fun PopProgressBar(
    progress: Float?, // null = indeterminate
    modifier: Modifier = Modifier,
    trackColor: Color? = null,
    fillColor: Color? = null,
    height: Dp = 8.dp,
) {
    val pop = GetiyThemeTokens.pop
    val track = trackColor ?: pop.track
    val fill = fillColor ?: pop.strong

    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(PopPillShape),
            color = fill,
            trackColor = track,
            strokeCap = StrokeCap.Round,
        )
    } else {
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(220),
            label = "progress",
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(PopPillShape),
            color = fill,
            trackColor = track,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
fun <T> PopSegmented(
    values: List<T>,
    labels: List<String>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    val selectedIdx = values.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(PopPillShape)
            .background(pop.field)
            .padding(4.dp),
    ) {
        val count = values.size.coerceAtLeast(1)
        val segWidth = maxWidth / count
        val indicatorX by animateDpAsState(
            targetValue = segWidth * selectedIdx,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "segIndicator",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(segWidth)
                .fillMaxHeight()
                .clip(PopPillShape)
                .background(pop.strong),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            values.forEachIndexed { idx, value ->
                val active = idx == selectedIdx
                val textColor by animateColorAsState(
                    targetValue = if (active) pop.onStrong else pop.text,
                    animationSpec = tween(160),
                    label = "segText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PopPillShape)
                        .clickable {
                            if (!active) {
                                GetiyHaptics.selection()
                                onSelected(value)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labels[idx],
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PopOptionTile(
    label: String,
    sizeLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = GetiyThemeTokens.pop
    val bg by animateColorAsState(
        targetValue = if (selected) pop.soft else pop.card,
        animationSpec = tween(160),
        label = "optBg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) pop.strong else Color.Transparent,
        animationSpec = tween(160),
        label = "optBorder",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(PopCardShape)
            .background(bg)
            .border(BorderStroke(2.dp, border), PopCardShape)
            .clickable {
                GetiyHaptics.selection()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) pop.strong else pop.muted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = pop.text,
            ),
            modifier = Modifier.weight(1f),
        )
        if (sizeLabel != null) {
            Text(
                text = sizeLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = pop.muted,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

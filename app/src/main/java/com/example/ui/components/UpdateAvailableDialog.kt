package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.AppUpdateChecker
import com.example.core.GetiyHaptics
import com.example.ui.theme.GetiyMint
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.viewmodel.ReleaseCheckState

@Composable
fun UpdateAvailableDialog(
    currentVersion: String,
    release: ReleaseCheckState.Available,
    onUpdateNow: () -> Unit,
    onMaybeLater: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    val currentLabel = "v${AppUpdateChecker.normalizeVersion(currentVersion)}"
    val newTagClean = AppUpdateChecker.normalizeVersion(release.tag)
    val newLabel = if (newTagClean.firstOrNull()?.isDigit() == true) "v$newTagClean" else release.tag

    Dialog(
        onDismissRequest = onMaybeLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = pop.card,
            border = BorderStroke(1.dp, pop.track),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("update_available_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GetiyNavy)
                            .border(1.dp, GetiyMint.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = stringResource(R.string.update_available_title),
                            tint = GetiyMint,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_available_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = pop.text,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        )
                        if (release.name.isNotBlank() &&
                            !release.name.equals(release.tag, ignoreCase = true) &&
                            !release.name.equals(newLabel, ignoreCase = true)
                        ) {
                            Text(
                                text = release.name,
                                style = MaterialTheme.typography.bodySmall.copy(color = pop.muted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Version comparison pill bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pop.field)
                        .border(1.dp, pop.track, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = pop.muted,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = pop.muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(
                        modifier = Modifier
                            .clip(PopPillShape)
                            .background(GetiyMint.copy(alpha = 0.18f))
                            .border(1.dp, GetiyMint.copy(alpha = 0.45f), PopPillShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = newLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = pop.strong,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (release.notes.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = pop.muted,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pop.bg)
                            .border(1.dp, pop.track, RoundedCornerShape(14.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = release.notes,
                            style = MaterialTheme.typography.bodySmall.copy(color = pop.text),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.update_available_fallback_body, newLabel),
                        style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                    )
                }

                Spacer(Modifier.height(20.dp))

                PopButton(
                    label = stringResource(R.string.update_action_now),
                    icon = Icons.Rounded.Download,
                    useGradient = true,
                    height = 48.dp,
                    onClick = onUpdateNow,
                    tag = "update_dialog_update_now_button",
                )

                Spacer(Modifier.height(10.dp))

                Surface(
                    onClick = {
                        GetiyHaptics.selection()
                        onMaybeLater()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, pop.track),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("update_dialog_maybe_later_button"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_action_later),
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = pop.text,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.GetiyHaptics
import com.example.data.engine.GetiyEngine
import com.example.data.model.LibraryItem
import com.example.data.model.MediaHelpers
import com.example.ui.components.PopButton
import com.example.ui.components.PopTag
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.theme.PopError
import com.example.ui.theme.PopYellow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_RINGTONE_SECONDS = 40f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneBottomSheet(
    item: LibraryItem,
    engine: GetiyEngine,
    onMakeRingtone: suspend (String, Double, Double, String) -> Result<Uri>,
    onSetRingtone: (Uri) -> Boolean,
    onToast: (String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val pop = GetiyThemeTokens.pop
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var totalDuration by remember(item.path) { mutableStateOf<Float?>(null) }
    var range by remember(item.path) { mutableStateOf(0f..30f) }
    var busy by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.path) {
        val dur = engine.mediaDurationSeconds(item.path)?.toFloat() ?: 30f
        val safeDur = dur.coerceAtLeast(2f)
        totalDuration = safeDur
        range = 0f..safeDur.coerceAtMost(30f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pop.bg,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Make a ringtone",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = pop.text,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(color = pop.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))

            val dur = totalDuration
            if (dur == null) {
                CircularProgressIndicator(color = pop.strong)
            } else {
                val len = (range.endInclusive - range.start).coerceAtLeast(1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = MediaHelpers.clockLabel(range.start.toDouble()),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = pop.text,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    PopTag("${len.roundToInt()} s (max 40 s)")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = MediaHelpers.clockLabel(range.endInclusive.toDouble()),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = pop.text,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                RangeSlider(
                    value = range,
                    onValueChange = { next ->
                        var s = next.start.coerceIn(0f, dur)
                        var e = next.endInclusive.coerceIn(s, dur)
                        if (e - s > MAX_RINGTONE_SECONDS) {
                            if (s != range.start) {
                                e = (s + MAX_RINGTONE_SECONDS).coerceAtMost(dur)
                            } else {
                                s = (e - MAX_RINGTONE_SECONDS).coerceAtLeast(0f)
                            }
                        }
                        if (e - s < 1f) {
                            e = (s + 1f).coerceAtMost(dur)
                        }
                        range = s..e
                        savedUri = null
                    },
                    valueRange = 0f..dur,
                    colors = SliderDefaults.colors(
                        thumbColor = PopYellow,
                        activeTrackColor = pop.strong,
                        inactiveTrackColor = pop.track,
                    ),
                )
            }

            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall.copy(color = PopError),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))
            if (savedUri == null) {
                PopButton(
                    label = "Cut & save ringtone",
                    icon = Icons.Rounded.ContentCut,
                    useGradient = true,
                    busy = busy,
                    enabled = totalDuration != null,
                    onClick = {
                        busy = true
                        errorMsg = null
                        scope.launch {
                            val result = onMakeRingtone(
                                item.path,
                                range.start.toDouble(),
                                range.endInclusive.toDouble(),
                                item.title,
                            )
                            busy = false
                            result.onSuccess { uri ->
                                GetiyHaptics.success()
                                savedUri = uri
                                onToast("Ringtone saved", "Saved to Ringtones/Getiy", false)
                            }.onFailure { err ->
                                GetiyHaptics.error()
                                errorMsg = err.message ?: "Couldn't create ringtone"
                            }
                        }
                    },
                    tag = "ringtone_cut_button",
                )
            } else {
                PopButton(
                    label = "Set as default ringtone",
                    icon = Icons.Rounded.RingVolume,
                    useGradient = true,
                    onClick = {
                        val ok = onSetRingtone(savedUri!!)
                        if (ok) {
                            GetiyHaptics.success()
                            onToast("Ringtone set", item.title, false)
                            onDismiss()
                        } else {
                            onToast(
                                "Allow Getiy to change ringtone",
                                "Turn on \"Modify system settings\", then tap Set again.",
                                false,
                            )
                        }
                    },
                    tag = "ringtone_set_button",
                )
            }
        }
    }
}

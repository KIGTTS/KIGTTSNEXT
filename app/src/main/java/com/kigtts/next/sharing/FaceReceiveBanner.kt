package com.kigtts.next.sharing

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.delay

@Composable internal fun FaceReceiveBanner(offer: FaceOffer?, approve: (String, Boolean) -> Unit, reject: (String) -> Unit) {
    var displayed by remember { mutableStateOf<FaceOffer?>(null) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(offer?.id) {
        details = false
        if (offer != null) displayed = offer else { delay(220); displayed = null }
    }
    displayed?.let { shown ->
        Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = false)) {
            val visible = remember { MutableTransitionState(false) }.apply { targetState = offer != null }
            AnimatedVisibility(visible, enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 }) {
                Surface(onClick = feedbackClick { details = true }, shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp, shadowElevation = 8.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(12.dp).widthIn(max = 560.dp).fillMaxWidth().testTag("face-incoming-banner")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FacePeerAvatar(shown.profile.optString("avatar").takeIf(String::isNotBlank))
                            Column(Modifier.weight(1f)) {
                                Text("${shown.nickname} 想与你分享", style = MaterialTheme.typography.titleMedium)
                                Text(faceOfferSummary(shown), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(feedbackClick { reject(shown.id) }) { Text("拒绝") }
                            FilledTonalButton(feedbackClick { approve(shown.id, false) }) { Text("接收") }
                        }
                    }
                }
            }
        }
        if (details && offer != null) FaceReceivePrompt(shown, approve, reject)
    }
}

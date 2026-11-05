@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.sharing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.compactPlainIconColors
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.launch

/** Keep the sheet anchor stable while discovery, QR codes and transfer status change. */
@Composable
internal fun FaceShareSheet(title: String, dismiss: () -> Unit, maximumHeight: Dp = 720.dp,
    back: (() -> Unit)? = null, finish: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(dismiss, sheetState = state, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = back == null)) {
        BackHandler(back != null) { back?.invoke() }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val height = animateDpAsState(minOf(maximumHeight, maxHeight * .86f), tween(240), label = "face-sheet-height").value
            Column(Modifier.fillMaxWidth().height(height).navigationBarsPadding().testTag("face-share")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (back != null) ToolButton("返回分享", Icons.AutoMirrored.Filled.ArrowBack, back, colors = compactPlainIconColors())
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    ToolButton("关闭", Icons.Default.Close, { scope.launch { state.hide(); dismiss() } }, colors = compactPlainIconColors())
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = if (finish == null) 24.dp else 8.dp).testTag("face-share-content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                if (finish != null) TextButton(feedbackClick { scope.launch { state.hide(); finish() } },
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { Text("结束分享") }
            }
        }
    }
}

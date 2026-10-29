@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.compactPlainIconColors

/** Keep the sheet anchor stable while discovery, QR codes and transfer status change. */
@Composable
internal fun FaceShareSheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val height = minOf(720.dp, maxHeight * .86f)
            Column(Modifier.fillMaxWidth().height(height).navigationBarsPadding().testTag("face-share")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    ToolButton("关闭", Icons.Default.Close, dismiss, colors = compactPlainIconColors())
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp).testTag("face-share-content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.navigation.ToolbarAction

/** Window geometry, rather than device orientation, decides how much space the title may use. */
@Composable
fun CardPageScaffold(title: String, back: () -> Unit, modifier: Modifier = Modifier,
    backLabel: String = "返回上一页", actions: List<ToolbarAction> = emptyList(), content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The original scaffold paints underneath the status bar and consumes insets only for controls.
        PageScaffold(title, back, backLabel = backLabel, largeBar = maxWidth <= maxHeight, actions = actions,
            subtitle = "下滑进行拍照", content = content)
    }
}

package com.kigtts.next.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import com.kigtts.next.state.SidePage
import com.kigtts.next.ui.cards.CardsScreen
import com.kigtts.next.ui.sketch.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.navigation.ToolbarAction

@Composable
fun SecondarySidePage(page: SidePage, close: () -> Unit, sketch: SketchState, shareRequest: Int,
    shareConsumed: () -> Unit, shareSketch: () -> Unit, message: (String) -> Unit) {
    PageScaffold(page.label,close,backLabel="返回大字幕",largeBar=page!=SidePage.SKETCH,
        actions=if(page==SidePage.SKETCH)listOf(ToolbarAction("share-sketch","分享画板",Icons.Default.Share,click=shareSketch)) else emptyList()) {
            when (page) {
                SidePage.SKETCH -> SketchScreen(sketch, shareRequest, shareConsumed, message)
                SidePage.CARDS -> CardsScreen()
            }
    }
}

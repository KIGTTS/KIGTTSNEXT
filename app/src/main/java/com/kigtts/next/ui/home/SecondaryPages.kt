package com.kigtts.next.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.state.SidePage
import com.kigtts.next.ui.cards.CardHostScreen
import com.kigtts.next.ui.cards.CardPageScaffold
import com.kigtts.next.ui.sketch.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.navigation.ToolbarAction

@Composable
fun SecondarySidePage(page: SidePage, close: () -> Unit, sketch: SketchState, shareRequest: Int,
    shareConsumed: () -> Unit, shareSketch: () -> Unit, message: (String) -> Unit, model: AppViewModel) {
    if (page == SidePage.CARDS) {
        com.kigtts.next.ui.album.AlbumHost(model, close)
        return
    }
    if (page == SidePage.SKETCH) {
        SketchPageScaffold(close, shareSketch) { SketchScreen(sketch, shareRequest, shareConsumed, message) }
        return
    }
    PageScaffold(page.label,close,backLabel="返回大字幕",largeBar=page!=SidePage.SKETCH,
        actions=if(page==SidePage.SKETCH)listOf(ToolbarAction("share-sketch","分享画板",Icons.Default.Share,click=shareSketch)) else emptyList()) {
            when (page) {
                SidePage.SKETCH -> SketchScreen(sketch, shareRequest, shareConsumed, message)
                SidePage.CARDS -> Unit // Handled by the window-adaptive scaffold above.
            }
    }
}

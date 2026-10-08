@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.state.SidePage
import com.kigtts.next.ui.cards.CardsScreen
import com.kigtts.next.ui.sketch.*

@Composable
fun SecondarySidePage(page: SidePage, close: () -> Unit, sketch: SketchState, shareRequest: Int,
    shareConsumed: () -> Unit, shareSketch: () -> Unit, message: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(page.label) }, windowInsets = WindowInsets(0.dp),
            navigationIcon = { IconButton(onClick = feedbackClick(close)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大字幕") } },
            actions = { if (page == SidePage.SKETCH) IconButton(onClick = feedbackClick(shareSketch)) { Icon(Icons.Default.Share, "分享画板") } })
        Box(Modifier.weight(1f)) {
            when (page) {
                SidePage.SKETCH -> SketchScreen(sketch, shareRequest, shareConsumed, message)
                SidePage.CARDS -> CardsScreen()
            }
        }
    }
}
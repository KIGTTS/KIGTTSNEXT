@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

@Composable
fun HomePanels(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel, motion: HomeSheetMotion,
    modifier: Modifier = Modifier) {
    val hasContent by remember(motion) { derivedStateOf { motion.panel != null && motion.height > 80f } }
    val background = animateFloatAsState(if (state.panel != null) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "sheet-background")
    val backdropVisible by remember(background) { derivedStateOf { background.value > 0f } }
    val shape = homeCardShape(CornerNeighbors(above = motion.panel != null))
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        .heightFrom { motion.height }.testTag("home-sheet").homePreviewRegion("phrases")) {
        Box(Modifier.matchParentSize().testTag("sheet-background")) {
            if (state.panel != null || backdropVisible) {
                com.kigtts.next.theme.DrawerBackdrop(state.theme, Modifier.matchParentSize()
                    .drawSheet(shape) { background.value })
            }
        }
        Column(if (hasContent) Modifier.drawSheet(shape) else Modifier) {
            SheetChrome(state, model, motion, model::editQuickTexts)
            if (hasContent) QuickTextPanel(state, phrases, categories, model, motion, Modifier.weight(1f))
        }
    }
}

// Android 10 can keep stale outline bounds on the hardware layer while the sheet grows.
private fun Modifier.drawSheet(shape: Shape, opacity: () -> Float = { 1f }) = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val bounds = Rect(0f, 0f, size.width, size.height)
    val paint = Paint()
    onDrawWithContent {
        val alpha = opacity().coerceIn(0f, 1f)
        if (alpha > 0f) clipPath(path) {
            if (alpha == 1f) this@onDrawWithContent.drawContent()
            else {
                paint.alpha = alpha
                drawContext.canvas.withSaveLayer(bounds, paint) { this@onDrawWithContent.drawContent() }
            }
        }
    }
}

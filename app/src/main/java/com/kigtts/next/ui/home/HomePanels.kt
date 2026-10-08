@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    Surface(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        .heightFrom { motion.height }.testTag("home-sheet"),
        color = Color.Transparent,
        shape = homeCardShape(CornerNeighbors(above = motion.panel != null))) {
        Box {
            Box(Modifier.matchParentSize().testTag("sheet-background").graphicsLayer { alpha = background.value }
                .background(MaterialTheme.colorScheme.surfaceContainer))
            Column {
                SheetChrome(state, model, motion, model::editQuickTexts)
                if (hasContent) QuickTextPanel(state, phrases, categories, model, motion, Modifier.weight(1f))
            }
        }
    }
}

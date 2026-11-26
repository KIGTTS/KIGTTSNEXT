package com.kigtts.next.ui.plugins

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ReadablePage
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.LocalPageSettled
import com.kigtts.next.ui.navigation.pageContentPadding
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.*
import java.io.File

internal val LocalProgressiveMarketContent = staticCompositionLocalOf { false }

/** Retained pages keep their mounted content and scroll state on return. */
@Composable
internal fun rememberMarketContentReady(pageId: String, enabled: Boolean): Boolean {
    if (!enabled) return true
    var ready by rememberSaveable(pageId) { mutableStateOf(false) }
    if (!ready) {
        val settled = LocalPageSettled.current
        LaunchedEffect(settled) {
            if (settled) {
                withFrameNanos { }
                ready = true
            }
        }
    }
    return ready
}

@Composable
internal fun MarketPagePlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("market-page-placeholder"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (width in listOf(1f, .82f, .58f)) {
            Box(Modifier.fillMaxWidth(width).height(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
        }
    }
}

@Composable
internal fun MarketCardPlaceholder(height: Dp, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().height(height), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        MarketPagePlaceholder(Modifier.padding(16.dp))
    }
}

@Composable
internal fun MarketContentReveal(ready: Boolean, modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit, content: @Composable () -> Unit) {
    val enteredWhileLoading = remember { !ready }
    val reveal = animateFloatAsState(if (ready) 1f else 0f, tween(140), label = "market-content-reveal")
    val fading by remember(reveal) { derivedStateOf { reveal.value < 1f } }
    Box(modifier) {
        if (ready) {
            Box(if (enteredWhileLoading) Modifier.graphicsLayer {
                alpha = reveal.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            } else Modifier) { content() }
            if (enteredWhileLoading && fading) Box(Modifier.matchParentSize().graphicsLayer {
                alpha = 1f - reveal.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }.clearAndSetSemantics { }) { placeholder() }
        }
        else placeholder()
    }
}

@Composable
internal fun MarketArticle(page: PluginPage, blocks: List<PageBlock>, ready: Boolean, busy: Boolean,
    resolve: (PluginResource) -> File?, invoke: (String, Map<String, String>) -> Unit) {
    val active = LocalPageActive.current
    ReadablePage(840.dp) {
        LazyColumn(Modifier.fillMaxSize().testTag("market-article"), contentPadding = pageContentPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (page.header != null) item("market-header") {
                PluginPageHeader(page.header, resolve, page.headerAction, !busy && active, invoke, loadArtwork = ready)
            }
            items(blocks, contentType = { it::class }) { block ->
                val height = when (block) {
                    is PageBlock.Group -> if (block.children.any { it is PageBlock.Gallery }) 324.dp else 148.dp
                    is PageBlock.Gallery -> 216.dp
                    else -> 92.dp
                }
                MarketContentReveal(ready, Modifier.fillMaxWidth().animateContentSize(tween(200)),
                    placeholder = { MarketCardPlaceholder(height) }) {
                    RenderBlock(block, true, { action, inputs -> if (!busy && active) invoke(action, inputs) },
                        SettingPosition.SINGLE, resolve)
                }
            }
        }
    }
}

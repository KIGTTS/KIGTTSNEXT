package com.kigtts.next.ui.cards

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import com.kigtts.plugin.api.PolaroidAlbum
import com.kigtts.plugin.api.PolaroidCard

internal data class AlbumBrowseSnapshot(val cards: List<PolaroidCard>, val gridCards: List<PolaroidCard>,
    val focusedId: String?, val selectedAlbumId: String?, val positionRequest: Long)

private data class AlbumPane(val request: Long, val albumId: String?, val direction: Int)

private class AlbumPaneSnapshots(request: Long, initial: AlbumBrowseSnapshot) {
    var pane = AlbumPane(request, initial.selectedAlbumId, 1)
    var current = initial
    val retained = linkedMapOf(pane to initial)
    fun accept(request: Long, next: AlbumBrowseSnapshot, albums: List<PolaroidAlbum>): AlbumPane {
        if (pane.request != request) {
            retained[pane] = current
            val old = albums.indexOfFirst { it.id == current.selectedAlbumId }
            val target = albums.indexOfFirst { it.id == next.selectedAlbumId }
            pane = AlbumPane(request, next.selectedAlbumId, if (target >= old) 1 else -1)
        }
        current = next
        retained[pane] = next
        return pane
    }
}

/** Only an explicit album selection replaces the pane; ordinary pager focus keeps its instance. */
@Composable
internal fun PolaroidAlbumTransition(request: Long, snapshot: AlbumBrowseSnapshot, albums: List<PolaroidAlbum>,
    content: @Composable (AlbumBrowseSnapshot, current: Boolean) -> Unit) {
    val memory = remember { AlbumPaneSnapshots(request, snapshot) }
    val pane = memory.accept(request, snapshot, albums)
    val motion = updateTransition(pane, label = "polaroid-album-change")
    SideEffect {
        if (!motion.isRunning && motion.currentState == motion.targetState)
            memory.retained.keys.retainAll(setOf(pane))
    }
    motion.AnimatedContent(Modifier.fillMaxSize().polaroidAlbumDrawingClip(), contentKey = { it.request }, transitionSpec = {
        val direction = targetState.direction
        (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it * direction } + fadeIn(tween(220)))
            .togetherWith(slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it * direction } + fadeOut(tween(200)))
            .using(null)
    }) { target ->
        val current = target == pane
        val scene = if (current) snapshot else memory.retained[target] ?: snapshot
        val tag = "polaroid-album-pane/" + target.request
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().then(if (current) Modifier.testTag(tag)
            else Modifier.clearAndSetSemantics { testTag = tag })) { content(scene, current) }
    }
}

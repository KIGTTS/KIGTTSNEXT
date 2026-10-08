@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.collectLatest

/** Keep the floating card until it reaches its final slot; reveal the list in the same frame. */
@Composable
fun rememberPhraseDrop(drag: ReorderDrag): State<Offset> {
    val settling = drag.settling
    val animation = remember(drag.key, settling) { Animatable(drag.floatingBounds.topLeft, Offset.VectorConverter) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
    LaunchedEffect(drag.key, drag.settling) {
        val id = drag.key ?: return@LaunchedEffect
        if (drag.settling) {
            // Let the last reorder and any category/viewport update finish laying out.
            withFrameNanos { }
            snapshotFlow { drag.targetBounds(id)?.topLeft ?: drag.floatingBounds.topLeft }.collectLatest { destination ->
                animation.animateTo(destination, spatial)
                drag.completeDrop(id)
            }
        }
    }
    // Do not switch the drawing layer to a previous animation before the release is composed.
    return remember(drag, animation, settling) { derivedStateOf { if (settling) animation.value else drag.floatingBounds.topLeft } }
}

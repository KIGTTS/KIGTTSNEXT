@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components.reorder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.collectLatest

/** Keep the floating card until it reaches its final slot; reveal the list in the same frame. */
@Composable
fun rememberReorderDrop(drag: ReorderDrag): State<Offset> {
    val settling = drag.settling
    val animation = remember(drag.key, settling) { Animatable(drag.floatingBounds.topLeft, Offset.VectorConverter) }
    // A deterministic drop tween avoids a spring overshoot that looks like a second
    // vibration and briefly reopens the adjacent rounded corners on release.
    val spatial = tween<Offset>(180)
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

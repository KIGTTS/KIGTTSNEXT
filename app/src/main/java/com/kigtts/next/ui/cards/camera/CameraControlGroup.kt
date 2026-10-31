@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.excludeCameraPull

internal val LocalCameraControlShape = staticCompositionLocalOf<RoundedCornerShape?> { null }

/** The outer corners form one capsule; adjacent corners remain connected in either orientation. */
@Composable
internal fun BoxScope.CameraControlGroup(vertical: Boolean, visible: List<Boolean>,
    item: @Composable (Int) -> Unit) {
    val first = visible.indexOfFirst { it }; val last = visible.indexOfLast { it }
    val content: @Composable () -> Unit = {
        repeat(visible.size) { index ->
            val start by animateDpAsState(if (index == first) 22.dp else 4.dp, tween(220), label = "camera-group-start")
            val end by animateDpAsState(if (index == last) 22.dp else 4.dp, tween(220), label = "camera-group-end")
            val gap by animateDpAsState(if (index > first && first >= 0) 2.dp else 0.dp, tween(220), label = "camera-group-gap")
            val shape = if (vertical) RoundedCornerShape(
                topStart = start, topEnd = start, bottomStart = end, bottomEnd = end)
            else RoundedCornerShape(
                topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
            AnimatedVisibility(visible[index],
                enter = fadeIn(tween(180)) + if (vertical) expandVertically(tween(220), expandFrom = Alignment.Top)
                    else expandHorizontally(tween(220), expandFrom = Alignment.Start),
                exit = fadeOut(tween(160)) + if (vertical) shrinkVertically(tween(220), shrinkTowards = Alignment.Top)
                    else shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start)) {
                Box(if (vertical) Modifier.padding(top = gap) else Modifier.padding(start = gap)) {
                    CompositionLocalProvider(LocalCameraControlShape provides shape,
                        LocalMinimumInteractiveComponentSize provides 44.dp) { item(index) }
                }
            }
        }
    }
    val modifier = Modifier.align(if (vertical) Alignment.BottomStart else Alignment.TopEnd)
        .padding(8.dp).testTag("camera-switch-strip").excludeCameraPull()
    if (vertical) Column(modifier) { content() }
    else Row(modifier) { content() }
}

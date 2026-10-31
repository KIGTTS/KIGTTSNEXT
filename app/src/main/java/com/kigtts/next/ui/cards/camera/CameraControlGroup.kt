@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.excludeCameraPull

internal val LocalCameraControlShape = staticCompositionLocalOf<RoundedCornerShape?> { null }

/** The outer corners form one capsule; adjacent corners remain connected in either orientation. */
@Composable
internal fun BoxScope.CameraControlGroup(vertical: Boolean, count: Int,
    item: @Composable (Int) -> Unit) {
    val content: @Composable () -> Unit = {
        repeat(count) { index ->
            val first = index == 0; val last = index == count - 1
            val small = 4.dp; val large = 22.dp
            val shape = if (vertical) RoundedCornerShape(
                topStart = if (first) large else small, topEnd = if (first) large else small,
                bottomStart = if (last) large else small, bottomEnd = if (last) large else small)
            else RoundedCornerShape(
                topStart = if (first) large else small, bottomStart = if (first) large else small,
                topEnd = if (last) large else small, bottomEnd = if (last) large else small)
            CompositionLocalProvider(LocalCameraControlShape provides shape,
                LocalMinimumInteractiveComponentSize provides 44.dp) { item(index) }
        }
    }
    val modifier = Modifier.align(if (vertical) Alignment.TopStart else Alignment.TopEnd)
        .padding(8.dp).testTag("camera-switch-strip").excludeCameraPull()
    if (vertical) Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    else Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

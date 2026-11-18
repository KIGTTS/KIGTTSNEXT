@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.components.reorder.*

/** The same chips stay on screen while edit affordances appear and tags change positions. */
@Composable internal fun ProfileEditableTags(tags: List<String>, editing: Boolean,
    change: (List<String>) -> Unit, add: () -> Unit) {
    val latestTags by rememberUpdatedState(tags)
    val latestChange by rememberUpdatedState(change)
    val latestEditing by rememberUpdatedState(editing)
    val bounds = remember { mutableMapOf<String, LayoutCoordinates>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val feedback = rememberDragFeedback()
    val drag = rememberReorderDrag({ bounds.filter { it.key in latestTags && it.value.isAttached }.map { (id, c) ->
        ReorderTarget(id, Rect(c.positionInRoot() - origin, Size(c.size.width.toFloat(), c.size.height.toFloat())))
    } }, animateDrop = true, feedback = feedback) { from, to ->
        val next = latestTags.toMutableList(); val a = next.indexOf(from); val b = next.indexOf(to)
        if (a >= 0 && b >= 0 && a != b) { next.add(b, next.removeAt(a)); latestChange(next); feedback.step() }
    }
    val drop = rememberReorderDrop(drag)
    val edit = updateTransition(editing, label = "tag-edit")
    val editFraction by edit.animateFloat({ tween(220) }, label = "tag-edit-fraction") { if (it) 1f else 0f }
    val editPadding = 8.dp * editFraction
    val changingMode = edit.currentState != edit.targetState
    DisposableEffect(editing) { onDispose { drag.finish(cancelled = true); drag.key?.let(drag::completeDrop) } }
    LookaheadScope {
        val lookahead = this
        FlowRow(Modifier.fillMaxWidth().padding(vertical = editPadding).testTag("profile-tags")
            .onGloballyPositioned { origin = it.positionInRoot() }.reorderGestures(drag, canStart = { latestEditing }),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag -> key(tag) {
                DisposableEffect(tag) { onDispose { bounds.remove(tag) } }
                Box(Modifier.align(Alignment.CenterVertically).zIndex(if (drag.key == tag) 1f else 0f)
                    .then(if (changingMode) Modifier else Modifier.animateBounds(lookahead,
                        boundsTransform = { _, _ -> tween(if (drag.key == tag) 0 else 220) }))
                    .onGloballyPositioned { bounds[tag] = it }.testTag("profile-tag/$tag")) {
                    Box(Modifier.reorderTranslation(tag, drag, drop).padding(end = editPadding).semantics {
                        if (editing) customActions = listOf(-1 to "前移", 1 to "后移").map { (step, label) ->
                            CustomAccessibilityAction(label) {
                                val next = latestTags.toMutableList(); val from = next.indexOf(tag); val to = from + step
                                if (from >= 0 && to in next.indices) { next.add(to, next.removeAt(from)); latestChange(next) }; true
                            }
                        }
                    }) {
                        Surface(Modifier.heightIn(min = 36.dp).testTag("profile-tag-body/$tag"),
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = if (drag.key == tag) 4.dp else 0.dp) {
                            Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        if (editing || editFraction > 0f) Box(Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp)
                            .graphicsLayer { alpha = editFraction; scaleX = .7f + .3f * editFraction; scaleY = scaleX }) {
                            IconButton(feedbackClick { latestChange(latestTags - tag) }, Modifier.size(24.dp).testTag("profile-tag-remove/$tag"), enabled = editing) {
                                Icon(Icons.Default.Cancel, "删除标签：$tag", Modifier.size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
                            }
                        }
                    }
                }
            } }
            if (editing || editFraction > 0f) Box(Modifier.align(Alignment.CenterVertically).clipToBounds()
                .layout { measurable, constraints ->
                    val child = measurable.measure(constraints)
                    layout((child.width * editFraction).toInt(), child.height) { child.placeRelative(0, 0) }
                }.graphicsLayer { alpha = editFraction }) {
                OutlinedButton(feedbackClick(add), Modifier.height(36.dp).testTag("profile-add-tag"), enabled = editing,
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(uiText("profile.tags"))
                }
            }
        }
    }
}

package com.kigtts.next.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private class MotionEntry<T>(val id:String,initial:T) {
    var value by mutableStateOf(initial)
    val visible=MutableTransitionState(false)
}

/** Keep removed content only for its exit, with actions disabled immediately. Updates keep owners. */
@Composable
internal fun <T> MotionColumn(items:List<T>,identity:(T,Int)->String,modifier:Modifier=Modifier,enabled:Boolean=true,
    spacing:androidx.compose.ui.unit.Dp=12.dp,content:@Composable (T,Boolean)->Unit) {
    val retained=remember { mutableStateListOf<MotionEntry<T>>() }
    val occurrences=mutableMapOf<String,Int>()
    val desired=items.mapIndexed { index,value->val base=identity(value,index);val occurrence=occurrences.getOrDefault(base,0)
        occurrences[base]=occurrence+1;"$base/$occurrence" to value }
    val current=desired.toMap()
    SideEffect {
        desired.forEachIndexed { index,(id,value) ->
            val entry=retained.firstOrNull { it.id==id } ?: MotionEntry(id,value).also { retained.add(index.coerceAtMost(retained.size),it) }
            entry.value=value;entry.visible.targetState=true
        }
        val ids=desired.map { it.first }.toSet()
        retained.filter { it.id !in ids }.forEach { it.visible.targetState=false }
        val ordered=desired.mapNotNull { (id,_)->retained.firstOrNull { it.id==id } }.toMutableList()
        retained.filter { it.id !in ids }.forEach { outgoing->
            val anchor=retained.drop(retained.indexOf(outgoing)+1).firstOrNull { it.id in ids }
            val position=anchor?.let { ordered.indexOf(it) }?.takeIf { it>=0 } ?: ordered.size
            ordered.add(position,outgoing)
        }
        if(retained.map { it.id }!=ordered.map { it.id }) { retained.clear();retained.addAll(ordered) }
    }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(spacing)) {
        retained.forEach { entry->key(entry.id) {
            AnimatedVisibility(entry.visible,enter=fadeIn(tween(160))+expandVertically(tween(220)),
                exit=fadeOut(tween(100))+shrinkVertically(tween(200))) {
                Column(verticalArrangement=Arrangement.spacedBy(spacing)) {
                    content(current[entry.id] ?: entry.value,enabled&&entry.id in current)
                }
            }
            LaunchedEffect(entry.visible.isIdle,entry.visible.currentState,entry.visible.targetState) {
                if(entry.visible.isIdle&&!entry.visible.currentState&&!entry.visible.targetState)retained.remove(entry)
            }
        } }
    }
}

package com.kigtts.next.ui.components

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.*

/** Own pointer press pairs before a scrolling container's delayed clickable indication. */
class ImmediateButtonPress(private val source: MutableInteractionSource, private val scope: CoroutineScope) {
    private var owner: PressInteraction.Press? = null
    private var pulse: Job? = null
    private var release: Job? = null
    val native = object : MutableInteractionSource {
        override val interactions get() = source.interactions
        override suspend fun emit(interaction: Interaction) { if (!interaction.isPress()) source.emit(interaction) }
        override fun tryEmit(interaction: Interaction) = interaction.isPress() || source.tryEmit(interaction)
    }
    private fun Interaction.isPress() = this is PressInteraction.Press || this is PressInteraction.Release || this is PressInteraction.Cancel
    fun down(position: Offset) {
        cancel();val press=PressInteraction.Press(position);owner=press
        scope.launch(start=CoroutineStart.UNDISPATCHED) { source.emit(press) }
        pulse=scope.launch {
            val start=withFrameNanos { it }
            while(withFrameNanos { it }-start<75_000_000L) { }
        }
    }
    fun up() {
        val press=owner ?: return;val minimum=pulse
        release?.cancel();release=scope.launch {
            minimum?.join()
            if(owner===press) { owner=null;source.emit(PressInteraction.Release(press)) }
        }
    }
    fun click() { if(owner==null) { down(Offset.Unspecified);up() } }
    fun cancel() {
        release?.cancel();pulse?.cancel()
        owner?.let { source.tryEmit(PressInteraction.Cancel(it)) };owner=null
    }
}

@Composable
fun rememberImmediateButtonPress(source: MutableInteractionSource): ImmediateButtonPress {
    val scope=rememberCoroutineScope()
    val press=remember(source,scope) { ImmediateButtonPress(source,scope) }
    DisposableEffect(press) { onDispose { press.cancel() } }
    return press
}

fun Modifier.immediateButtonPress(press: ImmediateButtonPress) = pointerInput(press) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=true,pass=PointerEventPass.Initial)
        press.down(down.position);var canceled=false;var released=false
        try {
            do {
                val change=awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id==down.id } ?: break
                if(change.pressed && change.previousPressed && change.position!=change.previousPosition && change.isConsumed) { canceled=true;press.cancel() }
                if(!change.pressed)released=true
            } while(change.pressed)
        } finally { if(released&&!canceled)press.up() else press.cancel() }
    }
}

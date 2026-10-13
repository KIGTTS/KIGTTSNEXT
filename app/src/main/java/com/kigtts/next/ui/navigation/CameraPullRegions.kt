package com.kigtts.next.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

internal class CameraPullRegion(val control: Boolean, val active: State<Boolean>) {
    var coordinates: LayoutCoordinates? = null
}

/** Opt in only the native preview; Compose sliders and exposure controls are explicit exclusions. */
@Composable
internal fun Modifier.cameraPullViewfinder(): Modifier = cameraPullRegion(control = false)

@Composable
internal fun Modifier.excludeCameraPull(): Modifier = cameraPullRegion(control = true)

@Composable
private fun Modifier.cameraPullRegion(control: Boolean): Modifier {
    val host = LocalCameraPullState.current ?: return this
    val active = rememberUpdatedState(LocalPageActive.current)
    val region = remember(host, control) { CameraPullRegion(control, active) }
    DisposableEffect(host, region) { host.addRegion(region); onDispose { host.removeRegion(region) } }
    return onGloballyPositioned { region.coordinates = it }
}

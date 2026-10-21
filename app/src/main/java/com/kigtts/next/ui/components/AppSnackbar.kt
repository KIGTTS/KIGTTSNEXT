package com.kigtts.next.ui.components

import android.content.res.Configuration
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.kigtts.next.ui.navigation.LocalPageActive
import kotlin.math.roundToInt

@Stable
internal class SnackbarAvoidance {
    val anchors = mutableStateMapOf<Any, Float>()
}
internal val LocalSnackbarAvoidance = staticCompositionLocalOf<SnackbarAvoidance?> { null }

/** Only visible foreground portrait controls reserve space; retained parent pages do not. */
@Composable
internal fun Modifier.snackbarAnchor(visible: Boolean = true): Modifier {
    val host = LocalSnackbarAvoidance.current ?: return this
    val key = remember { Any() }
    val enabled = visible && LocalPageActive.current && LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    DisposableEffect(host, key, enabled) {
        if (!enabled) host.anchors.remove(key)
        onDispose { host.anchors.remove(key) }
    }
    return if (!enabled) this else onGloballyPositioned {
        if (it.isAttached) host.anchors[key] = it.positionInWindow().y
    }
}

internal fun snackbarClearance(height: Int, anchor: Float?, minimum: Int, gap: Int): Int =
    maxOf(minimum, if (anchor == null) 0 else (height - anchor + gap).roundToInt()).coerceIn(0, height)

@Composable
internal fun AppSnackbarHost(state: SnackbarHostState, avoidance: SnackbarAvoidance) {
    val view = LocalView.current
    val density = LocalDensity.current
    val safe = WindowInsets.safeDrawing
    val ime = WindowInsets.ime
    var windowHeight by remember(view) { mutableIntStateOf(view.height) }
    DisposableEffect(view) {
        val listener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ -> windowHeight = bottom - top }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    val target = snackbarClearance(windowHeight, avoidance.anchors.values.minOrNull(),
        maxOf(safe.getBottom(density), ime.getBottom(density)), with(density) { 8.dp.roundToPx() })
    val animatedClearance by animateIntAsState(target,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "snackbar-avoidance")
    SnackbarHost(state, Modifier.fillMaxWidth().windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal)).testTag("snackbar-host").layout { measurable, constraints ->
        val clearance = animatedClearance.coerceIn(0, constraints.maxHeight)
        val child = measurable.measure(constraints.offset(vertical = -clearance))
        layout(child.width, child.height + clearance) { child.place(0, 0) }
    }) { data -> Snackbar(data, Modifier.testTag("app-snackbar"), shape=MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) }
}

package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.identity.rememberAvatarShape
import com.kigtts.next.ui.navigation.PageScaffold

@Stable internal class AboutFlowers(initial: List<String> = randomAboutShapes(count = 6), turns: List<Int> = List(6) { 0 }) {
    var shapes by mutableStateOf(initial); private set
    var turns by mutableStateOf(turns); private set
    fun change(index: Int) {
        shapes = changeAboutShape(shapes, index)
        turns = turns.mapIndexed { i, turn -> if (i == index) turn + 1 else turn }
    }
}

/** Decorations belong to the window backdrop, including the transparent large toolbar. */
@Composable internal fun AboutPage(back: () -> Unit, openLink: (String) -> Unit,
    openDocument: (com.kigtts.next.state.SettingsCategory) -> Unit, settings: ThemeSettings? = null, version: String? = null,
    reducedMotion: Boolean = false) {
    val copy = rememberUiCopy()
    val flowers = rememberSaveable(saver = listSaver<AboutFlowers, String>(
        save = { it.shapes.mapIndexed { i, shape -> "$shape:${it.turns[i]}" } },
        restore = { AboutFlowers(it.map { s -> s.substringBeforeLast(':') }, it.map { s -> s.substringAfterLast(':').toInt() }) }
    )) { AboutFlowers() }
    val taps = flowers.shapes.indices.map { index -> feedbackClick { flowers.change(index) } }
    PageScaffold(copy("settings.category.about"), back, Modifier.aboutFlowerTaps { point, size ->
        aboutFlowerBounds(size).indexOfLast { it.contains(point) }.takeIf { it >= 0 }?.let { taps[it]() }
    }, extendToBottom = true, backgroundSettings = settings,
        backgroundDecoration = { AboutBackdrop(flowers, taps, Modifier.matchParentSize()) }) {
        AboutScreen(openLink, openDocument, version, reducedMotion)
    }
}

internal fun aboutFlowerBounds(size: Size): List<Rect> {
    val w = size.width; val h = size.height
    return listOf(Triple(-.22f, .06f, .48f), Triple(.78f, -.05f, .60f), Triple(-.31f, .40f, .61f),
        Triple(.86f, .47f, .50f), Triple(-.15f, .84f, .56f), Triple(.72f, .90f, .62f)).map { (x, y, edge) ->
        Rect(Offset(x * w, y * h), Size(edge * w, edge * w))
    }
}

/** Observe unclaimed taps, so shapes never steal a link tap, Back, or a vertical scroll. */
private fun Modifier.aboutFlowerTaps(tap: (Offset, Size) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!down.isConsumed) {
            val up = waitForUpOrCancellation()
            if (up != null && !up.isConsumed && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop)
                tap(up.position, Size(size.width.toFloat(), size.height.toFloat()))
        }
    }
}

@Composable private fun AboutBackdrop(flowers: AboutFlowers, taps: List<() -> Unit>, modifier: Modifier) {
    val copy = rememberUiCopy()
    val colors = MaterialTheme.colorScheme
    val tints = listOf(colors.primaryContainer.copy(alpha = .65f), colors.tertiaryContainer.copy(alpha = .60f),
        colors.secondaryContainer.copy(alpha = .65f), colors.primary.copy(alpha = .12f),
        colors.tertiary.copy(alpha = .12f), colors.secondaryContainer.copy(alpha = .65f))
    BoxWithConstraints(modifier.testTag("about-backdrop")) {
        val positions = aboutFlowerBounds(Size(maxWidth.value, maxHeight.value))
        positions.forEachIndexed { index, bounds ->
            val rotation by animateFloatAsState(flowers.turns[index] * 90f, tween(460, easing = FastOutSlowInEasing), label = "about-flower-$index")
            val shape = rememberAvatarShape(flowers.shapes[index])
            Box(Modifier.offset(bounds.left.dp, bounds.top.dp).size(bounds.width.dp).testTag("about-flower/$index")
                .semantics {
                    contentDescription = copy("about.flower", "number" to (index + 1).toString())
                    stateDescription = "${flowers.shapes[index]}:${flowers.turns[index]}"
                    role = Role.Button
                    onClick(copy("about.flower_shuffle")) { taps[index](); true }
                }.graphicsLayer { rotationZ = rotation + index * 18f }.clip(shape).background(tints[index]))
        }
    }
}

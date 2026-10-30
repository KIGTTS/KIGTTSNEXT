package com.kigtts.next.ui.settings

import com.kigtts.next.identity.AvatarFrames
import kotlin.random.Random

/** Every Material silhouette is eligible, without repeating a visible neighbor. */
internal fun randomAboutShapes(previous: List<String> = emptyList(), random: Random = Random.Default, count: Int = 4): List<String> {
    val pool = AvatarFrames.names.keys.shuffled(random)
    val selected = mutableListOf<String>()
    repeat(count) { index -> selected += pool.first { it != previous.getOrNull(index) && it !in selected } }
    return selected
}

internal fun changeAboutShape(shapes: List<String>, index: Int, random: Random = Random.Default): List<String> =
    shapes.mapIndexed { i, current -> if (i == index) AvatarFrames.names.keys.filterNot { it in shapes }.random(random) else current }

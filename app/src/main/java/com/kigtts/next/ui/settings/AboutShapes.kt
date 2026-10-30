package com.kigtts.next.ui.settings

import com.kigtts.next.identity.AvatarFrames
import kotlin.random.Random

/** Every Material silhouette is eligible; each tap changes all four without duplicates. */
internal fun randomAboutShapes(previous: List<String> = emptyList(), random: Random = Random.Default): List<String> {
    val pool = AvatarFrames.names.keys.shuffled(random)
    val selected = mutableListOf<String>()
    repeat(4) { index -> selected += pool.first { it != previous.getOrNull(index) && it !in selected } }
    return selected
}

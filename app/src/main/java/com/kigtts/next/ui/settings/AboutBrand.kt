package com.kigtts.next.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.identity.rememberAvatarShape

/** Separate silhouettes crop into the edges of the full-bleed brand illustration. */
@Composable internal fun AboutBrand(modifier: Modifier = Modifier) {
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(turns * 90f, tween(460, easing = FastOutSlowInEasing), label = "about-flower-turn")
    val shapes = listOf("flower", "clover8", "cookie6", "soft-burst", "sun", "cookie9")
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(max = 280.dp).aspectRatio(1.5f)
        .testTag("about-easter-egg").clickable(interactionSource = remember { MutableInteractionSource() },
            indication = null, role = Role.Button, onClickLabel = "换个花样", onClick = feedbackClick { turns++ })) {
        val unit = minOf(maxWidth / 320, maxHeight / (320f / 1.5f))
        val positions = listOf(
            Triple(unit * -22, unit * -10, unit * 72),
            Triple(maxWidth - unit * 68, unit * -56, unit * 142),
            Triple(unit * -115, maxHeight - unit * 84, unit * 190),
            Triple(maxWidth - unit * 39, maxHeight - unit * 78, unit * 90))
        val tints = listOf(colors.primaryContainer.copy(alpha = .65f), colors.tertiaryContainer.copy(alpha = .55f),
            colors.secondaryContainer.copy(alpha = .65f), colors.primary.copy(alpha = .16f))
        positions.forEachIndexed { index, (x, y, edge) ->
            val shape = rememberAvatarShape(shapes[(turns + index) % shapes.size])
            Box(Modifier.offset(x, y).size(edge)
                .testTag("about-flower/$index").clearAndSetSemantics {}
                .graphicsLayer { rotationZ = rotation + index * 18f }.clip(shape).background(tints[index]))
        }
        Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS 横版字标",
            Modifier.align(Alignment.Center).width(unit * 204).height(unit * 51).testTag("about-logo"), tint = colors.onSurface)
    }
}

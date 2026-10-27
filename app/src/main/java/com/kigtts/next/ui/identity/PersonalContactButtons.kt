package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.ui.components.*

/** Connected contact shortcuts keep the portrait readable and reserve overflow after five links. */
@Composable internal fun PersonalContactButtons(contacts: List<ContactLink>, interactive: Boolean,
    open: (ContactLink) -> Unit, modifier: Modifier = Modifier) {
    var more by remember(contacts) { mutableStateOf(false) }
    val count = contacts.size.coerceAtMost(6)
    LazyRow(modifier.testTag("personal-card-contacts"), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(contacts.take(5)) { index, link ->
            ContactButton("联系方式：" + link.label.ifBlank { "链接" }, "personal-contact-$index", interactive,
                { open(link) }, index, count) { SocialContactIcon(link, Modifier.fillMaxSize()) }
        }
        if (contacts.size > 5) item("more") {
            Box {
                ContactButton("更多联系方式", "personal-contact-more", interactive, { more = true }, 5, count) {
                    Icon(Icons.Default.MoreHoriz, null, Modifier.fillMaxSize())
                }
                DropdownMenu(more && interactive, { more = false }) {
                    contacts.drop(5).forEachIndexed { index, link ->
                        DropdownMenuItem(text = { Text(link.label.ifBlank { "链接" }) },
                            leadingIcon = { SocialContactIcon(link, Modifier.size(22.dp)) },
                            modifier = Modifier.testTag("personal-contact-overflow-${index + 5}"),
                            onClick = feedbackClick { more = false; open(link) })
                    }
                }
            }
        }
    }
}

@Composable private fun ContactButton(label: String, tag: String, interactive: Boolean,
    click: () -> Unit, index: Int, count: Int, glyph: @Composable () -> Unit) {
    val shape = connectedShape(CornerNeighbors(start = index > 0, end = index < count - 1), outer = 20.dp)
    val backdrop = Modifier.size(40.dp).clip(shape).then(Modifier.background(Color.Black.copy(alpha = .36f))).testTag(tag)
    val content: @Composable () -> Unit = {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Color.Black.copy(alpha = .75f)) {
                Box(Modifier.matchParentSize().offset(y = 1.dp).blur(2.dp, BlurredEdgeTreatment.Unbounded)) { glyph() }
            }
            CompositionLocalProvider(LocalContentColor provides Color.White) { glyph() }
        }
    }
    if (interactive) IconActionHint(label) {
        IconButton(feedbackClick(click), backdrop.iconLongAction(true, null).semantics { contentDescription = label }, content = content)
    } else Box(backdrop, contentAlignment = Alignment.Center) { content() }
}

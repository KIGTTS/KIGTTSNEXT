package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun ProfileTileContactField(social: ContactLink?, reference: String, contacts: List<ContactLink>,
    changeSocial: (ContactLink) -> Unit, selectContact: (ContactLink) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var platform by remember { mutableStateOf(SocialPlatform.LINK) }
    var draft by remember { mutableStateOf<ContactLink?>(null) }
    val selected = social ?: contacts.firstOrNull { it.value == reference }
    selected?.let { link ->
        OutlinedButton(feedbackClick { draft = link; platform = socialPlatform(link); editing = true }, Modifier.fillMaxWidth()) {
            SocialContactIcon(link, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(link.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(link.value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    FilledTonalButton(feedbackClick { choosing = true }, Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (selected == null) "选择社媒平台" else "更换社媒平台")
    }
    if (contacts.isNotEmpty()) {
        Text(uiText("profile.saved_contacts"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        contacts.forEach { link -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(social == null && reference == link.value, feedbackClick { selectContact(link) })
            TextButton(feedbackClick { selectContact(link) }, Modifier.weight(1f)) {
                SocialContactIcon(link, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                Text(link.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } }
    }
    if (choosing) ContactPlatformPicker({ choosing = false }, title = uiText("profile.platform_choose")) {
        platform = it; draft = null; choosing = false; editing = true
    }
    if (editing) ContactLinkEditor(draft, platform, { editing = false }) { updated ->
        changeSocial(updated); editing = false
    }
}

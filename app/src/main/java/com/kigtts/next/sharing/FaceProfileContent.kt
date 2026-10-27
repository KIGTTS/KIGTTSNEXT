package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.identity.*
import com.kigtts.next.ui.settings.*
import org.json.JSONObject
import androidx.compose.ui.platform.LocalUriHandler
import java.io.File

/** A person's identity leads; each contact is a separate, reachable link card. */
@Composable internal fun FaceProfileContent(profile: JSONObject, modifier: Modifier = Modifier, showHeader: Boolean = true,
    image: (String) -> File? = { null }) {
    val person = remember(profile.toString()) { PublicIdentityProfile.from(profile) }
    var contact by remember { mutableStateOf<ContactLink?>(null) }
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth().testTag("face-profile"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeader) {
            FacePeerAvatar(person.avatar, Modifier.size(88.dp), person.avatarFrame)
            Text(person.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (person.bio.isNotBlank()) Text(person.bio, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        ProfileTags(person.tags)
        ProfileTileBoard(person.tiles, person.contacts, image, openContact = { contact = it },
            openLink = { value -> safeProfileLink(value)?.let { runCatching { uri.openUri(it) } } })
        if (person.contacts.isNotEmpty()) SettingsGroup("联系方式") {
            person.contacts.forEachIndexed { index, link ->
                val position = when { person.contacts.size == 1 -> SettingPosition.SINGLE
                    index == 0 -> SettingPosition.FIRST; index == person.contacts.lastIndex -> SettingPosition.LAST
                    else -> SettingPosition.MIDDLE }
                SettingItem(link.label.ifBlank { "联系方式" }, link.value, null, position,
                    modifier = Modifier.testTag("face-profile-contact-$index"), onClick = { contact = link },
                    leading = { SocialContactIcon(link, Modifier.size(24.dp)) },
                    trailing = { Icon(Icons.Default.QrCode, "查看二维码", Modifier.size(20.dp)) })
            }
        }
    }
    contact?.let { PersonalContactDialog(it) { contact = null } }
}

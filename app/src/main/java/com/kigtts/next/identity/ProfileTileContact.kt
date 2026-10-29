package com.kigtts.next.identity

import org.json.JSONObject

/** New social tiles own their account. Legacy tiles continue to reference the profile contacts. */
internal fun ProfileTile.resolveContact(contacts: List<ContactLink>): ContactLink? =
    if (kind == ProfileTileKind.CONTACT) social ?: contacts.firstOrNull { contact.isNotBlank() && it.value == contact } else null

internal fun profileTileSocial(json: JSONObject?): ContactLink? = json?.let {
    ContactLink(it.optString("label").take(24), it.optString("value").take(2048),
        it.optString("qrContent").take(2048).takeIf(String::isNotBlank)).takeIf { link -> link.value.isNotBlank() }
}

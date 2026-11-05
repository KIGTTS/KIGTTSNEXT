package com.kigtts.next.identity

import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.sharing.FaceWebIcons
import com.kigtts.next.sharing.faceAvatarData
import com.kigtts.next.sharing.shareWebAppearance
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A self-contained profile: no dependency on fonts installed on the recipient's device. */
internal fun profileWebAppearance(identity: IdentityCard, stickers: StickerRepository, directory: File,
    manifest: JSONObject, files: MutableList<File>) {
    val context = stickers.context
    val appearance = shareWebAppearance(context, directory, files)
    val icons = FaceWebIcons(context)
    appearance.put("contactIcons", JSONArray(identity.contacts.map(icons::icon)))
    appearance.put("tileIcons", JSONArray(identity.tiles.map { tile ->
        icons.icon(if (tile.kind == ProfileTileKind.LINK) ContactLink("链接", "")
            else tile.resolveContact(identity.contacts) ?: ContactLink("", ""))
    }))
    identity.avatar?.let(stickers::imageFile)?.let(::faceAvatarData)?.let { manifest.put("avatar", it) }
    manifest.put("appearance", appearance)
}

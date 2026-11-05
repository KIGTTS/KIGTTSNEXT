package com.kigtts.next.identity

import com.kigtts.next.cards.StickerRepository
import java.io.File

/** Only public profile fields and images referenced by this profile enter the share session. */
internal fun profileSharingFiles(identity: IdentityCard, stickers: StickerRepository, directory: File, pdf: File): List<File> {
    val banner = exportIdentityCard(identity, stickers, directory)
    val manifest = identity.publicJson().put("banner", banner.name)
    val files = mutableListOf(pdf, banner)
    val tiles = manifest.getJSONArray("tiles")
    identity.tiles.forEachIndexed { i, tile ->
        tiles.getJSONObject(i).remove("image")
        profileImageFile(stickers, tile.image)?.let { source ->
            val target = File(directory, "tile-$i.${source.extension.lowercase()}")
            source.copyTo(target, overwrite = true); files += target
            tiles.getJSONObject(i).put("file", target.name)
        }
    }
    profileWebAppearance(identity, stickers, directory, manifest, files)
    return files + File(directory, "profile.json").also { it.writeText(manifest.toString(), Charsets.UTF_8) }
}

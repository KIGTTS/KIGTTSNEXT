package com.kigtts.next.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.identity.ContactFavorites
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.ProfileTileKind
import com.kigtts.next.identity.PublicIdentityProfile
import com.kigtts.next.identity.writeProfileJpeg
import java.io.File

internal class LegacyContactCards(private val context: Context) {
    fun restore(cards: List<LegacyCard>): Int {
        if (cards.isEmpty()) return 0
        val favorites = ContactFavorites.get(context)
        val existing = favorites.items.value.mapTo(hashSetOf()) { it.id }
        val pending = cards.associate { card ->
            val profile = profile(card)
            favorites.key(profile) to (card to profile)
        }.filterKeys { it !in existing }
        require(existing.size + pending.size <= 1000) { context.uiCopy("backup.legacy_collection_full") }
        if (pending.isEmpty()) return 0
        val staging = kotlin.io.path.createTempDirectory(context.cacheDir.toPath(), "legacy-contact-").toFile()
        try {
            val prepared = pending.mapValues { (id, source) ->
                val (card, profile) = source
                val cover = File(staging, "$id.jpg")
                if (card.image == null) writeSolidCover(card, cover)
                else check(writeProfileJpeg(card.image, cover)) { context.uiCopy("backup.legacy_card_invalid") }
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cover.path, size)
                check(size.outWidth > 0 && size.outHeight > 0) { context.uiCopy("backup.legacy_card_invalid") }
                profile.put("coverAspect", size.outWidth.toDouble() / size.outHeight) to cover
            }
            return synchronized(favorites) {
                val saved = favorites.items.value.mapTo(hashSetOf()) { it.id }
                val additions = prepared.filterKeys { it !in saved }.values
                require(saved.size + additions.size <= 1000) { context.uiCopy("backup.legacy_collection_full") }
                additions.forEach { (profile, cover) -> favorites.save(profile, cover) }
                additions.size
            }
        } finally { staging.deleteRecursively() }
    }

    private fun profile(card: LegacyCard) = PublicIdentityProfile(
        name = card.title.take(40).ifBlank { context.uiCopy("profile.export_user") },
        bio = card.note.take(240), avatar = null, avatarFrame = "circle", contacts = emptyList(), tags = emptyList(),
        tiles = buildList {
            if (card.qr.isNotBlank()) add(ProfileTile(id = legacyId("qr/${card.id}"), kind = ProfileTileKind.QR,
                text = card.qr, columns = 4, rows = 4))
            if (card.title.length > 40) add(ProfileTile(id = legacyId("title/${card.id}"), text = card.title, columns = 4))
            card.note.drop(240).chunked(2048).forEachIndexed { index, text ->
                add(ProfileTile(id = legacyId("note/${card.id}/$index"), text = text, columns = 4, rows = 2))
            }
        }
    ).json().put("identityId", card.id).put("coverButtonsBaked", false).put("coverIsBackground", true)

    private fun writeSolidCover(card: LegacyCard, target: File) {
        val bitmap = if (card.landscape) Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
            else Bitmap.createBitmap(90, 160, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(card.color.toInt())
            check(target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }) {
                context.uiCopy("backup.legacy_card_invalid")
            }
        } finally { bitmap.recycle() }
    }
}

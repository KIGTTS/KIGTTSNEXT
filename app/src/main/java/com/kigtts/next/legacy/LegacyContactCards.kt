package com.kigtts.next.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.identity.ContactFavorites
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.identity.ProfileTileKind
import com.kigtts.next.identity.PublicIdentityProfile
import com.kigtts.next.identity.profileTileJson
import com.kigtts.next.identity.writeProfileJpeg
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val COVER_ASPECT = 16f / 9f

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
                prepare(profile, card.image, card.color.toInt(), card.landscape, staging, id)
            }
            return synchronized(favorites) {
                val saved = favorites.items.value.mapTo(hashSetOf()) { it.id }
                val additions = prepared.filterKeys { it !in saved }.values
                require(saved.size + additions.size <= 1000) { context.uiCopy("backup.legacy_collection_full") }
                additions.forEach { (profile, cover, images) -> favorites.save(profile, cover, images) }
                additions.size
            }
        } finally { staging.deleteRecursively() }
    }

    /** Rewrite snapshots that were saved with the previous full-height cover layout. */
    fun upgradeSaved() {
        val favorites = ContactFavorites.get(context)
        val staging = kotlin.io.path.createTempDirectory(context.cacheDir.toPath(), "legacy-layout-").toFile()
        try {
            favorites.items.value.forEach { item ->
                val cover = item.cover ?: return@forEach
                val profile = JSONObject(item.profile)
                val originalId = profile.optString("identityId")
                if (!isPreviousLayout(profile, originalId)) return@forEach
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cover.path, size)
                if (size.outWidth <= 0 || size.outHeight <= 0) return@forEach
                val solid = previousSolidColor(cover, size)
                val (updated, cropped, images) = prepare(profile, cover.takeIf { solid == null },
                    solid ?: 0xff038387.toInt(), size.outWidth >= size.outHeight, staging, item.id)
                synchronized(favorites) {
                    if (favorites.items.value.any { it.id == item.id && it.profile == item.profile }) {
                        favorites.save(updated, cropped, images)
                    }
                }
                cropped.delete()
                images.values.forEach(File::delete)
            }
        } finally { staging.deleteRecursively() }
    }

    private fun isPreviousLayout(profile: JSONObject, id: String): Boolean {
        if (profile.optInt("legacyCardLayout") == 2 || !profile.optBoolean("coverIsBackground") ||
            profile.optBoolean("coverButtonsBaked", true) ||
            runCatching { UUID.fromString(id).version() }.getOrNull() != 3) return false
        val person = PublicIdentityProfile.from(profile)
        if (person.avatar != null || person.contacts.isNotEmpty() || person.tags.isNotEmpty()) return false
        val oldTileIds = setOf(legacyId("qr/$id"), legacyId("title/$id")) +
            (0..3).map { legacyId("note/$id/$it") }
        return person.tiles.all { it.id in oldTileIds }
    }

    private fun prepare(profile: JSONObject, image: File?, color: Int, landscape: Boolean, staging: File, id: String):
        Triple<JSONObject, File, Map<String, File>> {
        val cover = File(staging, "$id.jpg")
        val images = if (image == null) {
            writeSolidCover(color, cover)
            emptyMap()
        } else {
            val tile = ProfileTile(id = legacyId("image/${profile.getString("identityId")}"), kind = ProfileTileKind.IMAGE,
                columns = 4, rows = if (landscape) 2 else 3)
            val photo = File(staging, "$id-photo.jpg")
            check(writeProfileJpeg(image, photo) && writeProfileJpeg(image, cover, COVER_ASPECT)) {
                context.uiCopy("backup.legacy_card_invalid")
            }
            profile.put("tiles", profileTileJson(listOf(tile) + PublicIdentityProfile.from(profile).tiles))
            mapOf(tile.id to photo)
        }
        profile.put("coverAspect", COVER_ASPECT.toDouble()).put("legacyCardLayout", 2)
        return Triple(profile, cover, images)
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

    private fun previousSolidColor(cover: File, size: BitmapFactory.Options): Int? {
        val dimensions = size.outWidth to size.outHeight
        if (dimensions != (160 to 90) && dimensions != (90 to 160)) return null
        val bitmap = BitmapFactory.decodeFile(cover.path) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            pixels.first().takeIf { color -> pixels.all { it == color } }
        } finally { bitmap.recycle() }
    }

    private fun writeSolidCover(color: Int, target: File) {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            check(target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }) {
                context.uiCopy("backup.legacy_card_invalid")
            }
        } finally { bitmap.recycle() }
    }
}

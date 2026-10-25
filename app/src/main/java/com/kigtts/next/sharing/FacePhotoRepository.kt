package com.kigtts.next.sharing

import com.kigtts.next.cards.*
import com.kigtts.next.identity.IdentityRepository
import com.kigtts.plugin.api.*
import com.kigtts.next.ui.cards.polaroidFormat
import java.io.File
import java.util.UUID

internal class FacePhotoRepository(private val cards: CardRepository, private val identities: IdentityRepository) {
    private val stickers get() = cards.stickers
    fun prepare(ids: List<String>, original: Boolean, polaroid: Boolean, root: File): List<FaceAsset> {
        require(original || polaroid) { "请选择原图或拍立得成片" }
        require(ids.size in 1..64) { "每次最多分享 64 张照片" }
        val outgoing = File(root, "outgoing").apply { mkdirs() }
        return ids.distinct().flatMap { id ->
            val identity = identities.items.value.firstOrNull { it.designId == id }
            val card = requireNotNull(if (identity != null) stickers.identityDesigns.get(id) else cards.cards.value.firstOrNull { it.id == id }) { "这张照片已移除" }
            val photo = UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)).toString()
            fun asset(source: File, variant: String): FaceAsset {
                val key = faceToken(); val suffix = source.extension.lowercase().takeIf { it in setOf("png", "webp", "jpg", "jpeg") } ?: "jpg"
                val file = File(outgoing, "$key.$suffix"); source.copyTo(file)
                return FaceAsset(key, photo, variant, "${card.title.ifBlank { "照片" }.take(80)}-$variant.$suffix",
                    when (suffix) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }, file.length(), sha256(file),
                    if (card.frameStyleId == "identity.banner") "BANNER" else polaroidFormat(card, stickers.frameStyle(card)).name, file)
            }
            buildList {
                if (original) (card.originalImageId ?: card.imageId)?.let(stickers::imageFile)?.takeIf { it.isFile }?.let { add(asset(it, "original")) }
                if (polaroid) {
                    val rendered = if (identity != null) com.kigtts.next.identity.exportIdentityCard(identity, stickers, outgoing)
                        else exportPolaroidStill(stickers, card)
                    add(asset(rendered, "polaroid"))
                }
            }
        }.also { require(it.isNotEmpty()) { "没有可以分享的原图，请选择拍立得成片" } }
    }
    fun receive(offer: FaceOffer, assets: List<FaceAsset>) = synchronized(stickers) {
        val album = cards.albums.value.firstOrNull { it.id == "face-received" } ?: cards.createAlbum("面对面接收", "face-received")
        assets.groupBy { it.photo }.forEach { (photo, variants) ->
            val id = UUID.nameUUIDFromBytes("face/${offer.id}/$photo".toByteArray(Charsets.UTF_8)).toString()
            if (cards.cards.value.none { it.id == id }) {
                val original = variants.firstOrNull { it.variant == "original" }?.file?.let(stickers::importResource)
                val composed = variants.firstOrNull { it.variant == "polaroid" }
                val shown = composed?.file?.let(stickers::importResource) ?: requireNotNull(original)
                val banner = variants.first().format == "BANNER"
                val format = if (banner) PolaroidFormat.WIDE else PolaroidFormat.valueOf(variants.first().format)
                val style = if (composed != null) "received.${if (banner) "banner" else format.name.lowercase()}" else "instax.${if (format == PolaroidFormat.LEGACY) "square" else format.name.lowercase()}.classic"
                if (composed != null && stickers.frameStyles.value.none { it.id == style })
                    stickers.apply(listOf(StickerEdit.PutFrameStyle(PolaroidFrameStyle(style, "收到的照片", format = format))))
                cards.receive(PolaroidCard(id = id, title = "", imageId = shown.id, originalImageId = original?.id, frameStyleId = style), album.id)
                stickers.context.getSharedPreferences("face-receipts", 0).edit().putString(id, offer.profile.toString()).apply()
            }
        }
    }
}

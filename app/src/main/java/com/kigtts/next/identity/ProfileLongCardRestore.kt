package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.File
import kotlin.math.roundToInt

internal object ProfileLongCardRestore {
    const val MAX_BYTES = 96L * 1024 * 1024

    fun imageFile(file: File): Boolean {
        if (file.length() !in 8..MAX_BYTES) return false
        return file.inputStream().use {
            when (it.read()) {
                0x89 -> it.read() == 0x50 && it.read() == 0x4e && it.read() == 0x47
                0xff -> it.read() == 0xd8 && it.read() == 0xff
                else -> false
            }
        }
    }

    fun restore(file: File, directory: File, favorites: ContactFavorites): SavedContact? {
        if (!imageFile(file)) return null
        directory.mkdirs()
        @Suppress("DEPRECATION")
        val decoder = runCatching { BitmapRegionDecoder.newInstance(file.path, false) }.getOrNull() ?: return null
        try {
            val width = decoder.width
            val height = decoder.height
            if (width !in 420..4096 || height !in (width / 2)..40000) return null
            val source = File(directory, "embedded.mhtml")
            if (ProfilePngPayload.extract(file, source)) {
                runCatching { ProfileArchive.restore(source, File(directory, "embedded"), favorites) }.getOrNull()?.let { return it }
            }
            val json = ProfileGrayCode.decode(width, height) { band ->
                if (width.toLong() * band > 24_000_000) null
                else runCatching { decoder.decodeRegion(Rect(0, height - band, width, height), options()) }.getOrNull()
            } ?: return null
            val data = OfflineProfileData.parse(json) ?: return null
            val known = data.optJSONObject("profile")?.let(OfflineProfileData::snapshot) ?: return null
            known.remove("avatar")
            val tiles = profileTiles(known)
            known.put("tiles", profileTileJson(tiles.map { it.copy(image = "") }))
            val cover = data.optJSONArray("cover")?.takeIf { it.length() == 4 }?.let {
                val scale = width.toDouble() / ProfileGrayCode.WIDTH
                val rect = Rect((it.optInt(0) * scale).roundToInt(), (it.optInt(1) * scale).roundToInt(),
                    (it.optInt(2) * scale).roundToInt(), (it.optInt(3) * scale).roundToInt())
                if (rect.left !in 0 until rect.right || rect.right > width || rect.top !in 0 until rect.bottom ||
                    rect.bottom > height || rect.height() > width * 2) return@let null
                val bitmap = runCatching { decoder.decodeRegion(rect, options(if (rect.width() > 1600) 2 else 1)) }.getOrNull() ?: return@let null
                try {
                    File(directory, "crop.jpg").takeIf { target ->
                        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } && target.length() <= 2 * 1024 * 1024
                    }
                } finally { bitmap.recycle() }
            }
            val photos = linkedMapOf<String, File>()
            data.optJSONArray("tileCrops")?.let { crops ->
                for (index in 0 until crops.length()) {
                    val item = crops.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val tile = tiles.firstOrNull { it.id == id && it.kind == ProfileTileKind.IMAGE } ?: continue
                    val scale = width.toDouble() / ProfileGrayCode.WIDTH
                    val rect = Rect((item.optInt("left") * scale).roundToInt(), (item.optInt("top") * scale).roundToInt(),
                        (item.optInt("right") * scale).roundToInt(), (item.optInt("bottom") * scale).roundToInt())
                    if (rect.left !in 0 until rect.right || rect.right > width || rect.top !in 0 until rect.bottom || rect.bottom > height ||
                        rect.width() > width || rect.height() > width * 2) continue
                    val bitmap = runCatching { decoder.decodeRegion(rect, options(if (rect.width() > 1600) 2 else 1)) }.getOrNull() ?: continue
                    try {
                        val target = File(directory, "tile-$index.jpg")
                        if (target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } && target.length() <= 2 * 1024 * 1024) {
                            photos[tile.id] = target
                        } else target.delete()
                    } finally { bitmap.recycle() }
                }
            }
            return favorites.save(known, cover, photos)
        } finally { decoder.recycle() }
    }

    private fun options(sample: Int = 1) = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = sample
    }
}

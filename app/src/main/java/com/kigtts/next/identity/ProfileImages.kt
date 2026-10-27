package com.kigtts.next.identity

import com.kigtts.next.cards.StickerRepository
import java.io.File

internal fun profileImageFile(stickers: StickerRepository, id: String): File? = id.takeIf {
    it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && it != "." && it != ".."
}?.let { File(stickers.context.filesDir, "profile-images/$it").takeIf(File::isFile) ?: stickers.imageFile(it) }

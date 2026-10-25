package com.kigtts.next.artwork

import android.graphics.Bitmap
import com.kigtts.next.cards.PolaroidRenderer
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.sketch.SketchAlbumRepository
import com.kigtts.plugin.api.*
import java.io.File

internal fun artworkImage(model: AppViewModel, library: ArtworkLibrary, id: String): File {
    if (library == ArtworkLibrary.SKETCHES) return SketchAlbumRepository(model.getApplication()).imageFile(id)
    val personal = if (library == ArtworkLibrary.PERSONAL_CARDS) requireNotNull(model.identities.items.value.firstOrNull { it.id == id }) else null
    val design = if (personal != null) model.stickers.identityDesigns.get(personal.designId)
        else model.cards.cards.value.firstOrNull { it.id == id && it.frameStyleId != "identity.banner" }
    requireNotNull(design) { "作品已移除" }
    val root = File(model.getApplication<android.app.Application>().cacheDir, "artwork-selection").apply { mkdirs() }
    val target = File(root, "$id-${design.hashCode()}-${personal.hashCode()}.png")
    synchronized(ArtworkLibrary::class.java) {
        if (target.isFile) return target
        if (personal != null) com.kigtts.next.identity.exportIdentityCard(personal, model.stickers, root).copyTo(target, overwrite = true)
        else {
            val bitmap = PolaroidRenderer.renderBitmap(model.stickers, design, maximumHeight = 720)
            try { target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        root.listFiles().orEmpty().filter { it.name != target.name }.sortedByDescending { it.lastModified() }.drop(96).forEach { it.delete() }
    }
    return target
}

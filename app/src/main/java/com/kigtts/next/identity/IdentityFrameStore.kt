package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.resources.ResourceArchive
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

data class IdentityFrame(val id: String, val name: String, val imageId: String? = null)

class IdentityFrameStore(private val stickers: StickerRepository) {
    private val prefs = stickers.context.getSharedPreferences("identity-frames", 0)
    private val defaults = listOf(IdentityFrame("none", "无边框"), IdentityFrame("fine-line", "细线"), IdentityFrame("petals", "花瓣"), IdentityFrame("circuit", "电路"))
    private val mutable = MutableStateFlow(defaults + runCatching {
        val json = JSONArray(prefs.getString("items", "[]")); List(json.length()) { i -> json.getJSONObject(i).let {
            IdentityFrame(it.getString("id"), it.getString("name"), it.getString("imageId")) } }
    }.getOrDefault(emptyList()))
    val items = mutable.asStateFlow()
    @Synchronized internal fun install(archive: ResourceArchive) {
        require(mutable.value.size + archive.cardFrames.items.size <= 256) { "名片边框较多，请先移除不需要的边框" }
        val next = archive.cardFrames.items.map { item ->
            val source = archive.file(item.artwork)
            val image = if (source.extension.equals("svg", true)) {
                val spec = VectorStickerGenerator("frame", item.name, item.artwork, 1024, 576)
                val bitmap = com.kigtts.next.cards.generators.VectorStickerRenderer.render(spec, source, emptyMap(), emptyMap())
                val temporary = java.io.File.createTempFile("identity-frame", ".png", stickers.context.cacheDir)
                try { temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; stickers.importResource(temporary) }
                finally { bitmap.recycle(); temporary.delete() }
            } else stickers.importResource(source)
            IdentityFrame(java.util.UUID.randomUUID().toString(), item.name, image.id)
        }
        val merged = mutable.value + next
        require(merged.size <= 256) { "名片边框较多，请先移除不需要的边框" }
        val json = JSONArray(merged.filter { it.imageId != null }.map { JSONObject().put("id", it.id).put("name", it.name).put("imageId", it.imageId) })
        check(prefs.edit().putString("items", json.toString()).commit()) { "边框未能保存" }; mutable.value = merged
    }
    internal fun draw(canvas: Canvas, id: String, width: Int, height: Int, ink: Int) {
        val frame = mutable.value.firstOrNull { it.id == id } ?: return
        frame.imageId?.let(stickers::imageFile)?.let { source ->
            com.kigtts.next.media.MediaBitmapDecoder.decode(source, 1200)?.let { bitmap ->
                try { canvas.drawBitmap(bitmap, null, Rect(0,0,width,height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
                finally { bitmap.recycle() }
            }; return
        }
        canvas.save(); canvas.scale(width / 1000f, height / 562.5f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 140; style = Paint.Style.STROKE; strokeWidth = 2f }
        when (id) {
            "fine-line" -> { canvas.drawRoundRect(14f,14f,986f,548.5f,24f,24f,p); p.alpha = 60; canvas.drawRoundRect(22f,22f,978f,540.5f,20f,20f,p) }
            "petals" -> { canvas.drawRoundRect(18f,18f,982f,544.5f,32f,32f,p)
                listOf(25f to 25f, 975f to 25f, 25f to 537.5f, 975f to 537.5f).forEach { (x,y) ->
                    canvas.save(); canvas.translate(x,y); p.style = Paint.Style.FILL
                    repeat(6) { canvas.save(); canvas.rotate(it*60f); canvas.drawOval(-5f,-18f,5f,-3f,p); canvas.restore() }
                    canvas.drawCircle(0f,0f,4f,p); canvas.restore() } }
            "circuit" -> { val path = Path().apply { moveTo(12f,100f); lineTo(12f,36f); lineTo(36f,12f); lineTo(200f,12f)
                    moveTo(800f,12f); lineTo(964f,12f); lineTo(988f,36f); lineTo(988f,100f)
                    moveTo(988f,462f); lineTo(988f,526f); lineTo(964f,550f); lineTo(800f,550f)
                    moveTo(200f,550f); lineTo(36f,550f); lineTo(12f,526f); lineTo(12f,462f) }
                canvas.drawPath(path,p); listOf(200f to 12f,800f to 12f,200f to 550f,800f to 550f).forEach { (x,y) -> canvas.drawCircle(x,y,4f,p) } }
        }
        canvas.restore()
    }
}

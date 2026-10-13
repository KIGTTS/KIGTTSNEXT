package com.kigtts.next.ui.sketch

import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject

/** A drawing keeps its own geometry, independent of the current phone orientation. */
data class SketchDocument(val width: Int, val height: Int, val background: Long, val strokes: List<Stroke>)

internal object SketchDocumentCodec {
    fun encode(document: SketchDocument): String = JSONObject().apply {
        put("version", 1); put("width", document.width); put("height", document.height)
        put("background", document.background)
        put("strokes", JSONArray().apply { document.strokes.forEach { stroke ->
            put(JSONObject().apply {
                put("color", stroke.color); put("alpha", stroke.alpha); put("erase", stroke.erase)
                put("points", JSONArray().apply { stroke.points.forEach { point ->
                    put(JSONArray().put(point.position.x).put(point.position.y).put(point.width))
                } })
            })
        } })
    }.toString()

    fun decode(source: String): SketchDocument {
        require(source.length <= 16 * 1024 * 1024) { "画板文件过大" }
        val root = JSONObject(source)
        require(root.getInt("version") == 1) { "不支持的画板格式" }
        val width = root.getInt("width"); val height = root.getInt("height")
        require(width in 1..16384 && height in 1..16384) { "画板尺寸无效" }
        val list = root.getJSONArray("strokes")
        require(list.length() <= 20000) { "画板笔画过多" }
        var count = 0
        val strokes = List(list.length()) { index ->
            val item = list.getJSONObject(index); val samples = item.getJSONArray("points")
            count += samples.length(); require(count <= 500000) { "画板笔画过多" }
            Stroke(List(samples.length()) { n ->
                val point = samples.getJSONArray(n)
                val x = point.getDouble(0).toFloat(); val y = point.getDouble(1).toFloat()
                val size = point.getDouble(2).toFloat()
                require(x.isFinite() && y.isFinite() && size.isFinite() && x in 0f..1f && y in 0f..1f && size > 0f)
                StrokePoint(Offset(x, y), size)
            }, item.getLong("color"), item.getDouble("alpha").toFloat().also { require(it.isFinite() && it in 0f..1f) },
                item.optBoolean("erase"))
        }
        return SketchDocument(width, height, root.getLong("background"), strokes)
    }
}

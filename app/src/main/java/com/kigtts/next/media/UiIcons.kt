package com.kigtts.next.media

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.caverock.androidsvg.SVG
import java.io.File
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/** Durable host-owned icon copies; quick entry metadata contains IDs, never arbitrary filesystem paths. */
class UiIcons(private val context: Context, private val root: File) {
    private val cache = object : android.util.LruCache<String, Bitmap>(2 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount }
    fun import(uri: Uri): String = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input); val stage = File.createTempFile("icon", ".tmp", context.cacheDir)
        try { stage.outputStream().use { output -> val buffer = ByteArray(8192); var total = 0
            while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 8 * 1024 * 1024) { "图标过大" }; output.write(buffer, 0, count) } }
            import(stage)
        } finally { stage.delete() }
    }
    fun import(source: File): String {
        require(source.isFile && source.length() in 1..8L * 1024 * 1024)
        val svg = source.inputStream().buffered().use { String(it.readNBytesCompat(256), Charsets.UTF_8).trimStart().let { text -> text.startsWith("<svg") || text.startsWith("<?xml") || text.startsWith("<!--") } }
        val bitmap = if (svg) renderSvg(source.readText(Charsets.UTF_8), 256) else MediaBitmapDecoder.decode(source, 512) ?: error("无法读取图片图标")
        val id = UUID.randomUUID().toString(); root.mkdirs()
        try { if (svg) source.copyTo(File(root, "$id.svg")) else File(root, "$id.png").outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    @Synchronized fun bitmap(id: String, size: Int = 256): Bitmap? {
        if (runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false).not()) return null
        cache.get(id)?.let { return it }
        return runCatching { val svg = File(root, "$id.svg"); val png = File(root, "$id.png")
            if (svg.isFile) renderSvg(svg.readText(Charsets.UTF_8), size) else if (png.isFile) MediaBitmapDecoder.decode(png, size) else null
        }.getOrNull()?.also { cache.put(id, it) }
    }
    companion object {
        fun renderSvg(text: String, size: Int): Bitmap {
            require(text.toByteArray(Charsets.UTF_8).size <= 512 * 1024 && size in 24..2048)
            validateSvg(text)
            val svg = SVG.getFromString(text)
            svg.setDocumentWidth(size.toFloat()); svg.setDocumentHeight(size.toFloat())
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { svg.renderToCanvas(Canvas(it)) }
        }
        fun renderPreviewSvg(text: String, size: Int): Bitmap {
            require(text.toByteArray(Charsets.UTF_8).size <= 512 * 1024 && size in 24..2048)
            validateSvg(text)
            val svg = SVG.getFromString(text)
            val box = svg.documentViewBox
            val ratio = if (box != null && box.width() > 0 && box.height() > 0) box.width() / box.height()
                else if (svg.documentWidth > 0 && svg.documentHeight > 0) svg.documentWidth / svg.documentHeight else 1f
            require(ratio.isFinite() && ratio > 0)
            val width = if (ratio >= 1) size else (size * ratio).toInt().coerceAtLeast(1)
            val height = if (ratio >= 1) (size / ratio).toInt().coerceAtLeast(1) else size
            svg.setDocumentWidth(width.toFloat()); svg.setDocumentHeight(height.toFloat())
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { svg.renderToCanvas(Canvas(it)) }
        }
        private fun validateSvg(text: String) {
            require(!text.contains(Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE))) { "SVG不允许实体或外部文档" }
            val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false; isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(text.byteInputStream(Charsets.UTF_8))
            require(document.documentElement.localName == "svg")
            val allowed = setOf("svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "defs", "clipPath", "linearGradient", "radialGradient", "stop", "title", "desc")
            val elements = mutableListOf<org.w3c.dom.Element>()
            val ids = mutableMapOf<String, org.w3c.dom.Element>()
            val references = java.util.IdentityHashMap<org.w3c.dom.Element, MutableList<String>>()
            var count = 0
            fun visit(node: org.w3c.dom.Element, depth: Int) {
                require(++count <= 2000 && depth <= 32 && node.localName in allowed) { "SVG包含不支持的元素或内容过多" }
                elements += node
                if (node.hasAttribute("id")) {
                    val id = node.getAttribute("id")
                    require(id.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) && ids.put(id, node) == null) { "SVG标识重复或不合法" }
                }
                for (i in 0 until node.attributes.length) {
                    val attr = node.attributes.item(i); val name = attr.localName ?: attr.nodeName
                    if (attr.nodeName == "xmlns" || attr.nodeName.startsWith("xmlns:")) { require(attr.nodeValue in setOf("http://www.w3.org/2000/svg", "http://www.w3.org/1999/xlink")); continue }
                    require(!name.startsWith("on", true) && name != "style" && !attr.nodeValue.contains(Regex("(?:https?:|file:|data:|javascript:|@import)", RegexOption.IGNORE_CASE))) { "SVG只允许本地矢量路径与渐变" }
                    require(name != "href") { "SVG不允许继承引用" }
                    Regex("url\\(\\s*([^)]+)\\)", RegexOption.IGNORE_CASE).findAll(attr.nodeValue).forEach {
                        val ref = it.groupValues[1].trim(' ', '\'', '"')
                        require(ref.startsWith('#')) { "SVG只允许本地引用" }
                        references.getOrPut(node) { mutableListOf() } += ref.drop(1)
                    }
                }
                for (i in 0 until node.childNodes.length) (node.childNodes.item(i) as? org.w3c.dom.Element)?.let { visit(it, depth + 1) }
            }
            visit(document.documentElement, 0)
            // Include child edges: a clipPath can recurse through a nested group's reference,
            // even when its own attributes have no direct self-reference.
            val colors = java.util.IdentityHashMap<org.w3c.dom.Element, Int>()
            fun check(node: org.w3c.dom.Element, depth: Int) {
                require(depth <= 64 && colors[node] != 1) { "SVG包含递归引用" }
                if (colors[node] == 2) return
                colors[node] = 1
                references[node].orEmpty().forEach { id -> check(requireNotNull(ids[id]) { "SVG引用不存在" }, depth + 1) }
                for (i in 0 until node.childNodes.length) (node.childNodes.item(i) as? org.w3c.dom.Element)?.let { check(it, depth + 1) }
                colors[node] = 2
            }
            elements.forEach { check(it, 0) }
        }
    }
}
private fun java.io.InputStream.readNBytesCompat(size: Int): ByteArray { val bytes = ByteArray(size); var total = 0
    while (total < size) { val read = read(bytes, total, size - total); if (read < 0) break; total += read }; return bytes.copyOf(total) }

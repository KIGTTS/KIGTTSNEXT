package com.kigtts.next.cards.generators

import android.graphics.*
import com.caverock.androidsvg.SVG
import com.kigtts.plugin.api.*
import java.io.File
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

/** Declarative SVG only: parameters cannot introduce markup, URLs, scripts or file access. */
internal object VectorStickerRenderer {
    fun values(spec: VectorStickerGenerator, inputs: Map<String, String>): Map<String, String> {
        require(spec.parameters.size <= 16 && spec.parameters.map { it.id }.distinct().size == spec.parameters.size)
        require(inputs.keys.all { key -> spec.parameters.any { it.id == key } })
        return spec.parameters.associate { p ->
            require(p.id.matches(Regex("[a-z][a-zA-Z0-9_]{0,31}")) && p.label.length in 1..64)
            val value = inputs[p.id] ?: p.default
            when (p.kind) {
                StickerParameterKind.TEXT -> require(p.maxLength in 1..512 && value.length <= p.maxLength && value.none { it == '\u0000' })
                StickerParameterKind.COLOR -> require(value.matches(Regex("#[0-9a-fA-F]{6}")))
                StickerParameterKind.NUMBER -> require(p.minimum.isFinite() && p.maximum.isFinite() && p.minimum < p.maximum && p.minimum >= -100000 && p.maximum <= 100000 &&
                    value.toDoubleOrNull()?.let { it.isFinite() && it in p.minimum..p.maximum } == true)
                StickerParameterKind.CHOICE -> require(p.choices.size in 1..32 && p.choices.all { it.length <= 128 } && value in p.choices)
            }
            p.id to value
        }
    }
    fun render(spec: VectorStickerGenerator, template: File, fonts: Map<String, File>, inputs: Map<String, String>): Bitmap {
        require(spec.width in 32..2048 && spec.height in 32..2048 && spec.width.toLong() * spec.height <= 2_097_152)
        require(template.length() in 1..512L * 1024 && fonts.size <= 8)
        val parameters = values(spec, inputs)
        var text = template.readText(Charsets.UTF_8)
        text = Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}").replace(text) { match -> escape(requireNotNull(parameters[match.groupValues[1]]) { "贴纸参数不存在" }) }
        val document = VectorStickerXml.parse(text)
        val nodes = document.getElementsByTagName("*"); require(nodes.length in 1..4096)
        val allowed = setOf("svg", "g", "defs", "path", "circle", "ellipse", "rect", "line", "polyline", "polygon", "linearGradient", "radialGradient", "stop", "clipPath", "text", "title", "desc")
        val texts = mutableListOf<Element>()
        repeat(nodes.length) { i -> val node = nodes.item(i) as Element
            require((node.localName ?: node.tagName) in allowed) { "贴纸包含不支持的矢量元素" }
            if (node.tagName == "text") texts += node
            repeat(node.attributes.length) { n -> val attr = node.attributes.item(n)
                if (attr.nodeName == "xmlns") {
                    require(attr.nodeValue == "http://www.w3.org/2000/svg"); return@repeat
                }
                require(!attr.nodeName.startsWith("on", true) && attr.nodeName !in setOf("style", "href", "xlink:href"))
                require(!attr.nodeValue.contains(Regex("(?:https?:|file:|data:|javascript:|@import)", RegexOption.IGNORE_CASE)))
            }
        }
        val faces = fonts.mapValues { (_, file) ->
            require(file.length() in 1..12L * 1024 * 1024 && file.extension.lowercase() in setOf("ttf", "otf"))
            Typeface.createFromFile(file)
        }
        // Convert text with the package font into ordinary SVG path geometry. No global resolver or font registration.
        texts.forEach { node ->
            require((0 until node.childNodes.length).none { node.childNodes.item(it) is Element })
            val size = node.getAttribute("font-size").ifBlank { "64" }.toFloat(); require(size.isFinite() && size in 1f..2048f)
            val x = node.getAttribute("x").ifBlank { "0" }.toFloat(); val y = node.getAttribute("y").ifBlank { "0" }.toFloat()
            require(x.isFinite() && y.isFinite() && kotlin.math.abs(x) < 100000 && kotlin.math.abs(y) < 100000)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = faces[node.getAttribute("font-family")] ?: Typeface.DEFAULT }
            val value = node.textContent.take(512); val length = paint.measureText(value)
            val start = x - when (node.getAttribute("text-anchor")) { "middle" -> length / 2; "end" -> length; else -> 0f }
            val path = Path(); paint.getTextPath(value, 0, value.length, start, y, path)
            val replacement = document.createElementNS("http://www.w3.org/2000/svg", "path")
            for (i in 0 until node.attributes.length) { val attr = node.attributes.item(i)
                if (attr.nodeName !in setOf("x", "y", "font-size", "font-family", "text-anchor")) replacement.setAttribute(attr.nodeName, attr.nodeValue) }
            replacement.setAttribute("d", pathData(path)); node.parentNode.replaceChild(replacement, node)
        }
        val out = java.io.StringWriter(); TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(out))
        val svg = SVG.getFromString(out.toString()); svg.setDocumentWidth(spec.width.toFloat()); svg.setDocumentHeight(spec.height.toFloat())
        val bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        try { svg.renderToCanvas(Canvas(bitmap)); return bitmap } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun pathData(path: Path): String {
        val points = path.approximate(.15f)
        return buildString { var previous = -1f
            for (i in points.indices step 3) {
                if (i == 0 || points[i] == previous) { if (i != 0) append('Z'); append('M') } else append('L')
                append(points[i+1]); append(','); append(points[i+2]); previous = points[i]
            }; append('Z') }
    }
}

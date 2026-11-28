package com.kigtts.next.ui.cards.camera

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import com.kigtts.next.camera.expressions.ExpressionFace
import com.kigtts.next.camera.expressions.ExpressionPoint
import com.kigtts.next.media.UiIcons
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

internal const val EXPRESSION_PREVIEW_EDGE = 320
internal const val EXPRESSION_PORTRAIT_SCRIM = .72f

// These landmarks are in the artwork's 1536 px viewBox, with left and right in screen order.
internal val expressionPreviewFace = ExpressionFace(
    0, RectF(70f, 104f, 251f, 278f), 1f, 1f,
    portraitPoint(507f, 852f), portraitPoint(1027f, 849f),
    portraitPoint(750f, 993f), portraitPoint(744f, 1156f), portraitPoint(786f, 1156f),
)

private fun portraitPoint(x: Float, y: Float): ExpressionPoint {
    val scale = EXPRESSION_PREVIEW_EDGE / 1536f
    return ExpressionPoint(x * scale, y * scale)
}

internal object ExpressionPreviewPortrait {
    private var picture: Picture? = null
    private var facelessPicture: Picture? = null
    private val features = setOf(
        "eyes", "nose", "mouth", "eyebrow-left", "eyebrow-right", "eyelashes-left", "eyelashes-right",
        "eye-white-left", "eye-white-right", "eye-shadow-left", "eye-shadow-right",
    )

    @Synchronized
    fun load(assets: AssetManager, withoutFeatures: Boolean = false): Picture {
        val cached = if (withoutFeatures) facelessPicture else picture
        if (cached != null) return cached
        return assets.open("camera-expressions/preview-avatar.svg").use { input ->
            val svg = if (withoutFeatures) {
                val document = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                }.newDocumentBuilder().parse(input)
                val elements = document.getElementsByTagName("*")
                val hidden = (0 until elements.length).mapNotNull { elements.item(it) as? Element }
                    .filter { it.getAttribute("id") in features }
                hidden.forEach { it.parentNode.removeChild(it) }
                val output = StringWriter()
                TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
                output.toString()
            } else {
                input.bufferedReader(Charsets.UTF_8).readText()
            }
            UiIcons.renderSvgPicture(svg, EXPRESSION_PREVIEW_EDGE)
        }.also {
            if (withoutFeatures) facelessPicture = it else picture = it
        }
    }
}

internal fun drawExpressionPortrait(canvas: Canvas, portrait: Picture, scrim: Paint? = null) {
    val edge = EXPRESSION_PREVIEW_EDGE.toFloat()
    canvas.drawPicture(portrait)
    scrim?.let { canvas.drawRect(0f, 0f, edge, edge, it) }
}

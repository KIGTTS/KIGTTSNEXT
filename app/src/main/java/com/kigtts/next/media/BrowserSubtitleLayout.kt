package com.kigtts.next.media

import android.content.Context
import android.graphics.*
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

/** Explicit simulation of host chrome, not a screenshot of the user's live subtitle or private cards. */
internal class BrowserSubtitleLayout(context: Context, width: Int, height: Int, private val web: WebView) : FrameLayout(context) {
    private val unit = (minOf(width, height) / 400f).coerceAtLeast(.5f)
    private val wide = width > height
    val viewport = if (wide) Rect((84 * unit).toInt(), (24 * unit).toInt(), (width * .70f).toInt(), height - (24 * unit).toInt())
        else Rect((12 * unit).toInt(), (92 * unit).toInt(), width - (12 * unit).toInt(), (height * .64f).toInt())
    private val safeTop = 56f * unit
    private val safeBottom = 52f * unit
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        setWillNotDraw(false)
        addView(web, LayoutParams(viewport.width(), viewport.height()).apply { leftMargin = viewport.left; topMargin = viewport.top })
        web.clipToOutline = true
        web.outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(view: android.view.View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, 24f * unit)
        } }
    }
    fun geometryJson() = JSONObject().put("x", viewport.left).put("y", viewport.top).put("width", viewport.width()).put("height", viewport.height())
        .put("safeTop", safeTop).put("safeBottom", safeBottom).put("simulated", true)
    fun applySafeArea() {
        // Use actual CSS-to-native ratio, including user display density.
        web.evaluateJavascript("(()=>{const ratio=(window.visualViewport?.width||innerWidth)/${viewport.width()};const r=document.documentElement;r.style.setProperty('--kigtts-safe-top',(${safeTop}*ratio)+'px');r.style.setProperty('--kigtts-safe-bottom',(${safeBottom}*ratio)+'px');r.style.setProperty('--kigtts-safe-left',(12*${unit}*ratio)+'px');r.style.setProperty('--kigtts-safe-right',(12*${unit}*ratio)+'px');window.dispatchEvent(new Event('resize'));})()", null)
    }
    private fun color(token: String, fallback: String) = Color.parseColor(WebThemeState.current.colors[token] ?: fallback)
    private fun panel(canvas: Canvas, rect: RectF, token: String) { paint.color = color(token, "#e2eded"); canvas.drawRoundRect(rect, 24 * unit, 24 * unit, paint) }
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, size: Float = 15f) {
        paint.color = color("on-surface", "#172222"); paint.textSize = size * unit; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL); canvas.drawText(text, x, y, paint)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(color("surface", "#f4fafb"))
        if (wide) {
            label(canvas, "KIGTTS", 10 * unit, height * .5f, 12f)
            panel(canvas, RectF(viewport.right + 12f * unit, 24f * unit, width - 12f * unit, height - 24f * unit), "surface-container")
        } else {
            label(canvas, "KIGTTS", width * .36f, 68f * unit, 22f)
            panel(canvas, RectF(12f * unit, viewport.bottom + 12f * unit, width - 12f * unit, height - 24f * unit), "surface-container")
        }
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val l = viewport.left.toFloat(); val r = viewport.right.toFloat(); val t = viewport.top.toFloat(); val b = viewport.bottom.toFloat()
        panel(canvas, RectF(l + 8 * unit, t + 8 * unit, l + 168 * unit, t + 48 * unit), "surface-container-high")
        label(canvas, "⋮     ↕    开始聆听", l + 18 * unit, t + 34 * unit, 13f)
        panel(canvas, RectF(r - 48 * unit, t + 8 * unit, r - 8 * unit, t + 48 * unit), "surface-container-high")
        label(canvas, "⌕", r - 37 * unit, t + 34 * unit, 22f)
        panel(canvas, RectF(l + 8 * unit, b - 46 * unit, l + 84 * unit, b - 8 * unit), "surface-container-high")
        label(canvas, "关闭", l + 27 * unit, b - 21 * unit, 13f)
        panel(canvas, RectF(r - 84 * unit, b - 46 * unit, r - 8 * unit, b - 8 * unit), "surface-container-high")
        label(canvas, "全屏", r - 65 * unit, b - 21 * unit, 13f)
    }
}

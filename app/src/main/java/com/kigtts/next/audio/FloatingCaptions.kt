package com.kigtts.next.audio

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

/** Generic floating text output. Audio/model logic stays in the recognition worker. */
internal class FloatingCaptions(private val context: Context, private val stop: () -> Unit) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var container: LinearLayout? = null
    private var textView: TextView? = null
    private val density = context.resources.displayMetrics.density
    fun show(text: String, size: Float, radius: Float) {
        if (container == null) {
            val params = WindowManager.LayoutParams((context.resources.displayMetrics.widthPixels * .88).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (96 * density).toInt()
            }
            val root = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                setPadding((16 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt()) }
            val caption = TextView(context).apply { setTextColor(android.graphics.Color.WHITE); maxLines = 6; gravity = Gravity.BOTTOM }
            val close = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel); contentDescription = "关闭聆听"
                background = null; imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                setOnClickListener { stop() }
            }
            root.addView(caption, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(close, LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()))
            var startY = 0f; var initialY = 0
            root.setOnTouchListener { _, event -> when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; initialY = params.y; true }
                MotionEvent.ACTION_MOVE -> { params.y = (initialY - event.rawY + startY).toInt().coerceIn(0, context.resources.displayMetrics.heightPixels); windows.updateViewLayout(root, params); true }
                else -> true
            } }
            windows.addView(root, params); container = root; textView = caption
        }
        container?.background = GradientDrawable().apply { setColor(0xD9222929.toInt()); cornerRadius = radius * density }
        textView?.textSize = size
        textView?.text = text.takeLast(4000).ifBlank { "正在聆听…" }
    }
    fun close() { container?.let { runCatching { windows.removeView(it) } }; container = null; textView = null }
}

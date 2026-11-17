package com.kigtts.next.media

import android.app.Presentation
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/** A private display draws only these WebViews. It never attaches to the phone's activity or IME. */
internal object BrowserInspectionSurface {
    private const val EDGE = 2048
    private var current: Surface? = null

    /** All acquire/release calls are serialized by the Android main dispatcher. */
    fun acquire(context: Context): Lease {
        val surface = current ?: create(context.applicationContext).also { current = it }
        surface.users++
        var closed = false
        return Lease(surface.root) {
            if (!closed) {
                closed = true
                if (--surface.users == 0) {
                    current = null
                    surface.close()
                }
            }
        }
    }

    private fun create(context: Context): Surface {
        val thread = HandlerThread("KigttsBrowserFrames").apply { start() }
        var reader: ImageReader? = null
        var display: VirtualDisplay? = null
        var window: Presentation? = null
        try {
            val frames = ImageReader.newInstance(EDGE, EDGE, PixelFormat.RGBA_8888, 2)
            reader = frames
            frames.setOnImageAvailableListener({ runCatching { it.acquireLatestImage()?.close() } }, Handler(thread.looper))
            val screen = requireNotNull(context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "KIGTTS private browser", EDGE, EDGE, 160, frames.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            )) { "设备无法创建后台浏览器" }
            display = screen
            val presentation = Presentation(context, screen.display)
            window = presentation
            val root = FrameLayout(presentation.context)
            presentation.setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            presentation.window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                setWindowAnimations(0)
            }
            presentation.show()
            presentation.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return Surface(presentation, screen, frames, thread, root)
        } catch (failure: Throwable) {
            runCatching { window?.dismiss() }
            display?.release(); reader?.close(); thread.quitSafely()
            throw failure
        }
    }

    internal class Lease(val root: FrameLayout, private val release: () -> Unit) {
        fun close() = release()
    }
    private class Surface(val window: Presentation, val display: VirtualDisplay, val reader: ImageReader,
        val thread: HandlerThread, val root: FrameLayout, var users: Int = 0) {
        fun close() {
            runCatching { window.dismiss() }
            display.release(); reader.close(); thread.quitSafely()
        }
    }
}

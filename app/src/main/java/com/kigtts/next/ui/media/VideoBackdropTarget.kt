package com.kigtts.next.ui.media

import android.view.Surface

/** Native surfaces are retired on the GL thread, after their EGL surface has been detached. */
internal class VideoBackdropTarget {
    data class Frame(val surface:Surface,val width:Int,val height:Int)
    @Volatile var frame:Frame?=null;private set
    private val retired=mutableListOf<Surface>()
    private var released=false
    @Synchronized fun update(surface:Surface,width:Int,height:Int) {
        if(released) { surface.release();return }
        frame?.surface?.takeIf { it!==surface }?.let { retired+=it }
        frame=Frame(surface,width,height)
    }
    @Synchronized fun detach(surface:Surface) {
        if(frame?.surface===surface) { frame=null;retired+=surface }
    }
    @Synchronized fun retired():List<Surface> = retired.toList().also { retired.clear() }
    @Synchronized fun release() {
        if(released)return
        released=true;frame?.surface?.release();frame=null;retired.forEach(Surface::release);retired.clear()
    }
}

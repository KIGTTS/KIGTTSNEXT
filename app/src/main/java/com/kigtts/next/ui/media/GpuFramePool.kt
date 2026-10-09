@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil

/** Resize only a returned buffer. Frames still owned by Media3 are never changed or deleted. */
internal class GpuFramePool(private val hdr:Boolean) {
    private val free=mutableListOf<GlTextureInfo>()
    private val used=mutableSetOf<GlTextureInfo>()
    fun acquire(provider:GlObjectsProvider,width:Int,height:Int):GlTextureInfo {
        check(used.size<2) { "GPU output backpressure exceeded" }
        val matching=free.firstOrNull { it.width==width&&it.height==height }
        val texture=matching ?: run {
            if(free.isNotEmpty())free.removeAt(0).release()
            provider.createBuffersForTexture(GlUtil.createTexture(width,height,hdr),width,height)
        }
        free.remove(texture);used+=texture;return texture
    }
    fun recycle(texture:GlTextureInfo):Boolean { if(!used.remove(texture))return false;free+=texture;return true }
    fun flush() { free+=used;used.clear() }
    fun release() { (free+used).forEach { it.release() };free.clear();used.clear() }
}

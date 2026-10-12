package com.kigtts.next.ui.media

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun VideoBackdropView(target:VideoBackdropTarget,modifier:Modifier,tap:()->Unit,hold:()->Boolean) {
    AndroidView(factory={ context -> TextureView(context).apply {
        isOpaque=false;setOnClickListener { tap() };setOnLongClickListener { hold() }
        var output:Surface?=null
        surfaceTextureListener=object:TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture:SurfaceTexture,width:Int,height:Int) {
                output=Surface(texture).also { target.update(it,width,height) }
            }
            override fun onSurfaceTextureSizeChanged(texture:SurfaceTexture,width:Int,height:Int) { output?.let { target.update(it,width,height) } }
            override fun onSurfaceTextureDestroyed(texture:SurfaceTexture):Boolean { output?.let(target::detach);output=null;return true }
            override fun onSurfaceTextureUpdated(texture:SurfaceTexture) {}
        }
    } },modifier=modifier,onRelease={ view -> view.surfaceTextureListener?.let { listener -> view.surfaceTexture?.let(listener::onSurfaceTextureDestroyed) };view.surfaceTextureListener=null })
}

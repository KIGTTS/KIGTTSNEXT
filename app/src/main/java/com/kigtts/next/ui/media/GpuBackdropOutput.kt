@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import android.opengl.*
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil

/** A blurred second output of the same decoded frame. The sharp output keeps its source aspect. */
internal class GpuBackdropOutput(private val target:VideoBackdropTarget) {
    private var surface:Surface?=null
    private var egl:EGLSurface=EGL14.EGL_NO_SURFACE
    private var reported=false
    private val program=GlProgram(VERTEX,FRAGMENT).apply { setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4) }
    private fun destroy(display:EGLDisplay) {
        if(egl!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,egl)
        egl=EGL14.EGL_NO_SURFACE;surface=null
    }
    fun draw(provider:GlObjectsProvider,texture:Int,inputAspect:Float) {
        val display=EGL14.eglGetCurrentDisplay();val context=EGL14.eglGetCurrentContext()
        val previousDraw=EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);val previousRead=EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        target.retired().forEach { if(it===surface)destroy(display);it.release() }
        val frame=target.frame ?: return
        if(frame.width<=0||frame.height<=0||!frame.surface.isValid)return
        try {
            if(surface!==frame.surface) { destroy(display);surface=frame.surface
                egl=provider.createEglSurface(display,frame.surface,C.COLOR_TRANSFER_SDR,false) }
            GlUtil.focusEglSurface(display,context,egl,frame.width,frame.height)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
            program.use();program.setSamplerTexIdUniform("uTexture",texture,0)
            program.setFloatUniform("uInputAspect",inputAspect)
            program.setFloatUniform("uViewAspect",frame.width.toFloat()/frame.height)
            program.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            if(!EGL14.eglSwapBuffers(display,egl))destroy(display)
        } catch(error:Exception) {
            if(!reported) { android.util.Log.w("VideoBackdrop","Background surface unavailable",error);reported=true }
            destroy(display)
        }
        finally { EGL14.eglMakeCurrent(display,previousDraw,previousRead,context) }
    }
    fun release() { destroy(EGL14.eglGetCurrentDisplay());program.delete();target.release() }
    companion object {
        private const val VERTEX="""attribute vec4 aPosition; varying vec2 vUv; void main(){gl_Position=aPosition;vUv=aPosition.xy*.5+.5;}"""
        private const val FRAGMENT="""precision mediump float;varying vec2 vUv;uniform sampler2D uTexture;uniform float uInputAspect;uniform float uViewAspect;
            void main(){vec2 crop=vec2(min(1.,uViewAspect/uInputAspect),min(1.,uInputAspect/uViewAspect));gl_FragColor=texture2D(uTexture,(vUv-.5)*crop+.5);}"""
    }
}

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.*
import androidx.media3.common.util.*
import androidx.media3.effect.*
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.MutableStateFlow

/** One decoder; a stable-aspect sharp frame and an independent low-resolution Gaussian backdrop. */
internal class LetterboxBlurEffect(private val target:VideoBackdropTarget):GlEffect {
    val outputSize=MutableStateFlow<IntSize?>(null)
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram=Program(this,target,useHdr)
    private class Program(private val effect:LetterboxBlurEffect,target:VideoBackdropTarget,hdr:Boolean):BaseGlShaderProgram(hdr,2) {
        private val blur=GlProgram(VERTEX,GAUSSIAN)
        private val copy=GlProgram(VERTEX,COPY)
        private val backdrop=GpuBackdropOutput(target)
        private var provider:GlObjectsProvider?=null
        private var inputAspect=1f;private var width=1;private var height=1
        private var outputWidth=1;private var outputHeight=1
        private var xTexture=0;private var yTexture=0;private var xFbo=0;private var yFbo=0
        init { for(program in listOf(blur,copy))program.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4) }
        override fun configure(inputWidth:Int,inputHeight:Int):Size {
            releaseBuffers();inputAspect=inputWidth.toFloat()/inputHeight
            val factor=128f/maxOf(inputWidth,inputHeight);width=(inputWidth*factor).toInt().coerceAtLeast(1);height=(inputHeight*factor).toInt().coerceAtLeast(1)
            xTexture=GlUtil.createTexture(width,height,false);yTexture=GlUtil.createTexture(width,height,false)
            xFbo=GlUtil.createFboForTexture(xTexture);yFbo=GlUtil.createFboForTexture(yTexture)
            val reduction=minOf(1f,1440f/maxOf(inputWidth,inputHeight))
            outputWidth=(inputWidth*reduction).toInt().coerceAtLeast(1);outputHeight=(inputHeight*reduction).toInt().coerceAtLeast(1)
            effect.outputSize.value=IntSize(outputWidth,outputHeight)
            return Size(outputWidth,outputHeight)
        }
        override fun queueInputFrame(objects:GlObjectsProvider,input:GlTextureInfo,timeUs:Long) {
            provider=objects;super.queueInputFrame(objects,input,timeUs)
        }
        private fun drawBlur(texture:Int,fbo:Int,step:FloatArray) {
            GlUtil.focusFramebufferUsingCurrentContext(fbo,width,height);blur.use()
            blur.setSamplerTexIdUniform("uTexture",texture,0);blur.setFloatsUniform("uStep",step);blur.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        }
        override fun drawFrame(inputTexId:Int,presentationTimeUs:Long) {
            val output=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,output,0)
            // Keep a backdrop frame even in FILL mode so a paused switch to FIT is immediate.
            drawBlur(inputTexId,xFbo,floatArrayOf(1f/width,0f));drawBlur(xTexture,yFbo,floatArrayOf(0f,1f/height))
            provider?.let { backdrop.draw(it,yTexture,inputAspect) }
            GlUtil.focusFramebufferUsingCurrentContext(output[0],outputWidth,outputHeight);copy.use()
            copy.setSamplerTexIdUniform("uTexture",inputTexId,0);copy.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
        }
        private fun releaseBuffers() { if(xFbo!=0)GlUtil.deleteFbo(xFbo);if(yFbo!=0)GlUtil.deleteFbo(yFbo)
            if(xTexture!=0)GlUtil.deleteTexture(xTexture);if(yTexture!=0)GlUtil.deleteTexture(yTexture);xTexture=0;yTexture=0;xFbo=0;yFbo=0 }
        override fun release() { try { backdrop.release();releaseBuffers();blur.delete();copy.delete() } finally { super.release() } }
    }
    companion object {
        private const val VERTEX="""attribute vec4 aPosition; varying vec2 vUv; void main(){gl_Position=aPosition;vUv=aPosition.xy*.5+.5;}"""
        private const val COPY="""precision mediump float;varying vec2 vUv;uniform sampler2D uTexture;void main(){gl_FragColor=texture2D(uTexture,vUv);}"""
        private const val GAUSSIAN="""precision mediump float;varying vec2 vUv;uniform sampler2D uTexture;uniform vec2 uStep;
            void main(){vec4 color=vec4(0.);float total=0.;for(int i=-12;i<=12;i++){float x=float(i);float weight=exp(-x*x/32.);
                color+=texture2D(uTexture,clamp(vUv+uStep*x,vec2(.001),vec2(.999)))*weight;total+=weight;}gl_FragColor=color/total;}"""
    }
}

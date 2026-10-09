@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.*
import androidx.media3.effect.*
import com.kigtts.next.media.MediaScale

/** One decoder, one GPU frame graph. Low-resolution Gaussian passes never read pixels back to CPU. */
class LetterboxBlurEffect:GlEffect {
    @Volatile var aspect=1f
    @Volatile var scale=MediaScale.FIT
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram=Program(this,useHdr)
    private class Program(private val effect:LetterboxBlurEffect,hdr:Boolean):BaseGlShaderProgram(hdr,2) {
        private val blur=GlProgram(VERTEX,GAUSSIAN)
        private val composite=GlProgram(VERTEX,COMPOSITE)
        private val pool=GpuFramePool(hdr)
        private var inputAspect=1f;private var side=1;private var width=1;private var height=1
        private var sourceWidth=0;private var sourceHeight=0;private var outputWidth=1;private var outputHeight=1
        private var xTexture=0;private var yTexture=0;private var xFbo=0;private var yFbo=0
        init { for(program in listOf(blur,composite))program.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4) }
        override fun configure(inputWidth:Int,inputHeight:Int):Size {
            releaseBuffers();inputAspect=inputWidth.toFloat()/inputHeight
            val factor=128f/maxOf(inputWidth,inputHeight);width=(inputWidth*factor).toInt().coerceAtLeast(1);height=(inputHeight*factor).toInt().coerceAtLeast(1)
            xTexture=GlUtil.createTexture(width,height,false);yTexture=GlUtil.createTexture(width,height,false)
            xFbo=GlUtil.createFboForTexture(xTexture);yFbo=GlUtil.createFboForTexture(yTexture)
            side=maxOf(inputWidth,inputHeight).coerceIn(256,1440)
            return Size(side,side)
        }
        override fun queueInputFrame(provider:GlObjectsProvider,input:GlTextureInfo,timeUs:Long) {
            try {
                if(sourceWidth!=input.width||sourceHeight!=input.height) {
                    configure(input.width,input.height);sourceWidth=input.width;sourceHeight=input.height
                }
                val ratio=effect.aspect.coerceIn(.05f,20f)
                outputWidth=if(ratio>=1f)side else (side*ratio).toInt().coerceAtLeast(1)
                outputHeight=if(ratio>=1f)(side/ratio).toInt().coerceAtLeast(1) else side
                val output=pool.acquire(provider,outputWidth,outputHeight)
                GlUtil.focusFramebufferUsingCurrentContext(output.fboId,outputWidth,outputHeight)
                drawFrame(input.texId,timeUs)
                getInputListener().onInputFrameProcessed(input)
                getOutputListener().onOutputFrameAvailable(output,timeUs)
            } catch(error:Exception) { onError(error) }
        }
        override fun releaseOutputFrame(output:GlTextureInfo) { if(pool.recycle(output))getInputListener().onReadyToAcceptInputFrame() }
        override fun flush() { pool.flush();super.flush() }
        private fun drawBlur(texture:Int,fbo:Int,step:FloatArray) {
            GlUtil.focusFramebufferUsingCurrentContext(fbo,width,height);blur.use()
            blur.setSamplerTexIdUniform("uTexture",texture,0);blur.setFloatsUniform("uStep",step);blur.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        }
        override fun drawFrame(inputTexId:Int,presentationTimeUs:Long) {
            val output=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,output,0)
            val fit=effect.scale==MediaScale.FIT
            if(fit) { drawBlur(inputTexId,xFbo,floatArrayOf(1f/width,0f));drawBlur(xTexture,yFbo,floatArrayOf(0f,1f/height)) }
            GlUtil.focusFramebufferUsingCurrentContext(output[0],outputWidth,outputHeight);composite.use()
            composite.setSamplerTexIdUniform("uTexture",inputTexId,0);composite.setSamplerTexIdUniform("uBlur",if(fit)yTexture else inputTexId,1)
            composite.setFloatUniform("uInputAspect",inputAspect);composite.setFloatUniform("uViewAspect",effect.aspect.coerceAtLeast(.01f))
            composite.setFloatUniform("uFit",if(fit)1f else 0f);composite.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
        }
        private fun releaseBuffers() { if(xFbo!=0)GlUtil.deleteFbo(xFbo);if(yFbo!=0)GlUtil.deleteFbo(yFbo)
            if(xTexture!=0)GlUtil.deleteTexture(xTexture);if(yTexture!=0)GlUtil.deleteTexture(yTexture);xTexture=0;yTexture=0;xFbo=0;yFbo=0 }
        override fun release() { try { pool.release();releaseBuffers();blur.delete();composite.delete() } finally { super.release() } }
    }
    companion object {
        private const val VERTEX="""attribute vec4 aPosition; varying vec2 vUv; void main(){ gl_Position=aPosition; vUv=aPosition.xy*0.5+0.5; }"""
        private const val GAUSSIAN="""precision mediump float; varying vec2 vUv; uniform sampler2D uTexture; uniform vec2 uStep;
            void main(){vec4 color=vec4(0.0);float total=0.0;for(int i=-12;i<=12;i++){float x=float(i);float weight=exp(-x*x/32.0);
                color+=texture2D(uTexture,clamp(vUv+uStep*x,vec2(0.001),vec2(0.999)))*weight;total+=weight;} gl_FragColor=color/total;}"""
        private const val COMPOSITE="""precision mediump float; varying vec2 vUv;uniform sampler2D uTexture;uniform sampler2D uBlur;
            uniform float uInputAspect;uniform float uViewAspect;uniform float uFit;
            void main(){ vec2 crop=vec2(min(1.0,uViewAspect/uInputAspect),min(1.0,uInputAspect/uViewAspect));
                vec2 bg=(vUv-0.5)*crop+0.5;
                if(uFit<0.5){gl_FragColor=texture2D(uTexture,bg);return;}
                vec2 fit=vec2(min(1.0,uInputAspect/uViewAspect),min(1.0,uViewAspect/uInputAspect));vec2 fg=(vUv-0.5)/fit+0.5;
                if(fg.x>=0.0&&fg.x<=1.0&&fg.y>=0.0&&fg.y<=1.0)gl_FragColor=texture2D(uTexture,fg);
                else gl_FragColor=texture2D(uBlur,bg);} """
    }
}

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.cards

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.util.*
import androidx.media3.effect.*
import com.kigtts.plugin.api.PhotoBackdropMode
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidFrameStyle

/** Streaming GPU composition: two tiny blur buffers and one immutable paper/lettering/sticker texture. */
internal class CardMotionPaperEffect(private val card: PolaroidCard, private val frame: PolaroidFrameStyle,
    private val overlay: Bitmap, private val colorMatrix: FloatArray, private val includeFrame: Boolean) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        require(!useHdr) { "暂不支持 HDR 实况照片" }
        return Program()
    }
    private inner class Program : BaseGlShaderProgram(false, 2) {
        private val paper = GlProgram(MotionPaperShaders.VERTEX, MotionPaperShaders.PAPER)
        private val blur = GlProgram(MotionPaperShaders.VERTEX, MotionPaperShaders.GAUSSIAN)
        private val output = MotionPaperGeometry.create(frame, overlay.width.coerceAtLeast(2) / 2 * 2,
            overlay.height.coerceAtLeast(2) / 2 * 2, includeFrame)
        private var overlayTexture = 0; private var xTexture = 0; private var yTexture = 0
        private var xFbo = 0; private var yFbo = 0; private var blurWidth = 1; private var blurHeight = 1
        init {
            listOf(paper, blur).forEach { it.setBufferAttribute("aPosition", GlUtil.getNormalizedCoordinateBounds(), 4) }
            val names = IntArray(1); GLES20.glGenTextures(1, names, 0); overlayTexture = names[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlay, 0)
        }
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            releaseBuffers()
            val foreground = output.foreground(card, inputWidth, inputHeight)
            val photo = output.photo
            val cover = maxOf(photo.width() / inputWidth, photo.height() / inputHeight)
            paper.setFloatsUniform("uOutputSize", floatArrayOf(output.width.toFloat(), output.height.toFloat()))
            paper.setFloatsUniform("uPhoto", floatArrayOf(photo.left, photo.top, photo.width(), photo.height()))
            paper.setFloatsUniform("uForeground", floatArrayOf(foreground.left, foreground.top, foreground.width(), foreground.height()))
            paper.setFloatsUniform("uBackdrop", floatArrayOf(photo.centerX() - inputWidth * cover / 2f,
                photo.centerY() - inputHeight * cover / 2f, inputWidth * cover, inputHeight * cover))
            paper.setFloatsUniform("uRadius", floatArrayOf(output.radius))
            paper.setFloatsUniform("uBlurred", floatArrayOf(if (card.photoBackdrop == PhotoBackdropMode.BLUR) 1f else 0f))
            (0..2).forEach { row -> paper.setFloatsUniform("uRow$row", colorMatrix.copyOfRange(row * 5, row * 5 + 4)) }
            paper.setFloatsUniform("uBias", floatArrayOf(colorMatrix[4] / 255f, colorMatrix[9] / 255f, colorMatrix[14] / 255f))
            val factor = 128f / maxOf(inputWidth, inputHeight)
            blurWidth = (inputWidth * factor).toInt().coerceAtLeast(1); blurHeight = (inputHeight * factor).toInt().coerceAtLeast(1)
            xTexture = GlUtil.createTexture(blurWidth, blurHeight, false); yTexture = GlUtil.createTexture(blurWidth, blurHeight, false)
            xFbo = GlUtil.createFboForTexture(xTexture); yFbo = GlUtil.createFboForTexture(yTexture)
            return Size(output.width, output.height)
        }
        private fun drawBlur(texture: Int, framebuffer: Int, step: FloatArray) {
            GlUtil.focusFramebufferUsingCurrentContext(framebuffer, blurWidth, blurHeight); blur.use()
            blur.setSamplerTexIdUniform("uTexture", texture, 0); blur.setFloatsUniform("uStep", step)
            blur.bindAttributesAndUniforms(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val framebuffer = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, framebuffer, 0)
            if (card.photoBackdrop == PhotoBackdropMode.BLUR) {
                drawBlur(inputTexId, xFbo, floatArrayOf(1f / blurWidth, 0f))
                drawBlur(xTexture, yFbo, floatArrayOf(0f, 1f / blurHeight))
            }
            GlUtil.focusFramebufferUsingCurrentContext(framebuffer[0], output.width, output.height); paper.use()
            paper.setSamplerTexIdUniform("uVideo", inputTexId, 0)
            paper.setSamplerTexIdUniform("uBlur", if (card.photoBackdrop == PhotoBackdropMode.BLUR) yTexture else inputTexId, 1)
            paper.setSamplerTexIdUniform("uOverlay", overlayTexture, 2)
            paper.bindAttributesAndUniforms(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GlUtil.checkGlError()
        }
        private fun releaseBuffers() {
            if (xFbo != 0) GlUtil.deleteFbo(xFbo); if (yFbo != 0) GlUtil.deleteFbo(yFbo)
            if (xTexture != 0) GlUtil.deleteTexture(xTexture); if (yTexture != 0) GlUtil.deleteTexture(yTexture)
            xFbo = 0; yFbo = 0; xTexture = 0; yTexture = 0
        }
        override fun release() { try { releaseBuffers(); if (overlayTexture != 0) GlUtil.deleteTexture(overlayTexture); paper.delete(); blur.delete() } finally { super.release() } }
    }
}

package com.kigtts.next.media.color

import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.Closeable

/** Owned by one GL context/thread; upload only when the selected LUT identity changes. */
internal class GlLutTexture(private val program: Int, private val unit: Int = 1) : Closeable {
    private var texture = 0
    private var selected: CubeLut? = null
    val textureId get() = texture
    private fun uniform(name: String) = GLES20.glGetUniformLocation(program, name)
    fun bind(lut: CubeLut?, strength: Float) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        if (texture == 0) {
            val names = IntArray(1); GLES20.glGenTextures(1, names, 0); texture = names[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.allocateDirect(4))
        } else GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        if (lut != null && selected !== lut) {
            val atlas = lut.atlas()
            try { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlas, 0) } finally { atlas.recycle() }
            selected = lut
        }
        GLES20.glUniform1i(uniform("uLut"), unit)
        GLES20.glUniform1f(uniform("uLutStrength"), if (lut == null) 0f else strength.coerceIn(0f, 1f))
        if (lut != null) {
            GLES20.glUniform1f(uniform("uLutSize"), lut.size.toFloat()); GLES20.glUniform1f(uniform("uLutTiles"), lut.tiles.toFloat())
            GLES20.glUniform3fv(uniform("uLutDomainMin"), 1, lut.domainMin, 0)
            GLES20.glUniform3fv(uniform("uLutDomainSpan"), 1, FloatArray(3) { lut.domainMax[it] - lut.domainMin[it] }, 0)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
    override fun close() { if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0); texture = 0; selected = null }
}

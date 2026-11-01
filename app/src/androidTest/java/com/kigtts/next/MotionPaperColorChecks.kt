package com.kigtts.next

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import org.json.JSONArray
import org.junit.Assert.assertEquals
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads back the production composition shader, including Android's bitmap premultiplication. */
internal fun assertMotionPaperColors(program: Int) {
    val cases = JSONArray(MOTION_PAPER_COLOR_CASES)
    val identity = floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f)
    val textures = IntArray(3)
    GLES20.glGenTextures(textures.size, textures, 0)
    GLES20.glUseProgram(program)
    GLES20.glDisable(GLES20.GL_BLEND)
    GLES20.glDisable(GLES20.GL_DITHER)
    val vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,0f,1f, -1f,1f,0f,1f, 1f,1f,0f,1f)); position(0)
    }
    val position = GLES20.glGetAttribLocation(program, "aPosition")
    GLES20.glEnableVertexAttribArray(position)
    GLES20.glVertexAttribPointer(position, 4, GLES20.GL_FLOAT, false, 0, vertices)
    fun floats(name: String, vararg values: Float) {
        val location = GLES20.glGetUniformLocation(program, name)
        when (values.size) {
            1 -> GLES20.glUniform1fv(location, 1, values, 0)
            2 -> GLES20.glUniform2fv(location, 1, values, 0)
            3 -> GLES20.glUniform3fv(location, 1, values, 0)
            4 -> GLES20.glUniform4fv(location, 1, values, 0)
        }
    }
    fun texture(index: Int, color: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + index)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[index])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0) } finally { bitmap.recycle() }
    }
    fun rgba(values: JSONArray): Int = (values.getInt(3) shl 24) or (values.getInt(0) shl 16) or
        (values.getInt(1) shl 8) or values.getInt(2)
    try {
        floats("uOutputSize", 8f, 8f)
        floats("uBackdrop", 0f, 0f, 8f, 8f)
        floats("uRadius", 0f); floats("uEffects", 0f, 0f, 0f, 0f)
        floats("uLutStrength", 0f); floats("uLutSize", 1f); floats("uLutTiles", 1f)
        floats("uLutDomainMin", 0f, 0f, 0f); floats("uLutDomainSpan", 1f, 1f, 1f)
        for ((name, index) in listOf("uVideo" to 0, "uBlur" to 0, "uOverlay" to 1, "uLut" to 2))
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, name), index)
        texture(2, -1)
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            texture(0, rgba(case.getJSONArray("video"))); texture(1, rgba(case.getJSONArray("overlay")))
            floats("uPhoto", if (case.optBoolean("gutter")) 20f else 0f, 0f, 8f, 8f)
            floats("uForeground", if (case.optBoolean("blur")) 20f else 0f, 0f, 8f, 8f)
            floats("uBlurred", if (case.optBoolean("blur")) 1f else 0f)
            val matrix = case.optJSONArray("matrix")
            fun coefficient(index: Int) = matrix?.getDouble(index)?.toFloat() ?: identity[index]
            for (row in 0..2) floats("uRow$row", *FloatArray(4) { coefficient(row * 5 + it) })
            floats("uBias", *FloatArray(3) { coefficient(it * 5 + 4) / 255f })
            GLES20.glViewport(0, 0, 8, 8); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            val pixel = ByteBuffer.allocateDirect(4)
            GLES20.glReadPixels(4, 4, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
            assertEquals(case.getString("name"), GLES20.GL_NO_ERROR, GLES20.glGetError())
            val expected = case.getJSONArray("expected")
            for (channel in 0..3) assertEquals("${case.getString("name")} channel $channel",
                expected.getDouble(channel), (pixel.get(channel).toInt() and 255).toDouble(), 2.0)
        }
    } finally { GLES20.glDeleteTextures(textures.size, textures, 0) }
}

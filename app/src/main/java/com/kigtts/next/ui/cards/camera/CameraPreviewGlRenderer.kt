package com.kigtts.next.ui.cards.camera

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Size
import android.view.Surface
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal class CameraGlInput(val textureId: Int, val texture: SurfaceTexture, val surface: Surface)
internal class CameraGlOutput(val eglSurface: EGLSurface, val size: Size)

internal interface CameraPreviewRenderer : Closeable {
    fun initialize()
    fun createInput(size: Size): CameraGlInput
    fun createOutput(surface: Surface, size: Size): CameraGlOutput
    fun latch(input: CameraGlInput, transform: FloatArray)
    fun draw(input: CameraGlInput, output: CameraGlOutput, transform: FloatArray, colors: CameraPreviewUniforms)
    fun releaseInput(input: CameraGlInput)
    fun releaseOutput(output: CameraGlOutput)
}

/** One GL-thread EGL context copies camera OES frames directly into PreviewView's output surface. */
internal class CameraPreviewGlRenderer : CameraPreviewRenderer {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var placeholder: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var lutTexture: com.kigtts.next.media.color.GlLutTexture? = null
    private val positions = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val coordinates = floats(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    private var positionLocation = -1; private var coordinateLocation = -1
    private var matrixLocation = -1; private var textureLocation = -1
    private var biasLocation = -1; private val rowLocations = IntArray(4)
    private var initialized = false
    private var displayInitialized = false

    override fun initialize() {
        check(!initialized)
        check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) { "EGL display unavailable" }
        displayInitialized = true
        try {
            val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0,
                configs, 0, 1, count, 0) && count[0] > 0) { "EGL config unavailable" }
            config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "EGL context unavailable" }
            placeholder = EGL14.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(placeholder != EGL14.EGL_NO_SURFACE) { "EGL placeholder unavailable" }
            makeCurrent(placeholder)
            program = link(CameraPreviewShader.VERTEX, CameraPreviewShader.FRAGMENT)
            lutTexture = com.kigtts.next.media.color.GlLutTexture(program)
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
            coordinateLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
            matrixLocation = GLES20.glGetUniformLocation(program, "uTextureMatrix")
            textureLocation = GLES20.glGetUniformLocation(program, "uTexture")
            biasLocation = GLES20.glGetUniformLocation(program, "uBias")
            for (row in 0..3) rowLocations[row] = GLES20.glGetUniformLocation(program, "uRow$row")
            checkGl(); initialized = true
        } catch (failure: Throwable) { close(); throw failure }
    }
    override fun createInput(size: Size): CameraGlInput {
        makeCurrent(placeholder)
        val names = IntArray(1); GLES20.glGenTextures(1, names, 0)
        val textureId = names[0]; check(textureId != 0)
        try {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            checkGl()
            val texture = SurfaceTexture(textureId)
            try {
                texture.setDefaultBufferSize(size.width, size.height)
                return CameraGlInput(textureId, texture, Surface(texture))
            } catch (failure: Throwable) { texture.release(); throw failure }
        } catch (failure: Throwable) { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0); throw failure }
    }
    override fun createOutput(surface: Surface, size: Size): CameraGlOutput {
        check(surface.isValid && size.width > 0 && size.height > 0)
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(egl != EGL14.EGL_NO_SURFACE) { "EGL output unavailable: ${EGL14.eglGetError()}" }
        return CameraGlOutput(egl, size)
    }
    override fun latch(input: CameraGlInput, transform: FloatArray) {
        makeCurrent(placeholder)
        input.texture.updateTexImage(); input.texture.getTransformMatrix(transform)
    }
    override fun draw(input: CameraGlInput, output: CameraGlOutput, transform: FloatArray, colors: CameraPreviewUniforms) {
        makeCurrent(output.eglSurface)
        GLES20.glViewport(0, 0, output.size.width, output.size.height)
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, input.textureId)
        GLES20.glUniform1i(textureLocation, 0); GLES20.glUniformMatrix4fv(matrixLocation, 1, false, transform, 0)
        for (row in 0..3) GLES20.glUniform4fv(rowLocations[row], 1, colors.rows, row * 4)
        GLES20.glUniform4fv(biasLocation, 1, colors.bias, 0)
        lutTexture?.bind(colors.lut, colors.lutStrength)
        positions.position(0); coordinates.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation); GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glEnableVertexAttribArray(coordinateLocation); GLES20.glVertexAttribPointer(coordinateLocation, 2, GLES20.GL_FLOAT, false, 0, coordinates)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); checkGl()
        check(EGL14.eglSwapBuffers(display, output.eglSurface)) { "EGL swap failed: ${EGL14.eglGetError()}" }
    }
    override fun releaseInput(input: CameraGlInput) {
        releaseCameraPreviewInput(input, { makeCurrent(placeholder) }) { texture ->
            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        }
    }
    override fun releaseOutput(output: CameraGlOutput) {
        var first: Throwable? = null
        try { makeCurrent(placeholder) } catch (failure: Throwable) { first = failure }
        try {
            check(EGL14.eglDestroySurface(display, output.eglSurface)) { "EGL output release failed: ${EGL14.eglGetError()}" }
        } catch (failure: Throwable) {
            val initial = first
            if (initial == null) first = failure else if (initial !== failure) initial.addSuppressed(failure)
        }
        first?.let { throw it }
    }
    override fun close() {
        if (context != EGL14.EGL_NO_CONTEXT && placeholder != EGL14.EGL_NO_SURFACE) {
            runCatching { makeCurrent(placeholder); lutTexture?.close(); if (program != 0) GLES20.glDeleteProgram(program) }
        }
        program = 0; lutTexture = null
        if (displayInitialized) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (placeholder != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, placeholder)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread(); EGL14.eglTerminate(display)
        }
        placeholder = EGL14.EGL_NO_SURFACE; context = EGL14.EGL_NO_CONTEXT; initialized = false; displayInitialized = false
    }
    private fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL current failed: ${EGL14.eglGetError()}" }
    }
    private fun link(vertex: String, fragment: String): Int {
        val shaders = mutableListOf<Int>(); var linked = 0
        try {
            for ((type, source) in listOf(GLES20.GL_VERTEX_SHADER to vertex, GLES20.GL_FRAGMENT_SHADER to fragment)) {
                val shader = GLES20.glCreateShader(type); shaders += shader
                GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
                val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { "Preview shader: ${GLES20.glGetShaderInfoLog(shader)}" }
            }
            linked = GLES20.glCreateProgram(); shaders.forEach { GLES20.glAttachShader(linked, it) }; GLES20.glLinkProgram(linked)
            val status = IntArray(1); GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Preview link: ${GLES20.glGetProgramInfoLog(linked)}" }
            return linked
        } catch (failure: Throwable) { if (linked != 0) GLES20.glDeleteProgram(linked); throw failure }
        finally { shaders.forEach(GLES20::glDeleteShader) }
    }
    private fun checkGl() { val error = GLES20.glGetError(); check(error == GLES20.GL_NO_ERROR) { "Preview GL error $error" } }
    private fun floats(vararg values: Float): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
}

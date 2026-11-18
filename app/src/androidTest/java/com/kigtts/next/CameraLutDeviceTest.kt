package com.kigtts.next

import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.media.ImageReader
import android.opengl.*
import android.os.SystemClock
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kigtts.next.media.color.*
import com.kigtts.next.ui.cards.camera.CameraPreviewShader
import com.kigtts.next.ui.cards.camera.CameraPreviewColors
import com.kigtts.next.ui.cards.camera.CameraPreviewGlRenderer
import com.kigtts.next.cards.MotionPaperShaders
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.StringReader

/** Hardware shader/link/pixel check, independent of the user's camera and photo library. */
@RunWith(AndroidJUnit4::class)
class CameraLutDeviceTest {
    @Test fun theRecordableRendererSpreadsBloomFromAnOesFrame() {
        val size = Size(128, 128)
        CameraPreviewGlRenderer(recordable = true).use { renderer ->
            renderer.initialize()
            val input = renderer.createInput(size)
            try {
                ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2).use { reader ->
                    val output = renderer.createOutput(reader.surface, size)
                    try {
                        val canvas = input.surface.lockCanvas(null)
                        canvas.drawColor(Color.rgb(20, 20, 20))
                        canvas.drawCircle(64f, 64f, 12f, Paint().apply { color = Color.rgb(255, 229, 151) })
                        input.surface.unlockCanvasAndPost(canvas)
                        val transform = FloatArray(16)
                        renderer.latch(input, transform)
                        val colors = CameraPreviewColors().snapshot()
                        renderer.draw(input, output, transform, colors.copy(effects = PhotoEffects(bloom = 1f)), false)
                        val deadline = SystemClock.elapsedRealtime() + 2000L
                        var frame = reader.acquireLatestImage()
                        while (frame == null && SystemClock.elapsedRealtime() < deadline) {
                            SystemClock.sleep(10)
                            frame = reader.acquireLatestImage()
                        }
                        requireNotNull(frame).use { image ->
                            val plane = image.planes.single()
                            fun channel(x: Int, y: Int, index: Int) =
                                plane.buffer.get(y * plane.rowStride + x * plane.pixelStride + index).toInt() and 255
                            val halo = channel(80, 64, 0)
                            val profile = (64..92 step 2).joinToString { x -> "$x=${channel(x, 64, 0)}" }
                            assertTrue("Bloom highlight profile: $profile", channel(78, 64, 0) > 45 && halo > 28)
                            assertTrue(halo > channel(80, 64, 2))
                            assertTrue(halo > channel(90, 64, 0))
                            assertEquals(20.0, channel(32, 64, 0).toDouble(), 2.0)
                            assertEquals(input.texture.timestamp, image.timestamp)
                        }
                    } finally { renderer.releaseOutput(output) }
                }
            } finally { renderer.releaseInput(input) }
        }
    }

    @Test fun actualGlesLutMatchesCpuAndBothProductionShadersLink() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, IntArray(1), 0))
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 8, EGL14.EGL_HEIGHT, 8, EGL14.EGL_NONE), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
        val programs = mutableListOf<Int>()
        try {
            programs += link(CameraPreviewShader.VERTEX, CameraPreviewShader.FRAGMENT)
            programs += link(MotionPaperShaders.VERTEX, MotionPaperShaders.PAPER)
            assertMotionPaperColors(programs[1])
            val program = link("attribute vec2 p;void main(){gl_Position=vec4(p,0.,1.);}",
                "precision highp float;uniform vec3 source;" + LutShader.GLSL + "void main(){gl_FragColor=vec4(applyLut(source),1.);}")
            programs += program; GLES20.glUseProgram(program)
            val lut = CubeLut.read(StringReader(buildString {
                append("LUT_3D_SIZE 2\n")
                for (b in 0..1) for (g in 0..1) for (r in 0..1) append("${1-r} ${1-g} ${1-b}\n")
            }))
            GlLutTexture(program).use { texture ->
                val vertices = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)); position(0) }
                val attribute = GLES20.glGetAttribLocation(program, "p")
                GLES20.glEnableVertexAttribArray(attribute); GLES20.glVertexAttribPointer(attribute, 2, GLES20.GL_FLOAT, false, 0, vertices)
                GLES20.glViewport(0, 0, 8, 8)
                for (strength in listOf(0f, .35f, 1f)) for (color in listOf(0xff2579bd.toInt(), 0xffe0137f.toInt(), -1, 0xff000000.toInt())) {
                    texture.bind(lut, strength)
                    GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "source"), (color ushr 16 and 255)/255f, (color ushr 8 and 255)/255f, (color and 255)/255f)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    val result = ByteBuffer.allocateDirect(4)
                    GLES20.glReadPixels(4, 4, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, result)
                    assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
                    val expected = lut.map(color, strength)
                    for (channel in 0..2) assertEquals((expected ushr (16-channel*8) and 255).toDouble(), (result.get(channel).toInt() and 255).toDouble(), 2.0)
                }
            }
        } finally {
            programs.forEach(GLES20::glDeleteProgram)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, context); EGL14.eglTerminate(display)
        }
    }
    private fun link(vertex: String, fragment: String): Int {
        fun shader(kind: Int, text: String): Int = GLES20.glCreateShader(kind).also { shader ->
            GLES20.glShaderSource(shader, text); GLES20.glCompileShader(shader)
            val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            assertEquals(GLES20.glGetShaderInfoLog(shader), GLES20.GL_TRUE, status[0])
        }
        val v = shader(GLES20.GL_VERTEX_SHADER, vertex); val f = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        try { return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, v); GLES20.glAttachShader(program, f); GLES20.glLinkProgram(program)
            val status = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            assertEquals(GLES20.glGetProgramInfoLog(program), GLES20.GL_TRUE, status[0])
        } } finally { GLES20.glDeleteShader(v); GLES20.glDeleteShader(f) }
    }
}

/** Optional adb app_process entry: pure EGL math, no install, permissions, app data or camera. */
object CameraLutDeviceMain {
    @JvmStatic fun main(args: Array<String>) {
        CameraLutDeviceTest().actualGlesLutMatchesCpuAndBothProductionShadersLink()
        CameraLutDeviceTest().theRecordableRendererSpreadsBloomFromAnOesFrame()
        println("KIGTTS_GPU_LUT_OK: production shaders linked; GPU/CPU pixels agree")
    }
}

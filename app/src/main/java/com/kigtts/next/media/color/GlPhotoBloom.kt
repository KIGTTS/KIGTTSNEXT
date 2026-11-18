@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.media.color

import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import java.io.Closeable

/** All passes stay in the caller's GL context. The caller restores its output framebuffer and viewport. */
internal class GlPhotoBloom(external: Boolean, width: Int, height: Int) : Closeable {
    private class Target(val width: Int, val height: Int) : Closeable {
        val texture = GlUtil.createTexture(width, height, false)
        val framebuffer = try { GlUtil.createFboForTexture(texture) }
            catch (failure: Throwable) { GlUtil.deleteTexture(texture); throw failure }
        override fun close() {
            GlUtil.deleteFbo(framebuffer)
            GlUtil.deleteTexture(texture)
        }
    }

    private val resources = mutableListOf<Closeable>()
    private val extract: GlProgram
    private val blur: GlProgram
    private val near: Target
    private val nearScratch: Target
    private val far: Target
    private val farScratch: Target
    private val identity = GlUtil.create4x4IdentityMatrix()
    private val step = FloatArray(2)
    private val downsample = FloatArray(2)
    val nearTexture get() = near.texture
    val farTexture get() = far.texture

    init {
        try {
            extract = program(extractShader(external))
            blur = program(GAUSSIAN)
            val (w, h) = PhotoBloom.size(width, height)
            near = Target(w, h).also(resources::add)
            nearScratch = Target(w, h).also(resources::add)
            far = Target((w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1)).also(resources::add)
            farScratch = Target(far.width, far.height).also(resources::add)
            downsample[0] = .25f / w
            downsample[1] = .25f / h
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private fun program(fragment: String): GlProgram = GlProgram(VERTEX, fragment).also {
        resources += Closeable(it::delete)
        it.setBufferAttribute("aPosition", GlUtil.getNormalizedCoordinateBounds(), 4)
    }

    fun render(input: Int, transform: FloatArray = identity) {
        GlUtil.focusFramebufferUsingCurrentContext(near.framebuffer, near.width, near.height)
        extract.use()
        extract.setSamplerTexIdUniform("uInput", input, 0)
        extract.setFloatsUniform("uTransform", transform)
        extract.setFloatsUniform("uOffset", downsample)
        extract.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        blur(near, nearScratch, true)
        blur(nearScratch, near, false)
        blur(near, farScratch, true)
        blur(farScratch, far, false)
    }

    private fun blur(input: Target, output: Target, horizontal: Boolean) {
        GlUtil.focusFramebufferUsingCurrentContext(output.framebuffer, output.width, output.height)
        blur.use()
        blur.setSamplerTexIdUniform("uInput", input.texture, 0)
        step[0] = if (horizontal) 1f / output.width else 0f
        step[1] = if (horizontal) 0f else 1f / output.height
        blur.setFloatsUniform("uStep", step)
        blur.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun close() {
        resources.asReversed().forEach { runCatching { it.close() } }
        resources.clear()
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aPosition;
            varying vec2 vUv;
            void main() { gl_Position=aPosition; vUv=aPosition.xy*.5+.5; }
        """
        const val GAUSSIAN = """
            precision highp float;
            varying vec2 vUv;
            uniform sampler2D uInput;
            uniform vec2 uStep;
            void main() {
                vec3 sum=texture2D(uInput,vUv).rgb*.227027;
                sum+=(texture2D(uInput,vUv+uStep*1.384615).rgb+texture2D(uInput,vUv-uStep*1.384615).rgb)*.316217;
                sum+=(texture2D(uInput,vUv+uStep*3.230769).rgb+texture2D(uInput,vUv-uStep*3.230769).rgb)*.070270;
                gl_FragColor=vec4(sum,1.);
            }
        """
        fun extractShader(external: Boolean): String =
            (if (external) "#extension GL_OES_EGL_image_external : require\n" else "") + """
                precision highp float;
                varying vec2 vUv;
                uniform ${if (external) "samplerExternalOES" else "sampler2D"} uInput;
                uniform mat4 uTransform;
                uniform vec2 uOffset;
            """ + PhotoBloom.EXTRACT + """
                vec3 bright(vec2 uv) {
                    return bloomHighlight(texture2D(uInput,(uTransform*vec4(uv,0.,1.)).xy).rgb);
                }
                void main() {
                    vec2 diagonal=vec2(uOffset.x,-uOffset.y);
                    gl_FragColor=vec4((bright(vUv-uOffset)+bright(vUv+uOffset)+bright(vUv-diagonal)+bright(vUv+diagonal))*.25,1.);
                }
            """
    }
}

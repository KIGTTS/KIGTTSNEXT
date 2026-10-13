package com.kigtts.next.ui.cards.camera

import java.util.concurrent.atomic.AtomicReference

/** ColorMatrix uses byte-domain offsets; OpenGL's encoded camera RGB samples are normalized. */
internal data class CameraPreviewUniforms(val rows: FloatArray, val bias: FloatArray)
internal fun cameraPreviewUniforms(values: FloatArray): CameraPreviewUniforms {
    require(values.size == 20 && values.all(Float::isFinite)) { "预览颜色参数无效" }
    return CameraPreviewUniforms(FloatArray(16) { values[(it / 4) * 5 + it % 4] },
        FloatArray(4) { values[it * 5 + 4] / 255f })
}

/** Animation updates a retained shader's uniforms, never the camera session or its surfaces. */
internal class CameraPreviewColors(initial: FloatArray = PhotoFilters.coefficients(PhotoFilter.ORIGINAL)) {
    private val current = AtomicReference(cameraPreviewUniforms(initial))
    fun update(values: FloatArray) { current.set(cameraPreviewUniforms(values)) }
    fun snapshot(): CameraPreviewUniforms = current.get()
}

internal object CameraPreviewShader {
    const val VERTEX = """attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTextureMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = (uTextureMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }"""
    const val FRAGMENT = """#extension GL_OES_EGL_image_external : require
        precision highp float;
        uniform samplerExternalOES uTexture;
        uniform vec4 uRow0;
        uniform vec4 uRow1;
        uniform vec4 uRow2;
        uniform vec4 uRow3;
        uniform vec4 uBias;
        varying vec2 vTexCoord;
        void main() {
            vec4 source = texture2D(uTexture, vTexCoord);
            gl_FragColor = clamp(vec4(dot(uRow0, source), dot(uRow1, source),
                dot(uRow2, source), dot(uRow3, source)) + uBias, 0.0, 1.0);
        }"""
}

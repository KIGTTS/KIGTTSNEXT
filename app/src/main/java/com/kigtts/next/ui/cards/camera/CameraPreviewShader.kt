package com.kigtts.next.ui.cards.camera

import com.kigtts.next.camera.expressions.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import com.kigtts.next.media.color.CubeLut
import com.kigtts.next.media.color.LutShader
import com.kigtts.next.media.color.PhotoEffects

/** ColorMatrix uses byte-domain offsets; OpenGL's encoded camera RGB samples are normalized. */
internal data class CameraPreviewUniforms(val rows: FloatArray, val bias: FloatArray, val lut: CubeLut? = null, val lutStrength: Float = 0f, val effects: PhotoEffects = PhotoEffects.NONE,
    val expression: CameraExpressionDraw? = null)
internal fun cameraPreviewUniforms(values: FloatArray): CameraPreviewUniforms {
    require(values.size == 20 && values.all(Float::isFinite)) { "预览颜色参数无效" }
    return CameraPreviewUniforms(FloatArray(16) { values[(it / 4) * 5 + it % 4] },
        FloatArray(4) { values[it * 5 + 4] / 255f })
}

/** Animation updates a retained shader's uniforms, never the camera session or its surfaces. */
internal class CameraPreviewColors(initial: FloatArray = PhotoFilters.coefficients(PhotoFilter.ORIGINAL)) {
    private val current = AtomicReference(cameraPreviewUniforms(initial))
    private val warning = AtomicBoolean(false)
    var exposureWarning: Boolean
        get() = warning.get()
        set(value) { warning.set(value) }
    fun update(values: FloatArray, lut: CubeLut? = null, strength: Float = 1f, effects: PhotoEffects = PhotoEffects.NONE) { current.set(cameraPreviewUniforms(values).copy(lut = lut, lutStrength = if (lut == null) 0f else strength.coerceIn(0f, 1f), effects = effects)) }
    @Volatile var faces: CameraFaceAnalysis? = null
    @Volatile var expression: CompiledExpression? = null
    fun expressionDraw(): CameraExpressionDraw? {
        val effect = expression ?: return null
        val frame = faces?.latest()?.takeIf { it.stickerFaces.isNotEmpty() } ?: return null
        return CameraExpressionDraw(effect, frame)
    }

    fun snapshot(): CameraPreviewUniforms {
        val base = current.get()
        return expressionDraw()?.let { base.copy(expression = it) } ?: base
    }
}

internal object CameraPreviewShader {
    const val VERTEX = """attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTextureMatrix;
        varying vec2 vTexCoord;
        varying vec2 vPhotoUv;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = (uTextureMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            vPhotoUv = aTexCoord;
        }"""
    const val FRAGMENT = """#extension GL_OES_EGL_image_external : require
        precision highp float;
        uniform samplerExternalOES uTexture;
        uniform mat4 uTextureMatrix;
        uniform vec4 uRow0;
        uniform vec4 uRow1;
        uniform vec4 uRow2;
        uniform vec4 uRow3;
        uniform vec4 uBias;
        uniform sampler2D uExpression;
        uniform float uExpressionEnabled;
        varying vec2 vTexCoord;
        varying vec2 vPhotoUv;
        """ + LutShader.GLSL + com.kigtts.next.media.color.PhotoEffectsShader.GLSL + CameraExposureWarning.GLSL + """
        vec3 effectSample(vec2 uv) { return texture2D(uTexture,(uTextureMatrix*vec4(uv,0.,1.)).xy).rgb; }
        void main() {
            vec4 source = texture2D(uTexture, vTexCoord);
            float luma = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
            source.rgb = applyPhotoEffects(vPhotoUv,source.rgb);
            vec4 color = clamp(vec4(dot(uRow0, source), dot(uRow1, source),
                dot(uRow2, source), dot(uRow3, source)) + uBias, 0.0, 1.0);
            vec3 result = applyExposureWarning(applyLut(color.rgb), luma);
            if (uExpressionEnabled > 0.5) {
                vec4 art = texture2D(uExpression, vec2(vPhotoUv.x, 1.0 - vPhotoUv.y));
                result = result * (1.0 - art.a) + art.rgb;
            }
            gl_FragColor = vec4(result, color.a);
        }"""
}

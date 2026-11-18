package com.kigtts.next.media.color

import kotlin.math.roundToInt

/** Shared SDR Bloom settings. Radii come from two reduced-resolution Gaussian passes. */
internal object PhotoBloom {
    const val LOW = .55f
    const val HIGH = .87f
    const val NEAR = .72f
    const val FAR = .78f
    val gaussian = floatArrayOf(.227027f, .194595f, .121622f, .054054f, .016216f)

    fun size(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1f, 156f / minOf(width, height), 384f / maxOf(width, height))
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun highlight(red: Float, green: Float, blue: Float): Float {
        val luma = red * .2126f + green * .7152f + blue * .0722f
        val peak = maxOf(red, green, blue)
        val t = ((luma * .65f + peak * .35f - LOW) / (HIGH - LOW)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    const val EXTRACT = """
        vec3 bloomHighlight(vec3 rgb) {
            float luma = dot(rgb, vec3(.2126,.7152,.0722));
            float peak = max(max(rgb.r,rgb.g),rgb.b);
            return rgb * smoothstep($LOW,$HIGH,mix(luma,peak,.35));
        }
    """
    const val COMPOSITE = """
        uniform sampler2D uBloomNear;
        uniform sampler2D uBloomFar;
        vec3 applyBloom(vec2 uv, vec3 color, float strength) {
            vec3 glow = clamp(texture2D(uBloomNear,uv).rgb*$NEAR + texture2D(uBloomFar,uv).rgb*$FAR, 0., 1.);
            return color + (1.-color)*glow*strength;
        }
    """
}

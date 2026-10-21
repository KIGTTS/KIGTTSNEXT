package com.kigtts.next.media.color

/** Bilinear red/green plus linear blue gives trilinear sampling without a 3D texture extension. */
internal object LutShader {
    const val GLSL = """
        uniform sampler2D uLut;
        uniform float uLutSize;
        uniform float uLutTiles;
        uniform float uLutStrength;
        uniform vec3 uLutDomainMin;
        uniform vec3 uLutDomainSpan;
        vec2 lutUv(vec3 p, float slice) {
            vec2 tile = vec2(mod(slice, uLutTiles), floor(slice / uLutTiles));
            return (tile * uLutSize + p.rg * (uLutSize - 1.0) + 0.5) / (uLutTiles * uLutSize);
        }
        vec3 applyLut(vec3 color) {
            if (uLutStrength <= 0.0) return color;
            vec3 p = clamp((color - uLutDomainMin) / uLutDomainSpan, 0.0, 1.0);
            float slice = p.b * (uLutSize - 1.0);
            vec3 low = texture2D(uLut, lutUv(p, floor(slice))).rgb;
            vec3 high = texture2D(uLut, lutUv(p, min(floor(slice) + 1.0, uLutSize - 1.0))).rgb;
            return mix(color, mix(low, high, fract(slice)), uLutStrength);
        }
    """
}

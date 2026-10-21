package com.kigtts.next.media.color

import kotlin.math.floor

/** Two nonlinear mixes avoid the old 101-cell repeating lattice; shared by preview and export. */
internal fun filmGrain(x: Int, y: Int): Float {
    fun mod(value: Float, divisor: Float) = value - floor(value / divisor) * divisor
    val a = mod(x.toFloat(), 251f); val b = floor(x / 251f)
    val c = mod(y.toFloat(), 251f); val d = floor(y / 251f)
    var value = mod(a * a * 73f + c * c * 151f + a * c * 31f + b * 7919f + d * 1543f + 1013f, 65521f)
    repeat(2) {
        val a = mod(value, 251f); val b = floor(value / 251f)
        value = mod(a * a * 53f + b * b * 97f + a * b * 31f + 103f, 65521f)
    }
    return value / 65520f - .5f
}

internal const val FILM_GRAIN_GLSL = """
    float filmGrain(vec2 p) {
        vec2 low=mod(p,251.);vec2 high=floor(p/251.);
        float n=mod(low.x*low.x*73.+low.y*low.y*151.+low.x*low.y*31.+high.x*7919.+high.y*1543.+1013.,65521.);
        for(int i=0;i<2;i++) {
            float a=mod(n,251.);float b=floor(n/251.);
            n=mod(a*a*53.+b*b*97.+a*b*31.+103.,65521.);
        }
        return n/65520.-.5;
    }
"""

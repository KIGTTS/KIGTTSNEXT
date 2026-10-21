package com.kigtts.next.media.color

enum class PhotoEffect(val label: String) { NONE("无特效"), VIGNETTE("暗角"), GRAIN("噪点"), ABERRATION("边缘色散"), BLOOM("辉光") }

/** Independent strengths can be layered; old photographs default to all effects disabled. */
data class PhotoEffects(val vignette: Float = 0f, val grain: Float = 0f, val aberration: Float = 0f, val bloom: Float = 0f) {
    init { require(values().all { it.isFinite() && it in 0f..1f }) }
    val active get() = vignette > 0f || grain > 0f || aberration > 0f || bloom > 0f
    fun values() = floatArrayOf(vignette, grain, aberration, bloom)
    fun amount(effect: PhotoEffect) = when (effect) { PhotoEffect.VIGNETTE -> vignette; PhotoEffect.GRAIN -> grain; PhotoEffect.ABERRATION -> aberration; PhotoEffect.BLOOM -> bloom; else -> 0f }
    fun with(effect: PhotoEffect, amount: Float): PhotoEffects {
        val value = amount.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return when (effect) { PhotoEffect.VIGNETTE -> copy(vignette = value); PhotoEffect.GRAIN -> copy(grain = value); PhotoEffect.ABERRATION -> copy(aberration = value); PhotoEffect.BLOOM -> copy(bloom = value); else -> NONE }
    }
    companion object {
        val NONE = PhotoEffects()
        fun from(values: List<Float>) = PhotoEffects(values.getOrElse(0) { 0f }.safe(), values.getOrElse(1) { 0f }.safe(), values.getOrElse(2) { 0f }.safe(), values.getOrElse(3) { 0f }.safe())
        private fun Float.safe() = takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    }
}

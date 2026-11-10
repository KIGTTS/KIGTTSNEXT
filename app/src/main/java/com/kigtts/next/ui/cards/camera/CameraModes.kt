package com.kigtts.next.ui.cards.camera

internal enum class CameraMode(val label: String) {
    SCAN("扫码"), PRO("专业"), VIDEO("录像"), PHOTO("拍照"), TIME_LAPSE("延时摄影");
    val recordsVideo get() = this == VIDEO || this == TIME_LAPSE
    val takesPhotos get() = this == PHOTO || this == PRO
}

/** Camera crops follow device orientation; no paper size is involved. */
internal enum class CameraAspect(val label: String, private val longSide: Float) {
    STANDARD("4:3", 4f / 3), WIDE("16:9", 16f / 9), SQUARE("1:1", 1f), FULL("全屏", 0f);
    fun ratio(width: Int, height: Int): Float {
        val aspect = if (longSide == 0f) maxOf(width, height).toFloat() / minOf(width, height).coerceAtLeast(1) else longSide
        return if (width > height) aspect else 1f / aspect
    }
}

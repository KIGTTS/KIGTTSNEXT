package com.kigtts.next.cards

import com.kigtts.plugin.api.PolaroidPhotoStyle

internal fun validateCardPhotoStyle(style: PolaroidPhotoStyle, images: Set<String>) {
    stickerRequire(style.filter.length in 1..64 && style.strength.isFinite() && style.strength in 0f..1f &&
        listOf(style.vignette, style.grain, style.aberration, style.bloom).all { it.isFinite() && it in 0f..1f }, message = "滤镜参数不合法")
    stickerRequire(style.lutImageId == null || style.lutImageId in images && style.lutSize in 2..65, message = "滤镜资源不存在")
    stickerRequire(style.lutMin.size == 3 && style.lutMax.size == 3 && (0..2).all {
        style.lutMin[it].isFinite() && style.lutMax[it].isFinite() && style.lutMax[it] > style.lutMin[it] &&
            style.lutMin[it] in -64f..64f && style.lutMax[it] in -64f..64f
    }, message = "滤镜色域不合法")
}

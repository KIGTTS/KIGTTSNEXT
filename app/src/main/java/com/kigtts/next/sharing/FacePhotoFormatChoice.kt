package com.kigtts.next.sharing

import androidx.compose.runtime.Composable
import com.kigtts.next.ui.components.ChoiceButtonGroup

internal enum class FacePhotoFormat(val label: String, val original: Boolean, val composed: Boolean) {
    COMPOSED("编辑后的照片", false, true), ORIGINAL("原图", true, false), BOTH("两种", true, true)
}

@Composable
internal fun FacePhotoFormatChoice(value: FacePhotoFormat, change: (FacePhotoFormat) -> Unit) {
    ChoiceButtonGroup("分享格式", FacePhotoFormat.entries, value, { it.label }, change)
}

package com.kigtts.next.ui.cards.camera

import androidx.camera.extensions.ExtensionMode

internal enum class CameraProcessing(val label: String, val extensionMode: Int?) {
    STANDARD("标准", null), AUTO("自动优化", ExtensionMode.AUTO), HDR("HDR", ExtensionMode.HDR), NIGHT("夜景", ExtensionMode.NIGHT)
}

/** Computational modes are explicit choices, never a fallback ladder that silently enables night capture. */
internal fun cameraExtensionPlan(requested: CameraProcessing, available: Boolean): List<Int?> =
    if (requested.extensionMode != null && available) listOf(requested.extensionMode, null) else listOf(null)

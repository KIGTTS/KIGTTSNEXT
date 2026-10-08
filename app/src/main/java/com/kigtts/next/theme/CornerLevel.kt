package com.kigtts.next.theme

enum class CornerLevel(val label: String, val radius: Float) {
    SMALL("小", 4f), MEDIUM("中", 14f), LARGE("大", 24f);

    companion object {
        fun fromLegacy(radius: Float): CornerLevel =
            if (!radius.isFinite()) LARGE else entries.minBy { kotlin.math.abs(it.radius - radius) }
    }
}

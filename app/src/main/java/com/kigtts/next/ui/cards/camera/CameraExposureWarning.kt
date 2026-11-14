package com.kigtts.next.ui.cards.camera

/** Zebra thresholds in the unfiltered SDR preview, not in RAW sensor data. */
internal object CameraExposureWarning {
    const val SHADOW_LUMA = 0.04f
    const val HIGHLIGHT_LUMA = 0.96f
    const val DISABLED_SHADOW_LUMA = -1f
    const val DISABLED_HIGHLIGHT_LUMA = 2f

    const val GLSL = """
        uniform vec2 uExposureWarning;
        vec3 applyExposureWarning(vec3 displayColor, float luma) {
            // A reversed threshold pair disables the overlay without changing the camera session.
            if (luma <= uExposureWarning.x) {
                float stripe = step(6.0, mod(gl_FragCoord.x + gl_FragCoord.y * 0.4, 12.0));
                return mix(displayColor, vec3(0.29, 0.64, 1.0), stripe * 0.88);
            }
            if (luma >= uExposureWarning.y) {
                float stripe = step(6.0, mod(gl_FragCoord.x - gl_FragCoord.y * 0.4, 12.0));
                return mix(displayColor, vec3(0.84, 0.12, 0.18), stripe * 0.88);
            }
            return displayColor;
        }
    """
}

package com.kigtts.next.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp

@Composable
fun SwitchColors.animateEnabled(enabled: Boolean): SwitchColors {
    val progress = enabledProgress(enabled)
    val checkedThumb = lerp(disabledCheckedThumbColor, checkedThumbColor, progress)
    val checkedTrack = lerp(disabledCheckedTrackColor, checkedTrackColor, progress)
    val checkedBorder = lerp(disabledCheckedBorderColor, checkedBorderColor, progress)
    val checkedIcon = lerp(disabledCheckedIconColor, checkedIconColor, progress)
    val uncheckedThumb = lerp(disabledUncheckedThumbColor, uncheckedThumbColor, progress)
    val uncheckedTrack = lerp(disabledUncheckedTrackColor, uncheckedTrackColor, progress)
    val uncheckedBorder = lerp(disabledUncheckedBorderColor, uncheckedBorderColor, progress)
    val uncheckedIcon = lerp(disabledUncheckedIconColor, uncheckedIconColor, progress)
    return copy(checkedThumbColor = checkedThumb, disabledCheckedThumbColor = checkedThumb,
        checkedTrackColor = checkedTrack, disabledCheckedTrackColor = checkedTrack,
        checkedBorderColor = checkedBorder, disabledCheckedBorderColor = checkedBorder,
        checkedIconColor = checkedIcon, disabledCheckedIconColor = checkedIcon,
        uncheckedThumbColor = uncheckedThumb, disabledUncheckedThumbColor = uncheckedThumb,
        uncheckedTrackColor = uncheckedTrack, disabledUncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = uncheckedBorder, disabledUncheckedBorderColor = uncheckedBorder,
        uncheckedIconColor = uncheckedIcon, disabledUncheckedIconColor = uncheckedIcon)
}

@Composable
fun SliderColors.animateEnabled(enabled: Boolean): SliderColors {
    val progress = enabledProgress(enabled)
    val thumb = lerp(disabledThumbColor, thumbColor, progress)
    val activeTrack = lerp(disabledActiveTrackColor, activeTrackColor, progress)
    val inactiveTrack = lerp(disabledInactiveTrackColor, inactiveTrackColor, progress)
    val activeTick = lerp(disabledActiveTickColor, activeTickColor, progress)
    val inactiveTick = lerp(disabledInactiveTickColor, inactiveTickColor, progress)
    return copy(thumbColor = thumb, disabledThumbColor = thumb,
        activeTrackColor = activeTrack, disabledActiveTrackColor = activeTrack,
        inactiveTrackColor = inactiveTrack, disabledInactiveTrackColor = inactiveTrack,
        activeTickColor = activeTick, disabledActiveTickColor = activeTick,
        inactiveTickColor = inactiveTick, disabledInactiveTickColor = inactiveTick)
}

@Composable
fun MenuItemColors.animateEnabled(enabled: Boolean): MenuItemColors {
    val progress = enabledProgress(enabled)
    val text = lerp(disabledTextColor, textColor, progress)
    val leading = lerp(disabledLeadingIconColor, leadingIconColor, progress)
    val trailing = lerp(disabledTrailingIconColor, trailingIconColor, progress)
    return copy(textColor = text, disabledTextColor = text,
        leadingIconColor = leading, disabledLeadingIconColor = leading,
        trailingIconColor = trailing, disabledTrailingIconColor = trailing)
}

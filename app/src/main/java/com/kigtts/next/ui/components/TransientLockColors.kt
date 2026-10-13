package com.kigtts.next.ui.components

import androidx.compose.material3.*

/** A temporary request/navigation lock disables input and semantics without flashing grey. */
fun ButtonColors.keepColorsWhenLocked() = copy(disabledContainerColor = containerColor, disabledContentColor = contentColor)
fun IconButtonColors.keepColorsWhenLocked() = copy(disabledContainerColor = containerColor, disabledContentColor = contentColor)
fun ToggleButtonColors.keepColorsWhenLocked(checked: Boolean) = copy(
    disabledContainerColor = if (checked) checkedContainerColor else containerColor,
    disabledContentColor = if (checked) checkedContentColor else contentColor)
fun SwitchColors.keepColorsWhenLocked() = copy(disabledCheckedThumbColor = checkedThumbColor,
    disabledCheckedTrackColor = checkedTrackColor, disabledCheckedBorderColor = checkedBorderColor,
    disabledCheckedIconColor = checkedIconColor, disabledUncheckedThumbColor = uncheckedThumbColor,
    disabledUncheckedTrackColor = uncheckedTrackColor, disabledUncheckedBorderColor = uncheckedBorderColor,
    disabledUncheckedIconColor = uncheckedIconColor)

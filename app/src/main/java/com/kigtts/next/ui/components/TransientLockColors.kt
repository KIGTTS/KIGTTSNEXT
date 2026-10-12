package com.kigtts.next.ui.components

import androidx.compose.material3.*

/** A temporary request/navigation lock disables input and semantics without flashing grey. */
fun ButtonColors.keepColorsWhenLocked() = copy(disabledContainerColor = containerColor, disabledContentColor = contentColor)
fun IconButtonColors.keepColorsWhenLocked() = copy(disabledContainerColor = containerColor, disabledContentColor = contentColor)
fun SwitchColors.keepColorsWhenLocked() = copy(disabledCheckedThumbColor = checkedThumbColor,
    disabledCheckedTrackColor = checkedTrackColor, disabledCheckedBorderColor = checkedBorderColor,
    disabledCheckedIconColor = checkedIconColor, disabledUncheckedThumbColor = uncheckedThumbColor,
    disabledUncheckedTrackColor = uncheckedTrackColor, disabledUncheckedBorderColor = uncheckedBorderColor,
    disabledUncheckedIconColor = uncheckedIconColor)

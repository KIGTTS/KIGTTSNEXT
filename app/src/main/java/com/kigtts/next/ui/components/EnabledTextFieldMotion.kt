package com.kigtts.next.ui.components

import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Preserve focus/error palettes while interpolating every disabled field channel. */
@Composable
fun TextFieldColors.animateEnabled(enabled: Boolean, focused: Boolean, isError: Boolean = false): TextFieldColors {
    val progress = enabledProgress(enabled)
    fun color(active: Color, disabled: Color) = lerp(disabled, active, progress)
    return copy(
        focusedTextColor = color(focusedTextColor, disabledTextColor),
        unfocusedTextColor = color(unfocusedTextColor, disabledTextColor),
        errorTextColor = color(errorTextColor, disabledTextColor),
        disabledTextColor = color(if (isError) errorTextColor else if (focused) focusedTextColor else unfocusedTextColor, disabledTextColor),
        focusedContainerColor = color(focusedContainerColor, disabledContainerColor),
        unfocusedContainerColor = color(unfocusedContainerColor, disabledContainerColor),
        errorContainerColor = color(errorContainerColor, disabledContainerColor),
        disabledContainerColor = color(if (isError) errorContainerColor else if (focused) focusedContainerColor else unfocusedContainerColor, disabledContainerColor),
        focusedIndicatorColor = color(focusedIndicatorColor, disabledIndicatorColor),
        unfocusedIndicatorColor = color(unfocusedIndicatorColor, disabledIndicatorColor),
        errorIndicatorColor = color(errorIndicatorColor, disabledIndicatorColor),
        disabledIndicatorColor = color(if (isError) errorIndicatorColor else if (focused) focusedIndicatorColor else unfocusedIndicatorColor, disabledIndicatorColor),
        focusedLeadingIconColor = color(focusedLeadingIconColor, disabledLeadingIconColor),
        unfocusedLeadingIconColor = color(unfocusedLeadingIconColor, disabledLeadingIconColor),
        errorLeadingIconColor = color(errorLeadingIconColor, disabledLeadingIconColor),
        disabledLeadingIconColor = color(if (isError) errorLeadingIconColor else if (focused) focusedLeadingIconColor else unfocusedLeadingIconColor, disabledLeadingIconColor),
        focusedTrailingIconColor = color(focusedTrailingIconColor, disabledTrailingIconColor),
        unfocusedTrailingIconColor = color(unfocusedTrailingIconColor, disabledTrailingIconColor),
        errorTrailingIconColor = color(errorTrailingIconColor, disabledTrailingIconColor),
        disabledTrailingIconColor = color(if (isError) errorTrailingIconColor else if (focused) focusedTrailingIconColor else unfocusedTrailingIconColor, disabledTrailingIconColor),
        focusedLabelColor = color(focusedLabelColor, disabledLabelColor),
        unfocusedLabelColor = color(unfocusedLabelColor, disabledLabelColor),
        errorLabelColor = color(errorLabelColor, disabledLabelColor),
        disabledLabelColor = color(if (isError) errorLabelColor else if (focused) focusedLabelColor else unfocusedLabelColor, disabledLabelColor),
        focusedPlaceholderColor = color(focusedPlaceholderColor, disabledPlaceholderColor),
        unfocusedPlaceholderColor = color(unfocusedPlaceholderColor, disabledPlaceholderColor),
        errorPlaceholderColor = color(errorPlaceholderColor, disabledPlaceholderColor),
        disabledPlaceholderColor = color(if (isError) errorPlaceholderColor else if (focused) focusedPlaceholderColor else unfocusedPlaceholderColor, disabledPlaceholderColor),
        focusedSupportingTextColor = color(focusedSupportingTextColor, disabledSupportingTextColor),
        unfocusedSupportingTextColor = color(unfocusedSupportingTextColor, disabledSupportingTextColor),
        errorSupportingTextColor = color(errorSupportingTextColor, disabledSupportingTextColor),
        disabledSupportingTextColor = color(if (isError) errorSupportingTextColor else if (focused) focusedSupportingTextColor else unfocusedSupportingTextColor, disabledSupportingTextColor),
        focusedPrefixColor = color(focusedPrefixColor, disabledPrefixColor),
        unfocusedPrefixColor = color(unfocusedPrefixColor, disabledPrefixColor),
        errorPrefixColor = color(errorPrefixColor, disabledPrefixColor),
        disabledPrefixColor = color(if (isError) errorPrefixColor else if (focused) focusedPrefixColor else unfocusedPrefixColor, disabledPrefixColor),
        focusedSuffixColor = color(focusedSuffixColor, disabledSuffixColor),
        unfocusedSuffixColor = color(unfocusedSuffixColor, disabledSuffixColor),
        errorSuffixColor = color(errorSuffixColor, disabledSuffixColor),
        disabledSuffixColor = color(if (isError) errorSuffixColor else if (focused) focusedSuffixColor else unfocusedSuffixColor, disabledSuffixColor),
    )
}

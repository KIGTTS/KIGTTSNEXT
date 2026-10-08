package com.kigtts.next.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kigtts.next.theme.LocalCompactControlPalette
import com.kigtts.next.theme.LocalUseCompactControlPalette

@Composable
fun compactTonalIconColors(): IconButtonColors {
    val palette = if (LocalUseCompactControlPalette.current) LocalCompactControlPalette.current else null
    return if (palette == null) IconButtonDefaults.filledTonalIconButtonColors()
    else IconButtonDefaults.filledTonalIconButtonColors(containerColor = palette.container, contentColor = palette.content)
}

@Composable
fun compactTonalButtonColors(): ButtonColors {
    val palette = if (LocalUseCompactControlPalette.current) LocalCompactControlPalette.current else null
    return if (palette == null) ButtonDefaults.filledTonalButtonColors()
    else ButtonDefaults.filledTonalButtonColors(containerColor = palette.container, contentColor = palette.content)
}

@Composable
fun compactPlainIconColors(): IconButtonColors {
    val palette = if (LocalUseCompactControlPalette.current) LocalCompactControlPalette.current else null
    return if (palette == null) IconButtonDefaults.iconButtonColors()
    else IconButtonDefaults.iconButtonColors(contentColor = palette.plainContent)
}

@Composable
fun compactPlainButtonColors(): ButtonColors {
    val palette = if (LocalUseCompactControlPalette.current) LocalCompactControlPalette.current else null
    return if (palette == null) ButtonDefaults.textButtonColors()
    else ButtonDefaults.textButtonColors(contentColor = palette.plainContent)
}

@Composable
fun compactTabColors(dragging: Boolean = false): SelectableChipColors {
    val palette = if (LocalUseCompactControlPalette.current) LocalCompactControlPalette.current else null
    val container = palette?.container ?: MaterialTheme.colorScheme.secondaryContainer
    val content = palette?.content ?: MaterialTheme.colorScheme.onSecondaryContainer
    return if (palette == null && !dragging) FilterChipDefaults.filterChipColors()
    else FilterChipDefaults.filterChipColors(containerColor = if (dragging) container else Color.Unspecified,
        labelColor = if (dragging) content else palette?.plainContent ?: Color.Unspecified,
        iconColor = if (dragging) content else palette?.plainContent ?: Color.Unspecified,
        selectedContainerColor = container, selectedLabelColor = content,
        selectedLeadingIconColor = content, selectedTrailingIconColor = content)
}

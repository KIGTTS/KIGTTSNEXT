package com.kigtts.next.ui.navigation

import androidx.compose.runtime.compositionLocalOf

/** Retained background pages must not intercept the current page's return. */
val LocalPageActive = compositionLocalOf { true }

/** Heavy page content starts only after its entrance/interactive pull reaches rest. */
val LocalPageSettled = compositionLocalOf { true }

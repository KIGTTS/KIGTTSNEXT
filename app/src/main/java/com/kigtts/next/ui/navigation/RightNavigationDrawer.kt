package com.kigtts.next.ui.navigation

import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Only the drawer's placement is RTL. Page content keeps the system's text direction. */
@Composable
fun RightNavigationDrawer(navigation: DrawerNavigation,
    drawerContent: @Composable (LayoutDirection) -> Unit, content: @Composable () -> Unit) {
    val contentDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(drawerState = navigation.state, gesturesEnabled = navigation.active,
            drawerContent = { drawerContent(contentDirection) }) {
            CompositionLocalProvider(LocalLayoutDirection provides contentDirection, content = content)
        }
    }
}

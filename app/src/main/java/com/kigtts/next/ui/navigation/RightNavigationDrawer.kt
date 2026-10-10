package com.kigtts.next.ui.navigation

import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import android.content.res.Configuration

/** Portrait opens from the right, landscape from the left; text follows the system direction. */
@Composable
fun RightNavigationDrawer(navigation: DrawerNavigation,
    drawerContent: @Composable (LayoutDirection) -> Unit, content: @Composable () -> Unit) {
    val contentDirection = LocalLayoutDirection.current
    val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
    CompositionLocalProvider(LocalLayoutDirection provides if(landscape)LayoutDirection.Ltr else LayoutDirection.Rtl) {
        ModalNavigationDrawer(drawerState = navigation.state, gesturesEnabled = navigation.active,
            drawerContent = { drawerContent(contentDirection) }) {
            CompositionLocalProvider(LocalLayoutDirection provides contentDirection, content = content)
        }
    }
}

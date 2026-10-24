package com.kigtts.next.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.components.PluginIconBadge

@Composable internal fun OverlayShortcutIcon(entry: OverlayShortcut, showBadge: Boolean = true) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(OverlayAppCatalog.cachedIcon(entry), entry.iconPath, entry.component) {
        value = OverlayAppCatalog.icon(context, entry)
    }
    PluginIconBadge(if (showBadge) entry.badge?.toPluginBadge() else null) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.size(24.dp))
        else Icon(MaterialSymbols.vector(entry.symbol) ?: Icons.Default.Extension, null, Modifier.size(24.dp))
    }
}

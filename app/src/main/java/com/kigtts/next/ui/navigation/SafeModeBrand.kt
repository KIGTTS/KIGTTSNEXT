package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kigtts.next.R

@Composable internal fun SafeModeBrand(active: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS 横版 Logo",
            Modifier.width(if (active) 104.dp else 128.dp).height(if (active) 26.dp else 32.dp),
            tint = MaterialTheme.colorScheme.onSurface)
        if (active) Text("安全模式", Modifier.testTag("safe-mode-label"), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp))
    }
}

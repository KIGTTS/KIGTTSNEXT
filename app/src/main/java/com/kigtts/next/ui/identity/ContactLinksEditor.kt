package com.kigtts.next.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.*

@Composable internal fun ContactLinksEditor(links: List<ContactLink>, change: (List<ContactLink>) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableIntStateOf(-2) }
    var platform by remember { mutableStateOf(SocialPlatform.LINK) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("联系方式", style = MaterialTheme.typography.titleSmall)
        if (links.isNotEmpty()) ContactOrderList(links, change, { index ->
            platform = socialPlatform(links[index]); editing = index
        }, { index -> change(links.filterIndexed { i, _ -> i != index }) })
        if (links.size < 8) OutlinedButton(feedbackClick { choosing = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加联系方式")
        }
    }
    if (choosing) ContactPlatformPicker({ choosing = false }) { platform = it; choosing = false; editing = -1 }
    if (editing >= -1) ContactLinkEditor(links.getOrNull(editing), platform, { editing = -2 }) { updated ->
        change(if (editing < 0) links + updated else links.mapIndexed { i, item -> if (i == editing) updated else item })
        editing = -2
    }
}

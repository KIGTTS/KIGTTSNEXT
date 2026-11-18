@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

@Composable
internal fun StickerPickerSheet(libraries: List<StickerLibrary>, items: List<StickerItem>, imageFile: (String) -> File?,
    select: (StickerItem) -> Unit, import: () -> Unit, dismiss: () -> Unit, manage: () -> Unit) {
    var libraryId by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(uiText("photo.add_sticker"), style = MaterialTheme.typography.titleLarge)
                MotionTextButton(feedbackClick(import)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(uiText("common.pick_image")) }
            }
            StickerLibraryPanel(libraries, items, libraryId, { libraryId = it }, imageFile, true,
                addLibrary = dismiss, editLibrary = { }, selectItem = select, itemMenu = select,
                modifier = Modifier.height(430.dp), showLibraryActions = false)
            MotionTextButton(feedbackClick(manage), Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(uiText("photo.sticker_manage"))
            }
        }
    }
}

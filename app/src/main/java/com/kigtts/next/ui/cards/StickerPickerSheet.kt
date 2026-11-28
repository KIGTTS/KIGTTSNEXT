@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.cards.StickerRepository
import com.kigtts.plugin.api.StickerItem

@Composable
internal fun StickerPickerSheet(repository: StickerRepository, dismiss: () -> Unit, select: (StickerItem) -> Unit) {
    val catalog by repository.snapshot.collectAsStateWithLifecycle()
    var libraryId by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
            Text(uiText("photo.choose_sticker"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge)
            StickerLibraryPanel(catalog.libraries, catalog.items, libraryId, { libraryId = it }, repository::imageFile, true,
                addLibrary = dismiss, editLibrary = { }, selectItem = select, itemMenu = select,
                modifier = Modifier.height(430.dp), showLibraryActions = false)
        }
    }
}

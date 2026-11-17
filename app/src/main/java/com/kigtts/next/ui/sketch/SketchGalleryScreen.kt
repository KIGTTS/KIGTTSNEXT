package com.kigtts.next.ui.sketch

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.PageScaffold
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable internal fun SketchGalleryScreen(repository: SketchAlbumRepository, open: (String) -> Unit,
    close: () -> Unit, message: (String) -> Unit) {
    val copy = rememberUiCopy()
    var works by remember { mutableStateOf<List<SketchArtwork>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    LaunchedEffect(repository) { works = withContext(Dispatchers.IO) { repository.list() }; loaded = true }
    fun remove(id: String) {
        if (busy) return
        busy = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                works = withContext(Dispatchers.IO) { repository.remove(id); repository.list() }
                deleting = null; selectedId = null
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; message(failure.message ?: copy("sketch.remove_failed")) }
            finally { busy = false }
        }
    }
    PageScaffold(copy("sketch.artwork"), close, backLabel = copy("sketch.back")) {
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().testTag("sketch-album"),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(works, key = { it.id }) { artwork ->
                Card(onClick = feedbackClick { selectedId = artwork.id }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("sketch-artwork/" + artwork.id), shape = homeCardShape()) {
                    Box(Modifier.fillMaxWidth().height(180.dp).padding(8.dp)) { SketchArtworkImage(repository, artwork.id) }
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(artwork.createdAt)),
                        Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        EmptyContent(loaded && works.isEmpty(), Icons.Default.Draw, copy("sketch.no_works"))
        }
    }
    val selected = works.firstOrNull { it.id == selectedId }
    if (selected != null) MotionAlertDialog(onDismissRequest = { if (!busy) selectedId = null }, title = { Text(copy("sketch.artwork")) }, text = {
        Column {
            Box(Modifier.fillMaxWidth().height(280.dp)) { SketchArtworkImage(repository, selected.id) }
            Row {
                MotionTextButton(feedbackClick { runCatching { shareSketchArtwork(context, selected) }.onFailure { message(copy("sketch.share_failed")) } }, enabled = !busy) {
                    Icon(Icons.Default.Share, null); Text(copy("action.share"))
                }
                MotionTextButton(feedbackClick { deleting = selected.id }, enabled = !busy) { Text(copy("action.delete")) }
            }
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick { selectedId = null; open(selected.id) }, enabled = !busy) { Text(copy("sketch.open_in_board")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { selectedId = null }, enabled = !busy) { Text(copy("action.close")) } })
    if (deleting != null) MotionAlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text(copy("sketch.remove_work")) },
        text = { Text(copy("sketch.remove_work_body")) },
        confirmButton = { MotionTextButton(dialogFeedbackClick { deleting?.let(::remove) }, enabled = !busy) { Text(copy("action.delete")) } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { deleting = null }, enabled = !busy) { Text(copy("action.cancel")) } })
}

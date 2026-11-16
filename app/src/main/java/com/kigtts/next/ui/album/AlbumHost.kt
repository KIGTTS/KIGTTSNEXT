package com.kigtts.next.ui.album

import com.kigtts.next.i18n.uiText
import androidx.compose.runtime.*
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.components.MotionAlertDialog
import androidx.compose.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun AlbumHost(model: AppViewModel, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    val request by model.cardUi.previewRequest.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    val openError = uiText("album.open_image_failed")
    var requestedUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(active, request) {
        val id = request.cardId ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        if (!request.openPreview) { model.cardUi.previewHandled(request.version); return@LaunchedEffect }
        try { requestedUri = model.cards.systemAlbum.save(id) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { model.navigate(Destination.CardPreview(id)) }
        model.cardUi.previewHandled(request.version)
    }
    DeviceAlbumScreen(back, onEdit = { uri ->
        if (!opening) scope.launch {
            opening = true
            try { model.navigate(Destination.CardEditor(model.cards.documents.open(uri))) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: openError) }
            finally { opening = false }
        }
    }, onCamera = { model.cardCapture.open(systemCamera = false) }, message = model::message, requestedUri = requestedUri,
        requestConsumed = { requestedUri = null })
    if (opening) MotionAlertDialog(onDismissRequest = {}, title = { Text(uiText("album.opening_image")) },
        text = { CircularProgressIndicator() }, confirmButton = {})
}

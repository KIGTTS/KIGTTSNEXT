package com.kigtts.next.ui.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.cards.*
import com.kigtts.next.ui.cards.camera.*
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidPhotoStyle
import kotlinx.coroutines.*

@Composable internal fun CardPhotoFilterTools(card: PolaroidCard, stickers: StickerRepository, enabled: Boolean,
    change: (PolaroidCard) -> Unit, message: (String) -> Unit) {
    val context = LocalContext.current
    val library = remember(context) { CameraLutLibrary(context) }
    var choices by remember { mutableStateOf(emptyList<CameraLutChoice>()) }
    val scope = rememberCoroutineScope()
    val current = rememberUpdatedState(card)
    var busy by remember { mutableStateOf(false) }
    val style = card.photoStyle ?: PolaroidPhotoStyle()
    var savedChoice by remember(style.lutImageId) { mutableStateOf<CameraLutChoice?>(null) }
    LaunchedEffect(style.lutImageId) {
        savedChoice = if (style.lutImageId == null) null else withContext(Dispatchers.IO) {
            style.customLut(stickers::imageFile)?.let { CameraLutChoice(requireNotNull(style.lutImageId), it.title, it) }
        }
    }
    LaunchedEffect(library) { choices = withContext(Dispatchers.IO) { library.choices() } }
    fun choose(custom: CameraLutChoice) {
        if (busy || !enabled) return
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { CapturedPhotoStyle(PhotoFilter.CUSTOM, style.strength, custom.lut, style.photoEffects()).persist(stickers) }
                change(current.value.withPhotoStyle(saved))
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { message(failure.message ?: "滤镜未能添加") }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { val added = withContext(Dispatchers.IO) { library.import(uri) }; choices = withContext(Dispatchers.IO) { library.choices() }; choose(added) }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { message(failure.message ?: "LUT 未能导入") }
        }
    }
    if (card.imageId == null) { Text("先添加一张照片"); return }
    CameraFilters(style.photoFilter(), enabled, { selected ->
        change(card.withPhotoStyle(style.copy(filter = selected.name, lutImageId = null, lutSize = 0)))
    }, strength = style.strength, changeStrength = { change(card.withPhotoStyle(style.copy(strength = it))) },
        custom = listOfNotNull(savedChoice) + choices, selectedLut = style.lutImageId,
        selectLut = { id -> (listOfNotNull(savedChoice) + choices).firstOrNull { it.id == id }?.let(::choose) },
        importLut = { picker.launch(arrayOf("*/*")) }, effects = style.photoEffects(), changeEffects = {
            change(card.withPhotoStyle(style.copy(vignette = it.vignette, grain = it.grain, aberration = it.aberration, bloom = it.bloom)))
        })
}

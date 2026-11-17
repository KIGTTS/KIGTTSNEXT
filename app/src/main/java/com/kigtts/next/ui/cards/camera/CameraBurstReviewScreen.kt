package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.CardBurstReview
import com.kigtts.next.ui.cards.rememberCardImage
import com.kigtts.next.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CameraBurstReviewScreen(review: CardBurstReview, busy: Boolean, saved: Int,
    cancel: () -> Unit, save: (Set<String>) -> Unit) {
    val copy = rememberCameraCopy()
    var selected by rememberSaveable(review.requestId) { mutableStateOf(review.photos.indices.toList()) }
    var preview by rememberSaveable(review.requestId) { mutableStateOf<Int?>(null) }
    val grid = rememberLazyGridState()
    fun toggle(index: Int) { selected = if (index in selected) selected - index else selected + index }
    fun saveIndices(indices: List<Int>) {
        selected = indices
        save(indices.map { review.photos[it].original.path }.toSet())
    }
    Scaffold(Modifier.fillMaxSize().testTag("burst-review"), topBar = {
        TopAppBar(title = { Column {
            Text(copy.text("camera.choose_photos"))
            Text(if (busy) copy.text("camera.burst_saving", "done" to saved.toString(), "total" to selected.size.toString()) else copy.text("camera.burst_selected", "count" to selected.size.toString()),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, navigationIcon = { ToolButton(copy.text("camera.back_to_camera"), Icons.AutoMirrored.Filled.ArrowBack, cancel,
            size = 48.dp, enabled = !busy, colors = compactPlainIconColors()) }, actions = {
            val all = selected.size == review.photos.size
            ToolButton(if (all) copy.text("camera.deselect_all") else copy.text("camera.select_all"), if (all) Icons.Default.Deselect else Icons.Default.SelectAll,
                { selected = if (all) emptyList() else review.photos.indices.toList() }, size = 48.dp,
                enabled = !busy, colors = compactPlainIconColors(), modifier = Modifier.testTag("burst-select-all"))
        })
    }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MotionTextButton(feedbackClick { saveIndices(review.photos.indices.toList()) }, enabled = !busy,
                    modifier = Modifier.testTag("burst-save-all")) { Text(copy.text("camera.save_all")) }
                Button(feedbackClick { saveIndices(selected) }, enabled = !busy && selected.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).testTag("burst-print-selected")) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.SaveAlt, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (busy) copy.text("camera.saving") else copy.text("camera.burst_save_selected", "count" to selected.size.toString()))
                }
            }
        }
    }) { insets ->
        LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(insets)
            .burstRangeSelection(grid, !busy && preview == null, selected) { selected = it }.testTag("burst-grid"), state = grid,
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(review.photos, key = { _, shot -> shot.original.path }) { index, shot ->
                Surface(onClick = feedbackClick { preview = index }, enabled = !busy,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.aspectRatio(1f).testTag("burst-photo/$index")) {
                    Box {
                        BurstPhotoImage(shot, Modifier.fillMaxSize(), crop = true, maximum = 384)
                        BurstPhotoCheck(index in selected, !busy, { toggle(index) },
                            Modifier.align(Alignment.TopEnd).testTag("burst-check/$index"))
                    }
                }
            }
        }
    }
    preview?.let { initial -> CameraBurstPreview(review.photos, initial, selected.toSet(), busy,
        { preview = null }, ::toggle) }
}

@Composable
internal fun BurstPhotoImage(shot: PolaroidCameraCapture, modifier: Modifier, crop: Boolean, maximum: Int) {
    val copy = rememberCameraCopy()
    val source = rememberCardImage(shot.displayFile, maximum)
    val image = com.kigtts.next.ui.cards.rememberCardPhotoStyle(source, shot.original.path, null, { null }, shot.photoStyle)
    Box(modifier.background(if (crop) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
        else Icon(Icons.Default.Photo, copy.text("camera.photo_loading"), Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BurstPhotoCheck(checked: Boolean, enabled: Boolean, change: () -> Unit, modifier: Modifier = Modifier) {
    val copy = rememberCameraCopy()
    IconToggleButton(checked, feedbackChange { change() }, modifier.size(48.dp), enabled = enabled) {
        Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center) {
            Icon(if (checked) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                if (checked) copy.text("camera.deselect") else copy.text("camera.choose_photos"), Modifier.size(if (checked) 18.dp else 26.dp),
                tint = if (checked) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White)
        }
    }
}

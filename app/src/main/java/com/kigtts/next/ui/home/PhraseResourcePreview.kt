package com.kigtts.next.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.audio.AudioCardSession
import com.kigtts.next.media.*
import com.kigtts.next.ui.media.*
import com.kigtts.next.ui.navigation.LocalPageActive

/** Draft previews own their decoder and never send content to the main subtitle card. */
@Composable
internal fun PhraseResourcePreview(entry: MediaEntry, repository: MediaRepository, active: Boolean, appleLive: Boolean) {
    key(entry.id) {
        val file = remember(entry.id) { repository.file(entry) }
        CompositionLocalProvider(LocalPageActive provides (active && LocalPageActive.current)) {
            when(entry.kind) {
                MediaKind.AUDIO -> {
                    val context=LocalContext.current
                    val session=remember(entry.id) { AudioCardSession(context) }
                    DisposableEffect(session,file) {
                        file?.let { session.start(entry.id,Uri.fromFile(it),autoPlay=false) }
                        onDispose { session.release() }
                    }
                    AudioCardControls(session,Modifier.fillMaxWidth().testTag("phrase-audio-preview"),label="预览音频",playTag="preview-audio")
                }
                MediaKind.IMAGE,MediaKind.MOTION_PHOTO,MediaKind.VIDEO -> {
                    Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large).testTag("phrase-media-preview")) {
                        if(entry.kind==MediaKind.VIDEO)MediaVideo(file,entry.name,false,{},Modifier.fillMaxSize(),entry.scale,autoPlay=false)
                        else {
                            MediaImage(file,entry.name,Modifier.fillMaxSize(),scale=entry.scale,backdrop=true)
                            if(entry.video!=null&&(!entry.appleLive||appleLive))LivePhotoPlayback(repository.file(entry,true),entry.name,entry.scale)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

package com.kigtts.next.ui.sketch

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Finishing a drawing is not tied to a disposable Compose coroutine or an Activity rotation. */
internal object SketchDraftWriter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong()
    private val gate = Mutex()
    fun submit(repository: SketchAlbumRepository, document: SketchDocument) {
        val request = generation.incrementAndGet()
        scope.launch {
            delay(160)
            gate.withLock { if (request == generation.get()) runCatching { repository.saveDraft(document) } }
        }
    }
}

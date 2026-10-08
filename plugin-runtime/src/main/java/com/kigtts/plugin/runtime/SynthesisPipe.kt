package com.kigtts.plugin.runtime

import android.os.ParcelFileDescriptor
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.DataInputStream
import java.util.UUID

internal fun synthesisPipe(open: (String) -> ParcelFileDescriptor, cancel: (String) -> Unit) = channelFlow {
    val token = UUID.randomUUID().toString()
    val pipe = open(token)
    val unblock = launch(start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { pipe.close(); runCatching { cancel(token) } }
    }
    var finished = false
    try {
        DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(pipe)).use { input ->
            while (true) when (AudioWire.tag(input)) {
                null -> break
                AudioWire.START -> send(SynthesisEvent.Started)
                AudioWire.AUDIO -> send(SynthesisEvent.Audio(AudioWire.readPcm(input)))
                AudioWire.FINISH -> { finished = true; send(SynthesisEvent.Finished(AudioWire.readText(input).ifEmpty { null })) }
                AudioWire.ERROR -> AudioWire.failure(input)
                else -> error("非法合成响应")
            }
        }
        check(finished) { "合成进程中断" }
    } finally { unblock.cancelAndJoin() }
}.flowOn(Dispatchers.IO)

package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class QuickTextWireTest {
    @Test fun wireRoundTripNotificationsCancellationAndRevocation()=runBlocking {
        var calls=0;val changes=MutableStateFlow(QuickTextChange(1))
        val backend=object:QuickTextBroker {
            override suspend fun request(command:QuickTextCommand):QuickTextReply {calls++;return QuickTextReply.Features(QuickTextFeatures(operations=listOf("query"),writable=false))}
            override fun changes(afterRevision:Long)=changes
        }
        val host=HostQuickTextBroker(backend);val worker=WorkerQuickTextBroker(host)
        try {
            assertTrue(worker.request(QuickTextCommand.Describe) is QuickTextReply.Features)
            assertEquals(1,withTimeout(5000) {worker.changes().first().revision})
            val next=async {withTimeout(5000) {worker.changes().first {it.revision==2L}}}
            changes.value=QuickTextChange(2);assertEquals(2,next.await().revision)
            val huge=QuickTextCommand.Prepare("x",1,listOf(QuickTextEdit.PutEntry(QuickEntry(groupId="g",text="a".repeat(300*1024)))))
            assertEquals(QuickTextError.TOO_LARGE,(worker.request(huge) as QuickTextReply.Failure).code);assertEquals(1,calls)
            host.close();assertEquals(QuickTextError.PERMISSION_DENIED,(worker.request(QuickTextCommand.Describe) as QuickTextReply.Failure).code)
        } finally {host.close()}
    }
}

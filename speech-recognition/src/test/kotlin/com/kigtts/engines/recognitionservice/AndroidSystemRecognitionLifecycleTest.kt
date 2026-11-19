package com.kigtts.engines.recognitionservice

import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.speech.RecognitionService
import com.kigtts.plugin.api.RecognitionConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSystemRecognitionLifecycleTest {
    @Test fun cancellingLiveCaptionsDestroysThePendingSystemRecognizer() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE),
            ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = "test.recognizer"; name = "Recognizer" } })
        val engine = AndroidSystemRecognition(context) { "en" }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val listening = launch { engine.listen(CompletableDeferred(), RecognitionConfig(), continuousCaptions = true).collect {} }
            runCurrent()
            val recognizer = requireNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            assertFalse(shadowOf(recognizer).isDestroyed)

            listening.cancelAndJoin()
            assertTrue(shadowOf(recognizer).isDestroyed)
            advanceUntilIdle()
            assertSame(recognizer, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        } finally {
            Dispatchers.resetMain()
        }
    }
}

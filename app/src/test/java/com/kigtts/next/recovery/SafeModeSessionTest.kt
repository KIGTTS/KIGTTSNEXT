package com.kigtts.next.recovery

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class SafeModeSessionTest {
    private fun recovery() = CrashRecovery(ApplicationProvider.getApplicationContext<Application>()).apply {
        restore(); repeat(3) { record(IllegalStateException(), false) }
    }
    @Test fun enteringSoftwareKeepsProtectionAndDoesNotRepeatThePromptOnRefresh() {
        val recovery = recovery(); val session = SafeModeSession(recovery) { fail("Do not reload on acknowledge") }
        assertTrue(session.state.value.prompt)
        session.enterSoftware(); session.synchronize()
        assertTrue(recovery.safeMode()); assertTrue(session.state.value.active); assertFalse(session.state.value.prompt)
        assertTrue(SafeModeSession(recovery) {}.state.value.prompt)
    }
    @Test fun restoringOnlyReloadsOnceAndRemovesTheSafeModeBadge() = runBlocking {
        val recovery = recovery(); var reloads = 0
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val session = SafeModeSession(recovery) { reloads++; started.complete(Unit); finish.await() }
        val job = launch { session.restorePlugins() }; started.await()
        assertTrue(session.state.value.restoring)
        session.restorePlugins(); assertEquals(1, reloads)
        finish.complete(Unit); job.join()
        assertFalse(recovery.safeMode()); assertFalse(session.state.value.active)
        assertFalse(session.state.value.prompt); assertFalse(session.state.value.restoring)
    }
}

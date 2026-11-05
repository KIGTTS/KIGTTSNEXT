package com.kigtts.next

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.sharing.CloudSharingSetup
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class CloudSharingSetupTest {
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("sharing-test",0).apply { edit().clear().commit() }
    @Test fun noDownloadBeforeConsentAndSystemChoicePersists() = runBlocking {
        var installs=0
        val setup=CloudSharingSetup(preferences(),this,{false},{false},{installs++})
        val choice=async(start=CoroutineStart.UNDISPATCHED){setup.ensure()}
        assertNotNull(setup.state.value);assertEquals(0,installs)
        setup.useSystem();assertFalse(choice.await());assertFalse(setup.ensure());assertEquals(0,installs)
    }
    @Test fun consentInstallsOnceThenDisabledPluginFallsBackToSystem() = runBlocking {
        var installed=false;var ready=false;var installs=0
        val setup=CloudSharingSetup(preferences(),this,{ready},{installed},{installs++;installed=true;ready=true})
        val choice=async(start=CoroutineStart.UNDISPATCHED){setup.ensure()}
        setup.useCloud();assertTrue(choice.await());assertTrue(setup.enabled);assertTrue(setup.ensure());assertEquals(1,installs)
        ready=false;assertFalse(setup.ensure());assertEquals(1,installs)
    }
    @Test fun failedInstallOffersRetryOrSystemSharingWithoutClaimingEnabled() = runBlocking {
        val setup=CloudSharingSetup(preferences(),this,{false},{false},{error("网络暂不可用")})
        val choice=async(start=CoroutineStart.UNDISPATCHED){setup.ensure()}
        setup.useCloud();yield();assertEquals("网络暂不可用",setup.state.value?.error);assertFalse(setup.enabled)
        setup.useSystem();assertFalse(choice.await())
    }
    @Test fun canUseSystemSharingWhileInstallationIsWaiting() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val setup = CloudSharingSetup(preferences(), this, { false }, { false }, {
            try { started.complete(Unit); awaitCancellation() }
            finally { stopped.complete(Unit) }
        })
        val choice = async(start = CoroutineStart.UNDISPATCHED) { setup.ensure() }
        setup.useCloud(); started.await()
        assertTrue(setup.state.value?.busy == true)
        setup.useSystem()
        assertFalse(choice.await()); stopped.await()
        assertNull(setup.state.value); assertFalse(setup.enabled)
    }
    @Test fun timedOutInstallationReturnsToRetryState() = runBlocking {
        val setup = CloudSharingSetup(preferences(), this, { false }, { false }, { awaitCancellation() },
            installTimeoutMillis = 10)
        val choice = async(start = CoroutineStart.UNDISPATCHED) { setup.ensure() }
        setup.useCloud()
        withTimeout(2000) { while (setup.state.value?.error == null) delay(5) }
        assertFalse(setup.state.value?.busy == true); assertFalse(setup.enabled)
        setup.useSystem(); assertFalse(choice.await())
    }
    @Test fun dismissingInstallationCancelsChoiceAndAllowsFreshAttempt() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var ready = false
        var calls = 0
        val setup = CloudSharingSetup(preferences(), this, { ready }, { false }, {
            if (++calls == 1) { started.complete(Unit); awaitCancellation() } else ready = true
        })
        val first = async(start = CoroutineStart.UNDISPATCHED) { setup.ensure() }
        setup.useCloud(); started.await(); setup.dismiss()
        first.join(); assertTrue(first.isCancelled); assertNull(setup.state.value)
        val second = async(start = CoroutineStart.UNDISPATCHED) { setup.ensure() }
        setup.useCloud(); assertTrue(second.await()); assertEquals(2, calls)
    }
    @Test fun downloadProgressAndInstallationStageAreReported() = runBlocking {
        val downloaded = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var ready = false
        val setup = CloudSharingSetup(preferences(), this, { ready }, { false }, { progress ->
            progress(0.45f); downloaded.complete(Unit); finish.await()
            progress(null); ready = true
        })
        val choice = async(start = CoroutineStart.UNDISPATCHED) { setup.ensure() }
        setup.useCloud(); downloaded.await()
        assertEquals(0.45f, setup.state.value!!.progress!!, 0.001f)
        finish.complete(Unit); assertTrue(choice.await())
    }
}

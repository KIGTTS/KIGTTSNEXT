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
}

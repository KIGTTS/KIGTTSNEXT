package com.kigtts.next

import com.kigtts.next.plugins.control.*
import com.kigtts.plugin.runtime.PackageManifest
import com.kigtts.plugin.runtime.PackagePreview
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class StoreBootstrapTest {
    private fun candidate() = StoreDownload(Files.createTempFile("store-bootstrap-", ".kigplugin").toFile(), PackagePreview(
        PackageManifest(STORE_ID, "插件商店", "0.10.1", 101, "com.example.Store", files = emptyMap()), STORE_PUBLISHER))

    @Test fun offerDoesNotDownloadOrTrustAndInstallNeedsSecondConfirmation() = runTest {
        val file = candidate(); var fetched = 0; var installed = false; var completed = 0
        val controller = StoreBootstrapCoordinator(this, { installed }, { installed },
            { fetched++; file }, { installed = true }, { error("not installed yet") }, { completed++ }, StandardTestDispatcher(testScheduler))
        controller.offer(); controller.offer(); runCurrent()
        assertEquals(StoreBootstrapPhase.OFFER, controller.state.value!!.phase); assertEquals(0, fetched)
        controller.confirm(); controller.confirm(); advanceUntilIdle()
        assertEquals(1, fetched); assertFalse(installed); assertEquals(StoreBootstrapPhase.REVIEW, controller.state.value!!.phase)
        controller.confirm(); advanceUntilIdle()
        assertTrue(installed); assertEquals(1, completed); assertNull(controller.state.value); assertFalse(file.file.exists())
    }
    @Test fun disabledStoreIsEnabledWithoutDownloadingAndFailedRestoreCanRetry() = runTest {
        var ready = false; var attempts = 0; var completed = false
        val controller = StoreBootstrapCoordinator(this, { true }, { ready }, { error("must not download") },
            { error("must not install") }, { attempts++; ready = attempts == 2 }, { completed = true }, StandardTestDispatcher(testScheduler))
        controller.offer(); assertEquals(StoreBootstrapPhase.RESTORE, controller.state.value!!.phase)
        controller.confirm(); advanceUntilIdle(); assertEquals(StoreBootstrapPhase.ERROR, controller.state.value!!.phase)
        assertFalse(completed); controller.confirm(); advanceUntilIdle(); assertTrue(completed); assertEquals(2, attempts)
    }
    @Test fun cancellingReviewDeletesTemporaryPackageAndNeverInstalls() = runTest {
        val file = candidate()
        val controller = StoreBootstrapCoordinator(this, { false }, { false }, { file }, { error("must not install") }, {}, {}, StandardTestDispatcher(testScheduler))
        controller.offer(); controller.confirm(); advanceUntilIdle(); assertTrue(file.file.exists())
        controller.dismiss(); assertNull(controller.state.value); assertFalse(file.file.exists())
    }
    @Test fun cancellationAndNetworkFailureRemainRetryableWithoutAutomaticDownloads() = runTest {
        var fetches = 0
        val controller = StoreBootstrapCoordinator(this, { false }, { false }, {
            fetches++; if (fetches == 1) awaitCancellation() else error("offline")
        }, {}, {}, {}, StandardTestDispatcher(testScheduler))
        controller.offer(); controller.confirm(); runCurrent(); controller.dismiss(); advanceUntilIdle()
        assertNull(controller.state.value); assertEquals(1, fetches)
        controller.offer(); controller.confirm(); advanceUntilIdle()
        assertEquals(StoreBootstrapPhase.ERROR, controller.state.value!!.phase); assertEquals(2, fetches)
        advanceUntilIdle(); assertEquals(2, fetches); controller.dismiss()
    }
}

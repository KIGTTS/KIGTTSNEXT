package com.kigtts.next

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.updates.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdatesTest {
    private fun mirror(version: String) = UpdateResponse(200,
        """{"schema":1,"release":{"version":"$version","notes":"魔搭更新内容"}}""")
    private val github = UpdateResponse(200, """{"tag_name":"v1.2.10","body":"GitHub release notes"}""")

    @Test fun mirrorIsFirstIncludingWhenItAdvertisesTheInstalledBuild() {
        val asked = mutableListOf<String>()
        val source = AppUpdateSource { url -> asked += url; mirror("1.0.0") }
        assertEquals(AppRelease(ReleaseVersion(1, 0, 0), "魔搭更新内容"), source.latest().getOrThrow())
        assertEquals(listOf(MODELSCOPE_UPDATES), asked)
    }

    @Test fun brokenMissingOrUnpublishedMirrorUsesGithubNotes() {
        for (response in listOf(UpdateResponse(503, ""), UpdateResponse(200, "<html>"),
            UpdateResponse(200, """{"schema":1,"release":null}"""), UpdateResponse(404, ""))) {
            val source = AppUpdateSource { if (it == MODELSCOPE_UPDATES) response else github }
            assertEquals(AppRelease(ReleaseVersion(1, 2, 10), "GitHub release notes"), source.latest().getOrThrow())
        }
        assertTrue(ReleaseVersion.parse("v1.2.10")!! > ReleaseVersion.parse("1.2.9")!!)
        assertNull(ReleaseVersion.parse("1.2.10-beta"))
    }

    @Test fun laterKeepsBadgeAndIgnoreSurvivesRestartOnlyForTheChosenVersion() = runBlocking {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("update-test", 0)
        prefs.edit().clear().commit()
        var version = "1.1.0"
        var clock = 50_000_000L
        val source = AppUpdateSource { mirror(version) }
        fun controller() = AppUpdates(prefs, this, ReleaseVersion(1, 0, 0), source) { clock }
        val updates = controller()
        assertTrue(updates.state.value.automatic)
        updates.check()
        withTimeout(5_000) { updates.state.first { it.pending } }
        updates.later()
        assertNotNull(updates.state.value.release)
        updates.check()
        assertFalse(updates.state.value.pending)
        val restarted = controller()
        restarted.check()
        assertTrue(restarted.state.value.pending)
        restarted.ignore()
        assertNull(controller().state.value.release)
        restarted.check(manual = true)
        withTimeout(5_000) { restarted.state.first { it.result == UpdateResult.IGNORED } }
        version = "1.1.1"
        clock += 24 * 60 * 60 * 1000L
        restarted.check()
        assertEquals(ReleaseVersion(1, 1, 1), withTimeout(5_000) { restarted.state.first { it.pending } }.release?.version)
        restarted.automatic(false)
        assertFalse(restarted.state.value.pending)
        assertFalse(controller().state.value.automatic)
        assertNotNull(controller().state.value.release)
    }
}

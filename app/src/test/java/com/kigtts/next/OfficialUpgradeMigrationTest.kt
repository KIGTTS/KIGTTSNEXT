package com.kigtts.next

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.legacy.LegacyContent
import com.kigtts.next.legacy.legacyOverlayShortcuts
import com.kigtts.next.legacy.migrateLegacyPiperPacks
import com.kigtts.next.ui.onboarding.FirstRunStep
import com.kigtts.next.ui.onboarding.FirstRunStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficialUpgradeMigrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun oldReleaseGuideRemembersItsStageAndSkipsPermissionAndPresetPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guide = FirstRunStore(context, upgradingOriginal = true)
        assertTrue(guide.upgrade)
        assertTrue(com.kigtts.next.state.QuickPhraseStore(context).load().isEmpty())
        assertEquals(listOf(FirstRunStep.WELCOME, FirstRunStep.APPEARANCE, FirstRunStep.FEATURES), guide.pages)
        guide.visit(FirstRunStep.APPEARANCE)
        guide.visit(FirstRunStep.PRESETS)
        val resumed = FirstRunStore(context)
        assertTrue(resumed.upgrade)
        assertEquals(FirstRunStep.APPEARANCE, resumed.step())
        resumed.visit(FirstRunStep.FEATURES)
        assertTrue(resumed.finish(agreementConfirmed = false))
        assertEquals(FirstRunStep.COMPLETE, FirstRunStore(context).step())
    }

    @Test fun theOldOverlayKeepsExplicitLauncherComponentsAndSkipsInvalidEntries() {
        val source = mapOf("floating_overlay_shortcuts" to """[
          {"packageName":"sample.player","className":".Launch","label":"播放器"},
          {"packageName":"sample.player","className":"sample.player.Launch","label":"duplicate"},
          {"packageName":"other.player","className":"content://not-a-class","label":"invalid"},
          {"packageName":"other.player","className":"Open"}
        ]""")
        assertEquals(listOf("sample.player/sample.player.Launch", "other.player/other.player.Open"),
            legacyOverlayShortcuts(source).map { it.component })
        assertEquals("播放器", legacyOverlayShortcuts(source).first().title)
        assertTrue(legacyOverlayShortcuts(mapOf("floating_overlay_shortcuts" to "[")).isEmpty())
    }

    @Test fun piperPacksPersistBeforeEngineInstallationWithoutCopyingKokoro() {
        val source = temporary.newFolder("old")
        val piper = File(source, "voice/old-piper").apply { mkdirs() }
        val kokoro = File(source, "voice/old-kokoro").apply { mkdirs() }
        for ((name, pack) in listOf("piper" to piper, "kokoro" to kokoro)) {
            File(pack, "manifest.json").writeText("""{"engine":"$name","files":{"model":"model.onnx"}}""")
            File(pack, "model.onnx").writeBytes(byteArrayOf(1, 2))
            File(pack, "model.onnx.json").writeText("{}")
        }
        val pluginData = temporary.newFolder("private")
        val content = LegacyContent("old", resourceRoot = source)
        assertTrue(migrateLegacyPiperPacks(content, pluginData))
        assertTrue(migrateLegacyPiperPacks(content, pluginData))
        val packs = File(pluginData, "engine.piper/models/voices").listFiles().orEmpty()
        assertEquals(1, packs.size)
        assertTrue(File(packs.single(), "model.onnx.json").isFile)
        assertTrue(File(piper, "manifest.json").isFile)
        assertTrue(File(kokoro, "manifest.json").isFile)
    }
}

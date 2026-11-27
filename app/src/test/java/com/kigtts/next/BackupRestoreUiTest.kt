package com.kigtts.next

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.CategoryIcon
import com.kigtts.next.state.QuickArchiveSelection
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.settings.BackupRestoreScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowActivity
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class BackupRestoreUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun groupExportOpensTheSystemPickerAndWritesAReadableFile() {
        val model = openBackupScreen()
        compose.onNodeWithText("导入旧版备份").assertDoesNotExist()
        compose.onNodeWithTag("backup-export").performScrollTo().performClick()
        compose.onNodeWithText("选择要导出的分组").assertIsDisplayed()
        compose.onNodeWithText("导出").performClick()
        val picker = awaitPicker(Intent.ACTION_CREATE_DOCUMENT)
        val output = File(compose.activity.cacheDir, "ui-quick-backup.kigcard")
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(output)))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("backup-export") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(model.phrases.value.size, QuickArchiveSelection.read(output).entries.size)
    }

    @Test fun theSamePickerShowsAnOriginalBackupPreview() {
        val model = openBackupScreen()
        val source = File(compose.activity.cacheDir, "legacy-quick-text.json").apply {
            writeText("""{"type":"quick_subtitle","version":1,"groups":[{"title":"原版分组","items":["从备份恢复"]}]}""")
        }
        compose.onNodeWithTag("backup-import").performClick()
        val picker = awaitPicker(Intent.ACTION_OPEN_DOCUMENT)
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(source)))
        }
        compose.waitUntil(10_000) { model.legacyImports.preview.value != null }
        compose.onNodeWithText("原版分组 · 1 条").assertIsDisplayed()
        assertTrue(model.phrases.value.none { it.text == "从备份恢复" })
    }

    @Test fun restoresOnlyCheckedPresetGroupsAndKeepsEveryExistingCopy() {
        val model = openBackupScreen()
        compose.runOnIdle {
            val library = model.quickTexts
            library.commit(listOf(QuickPhrase("保留我的内容", "常用")), listOf("常用"),
                mapOf("常用" to CategoryIcon.INFO), mapOf("常用" to java.util.UUID.randomUUID().toString()), library.revision.value)
        }
        val original = model.phrases.value
        for (suffix in 1..2) {
            compose.onNodeWithTag("backup-restore-presets").performScrollTo().performClick()
            compose.onNodeWithTag("backup-group-confirm").assertIsNotEnabled()
            compose.onNodeWithTag("backup-group-icon/general", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("backup-group/general").assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("backup-group/photo").assertIsOff()
            compose.onNodeWithTag("backup-group-confirm").performClick()
            compose.waitUntil(5_000) { "常用($suffix)" in model.categories.value }
            val restored = model.phrases.value.filter { it.category == "常用($suffix)" }
            assertEquals(8, restored.size)
            assertEquals("谢谢", restored.first().title)
            assertEquals("谢谢你", restored.first().text)
            assertEquals("下次再见~", restored.last().text)
            assertEquals(CategoryIcon.valueOf("symbol:chat"), model.quickTexts.categoryIcons.value["常用($suffix)"])
        }
        assertEquals(original, model.phrases.value.take(original.size))
        assertFalse("拍照" in model.categories.value)
        assertEquals(model.phrases.value.size, model.phrases.value.map { it.id }.toSet().size)
    }

    private fun openBackupScreen(): AppViewModel {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.setContent {
            CompositionLocalProvider(LocalUiLocale provides "zh-CN") {
                KigttsTheme(ThemeSettings()) { BackupRestoreScreen(model) }
            }
        }
        return model
    }

    private fun awaitPicker(action: String): ShadowActivity.IntentForResult {
        var request: ShadowActivity.IntentForResult? = null
        compose.waitUntil(10_000) {
            request = shadowOf(compose.activity).nextStartedActivityForResult
            request != null
        }
        return requireNotNull(request).also { assertEquals(action, it.intent.action) }
    }
}

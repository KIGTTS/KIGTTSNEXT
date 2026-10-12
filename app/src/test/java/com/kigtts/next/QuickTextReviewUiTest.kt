package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class QuickTextReviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeReviewRequiresAnExplicitClickAndReturnsThroughNormalNavigation() {
        val app=ApplicationProvider.getApplicationContext<Application>();app.getSharedPreferences("quick-phrases",0).edit().clear().commit()
        val model=testAppModel(app);val first=model.phrases.value.first();val broker=model.quickTextPlugins.forPlugin("test.review",setOf("quick-text-write"))
        compose.setContent {KigttsApp(model)}
        compose.runOnIdle {compose.activity.lifecycleScope.launch {
            val prepared=broker.request(QuickTextCommand.Prepare("delete",model.quickTexts.revision.value,listOf(QuickTextEdit.RemoveEntries(listOf(first.id))))) as QuickTextReply.Prepared
            broker.request(QuickTextCommand.OpenUi(QuickUiTarget.REVIEW,prepared.value.token))
        }}
        compose.waitUntil(5000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.state.value.destination is Destination.QuickTextReview};compose.waitForIdle()
        assertTrue(model.phrases.value.any {it.id==first.id});compose.onNodeWithTag("quick-text-review").assertIsDisplayed()
        compose.onNodeWithText("此操作包含删除。确认后将应用整批修改。").assertExists()
        compose.onRoot().captureRoboImage("build/previews/quick-text-agent-review.png")
        compose.onNodeWithTag("apply-quick-text-batch").performClick()
        compose.waitUntil(5000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.phrases.value.none {it.id==first.id}};compose.waitForIdle()
        assertEquals(Destination.Home,model.state.value.destination)
    }
}

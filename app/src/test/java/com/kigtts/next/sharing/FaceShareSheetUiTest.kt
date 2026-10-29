package com.kigtts.next.sharing

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.identity.ProfileTile
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class FaceShareSheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ownShareShowsOnlyTheCardAndKeepsItsAnchorWhenContentChanges() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("face-share-test", model)
        val person = model.identities.create().copy(nickname = "我的名片", tags = listOf("我的标签"),
            tiles = listOf(ProfileTile(text = "不在分享面板显示的磁贴")))
        model.identities.save(person)
        model.faceShare.openSend(listOf(person.designId))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) { FaceShareHost(model) } }
        compose.onNodeWithText("分享名片").assertIsDisplayed()
        compose.onNodeWithText("我的名片").assertIsDisplayed()
        compose.onNodeWithText("不在分享面板显示的磁贴").assertDoesNotExist()
        compose.onNodeWithTag("face-profile").assertDoesNotExist()
        val before = compose.onNodeWithTag("face-share").getUnclippedBoundsInRoot()
        compose.runOnIdle { model.identities.save(person.copy(nickname = "新的昵称")) }
        compose.onNodeWithText("新的昵称").assertIsDisplayed()
        val after = compose.onNodeWithTag("face-share").getUnclippedBoundsInRoot()
        assertEquals(before.top.value, after.top.value, 1f)
        assertEquals((before.bottom - before.top).value, (after.bottom - after.top).value, 1f)
        compose.runOnIdle {
            val photos = (1..2).map { model.cards.create(PolaroidCard(title = "照片$it")) }
            model.faceShare.openSend(photos.map { it.id })
        }
        compose.onNodeWithText("2 张照片").assertIsDisplayed()
        compose.onNodeWithTag("face-share-identity").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithTag("face-share").assertDoesNotExist()
    }

    @Test fun contentCanGrowAndScrollWithoutMovingTheHeaderOrSheet() {
        var expanded by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            FaceShareSheet("面对面返图", {}) {
                Text("顶部内容")
                if (expanded) Spacer(Modifier.height(1200.dp))
                Text("底部内容")
            }
        } }
        val before = compose.onNodeWithTag("face-share").getUnclippedBoundsInRoot()
        compose.runOnIdle { expanded = true }
        compose.onNodeWithText("底部内容").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("面对面返图").assertIsDisplayed()
        val after = compose.onNodeWithTag("face-share").getUnclippedBoundsInRoot()
        assertEquals(before.top.value, after.top.value, 1f)
        assertEquals((before.bottom - before.top).value, (after.bottom - after.top).value, 1f)
    }
}

package com.kigtts.next.sharing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
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

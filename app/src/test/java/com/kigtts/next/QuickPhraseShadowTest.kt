package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.home.PhraseTile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QuickPhraseShadowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun bottomRowLiftShadowStaysCenteredWhenEitherCardMoves() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        var at by mutableStateOf(Offset(18f, 340f))
        val phrase = QuickPhrase("快捷文本", "测试", colorMode = CardColorMode.CUSTOM, color = 0xFFFFFFFFL)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { MaterialTheme {
            Box(Modifier.size(300.dp, 470.dp).background(Color.White).testTag("shadow-stage")) {
                PhraseTile(phrase, {}, CornerNeighbors(), false, false, remember { MutableInteractionSource() },
                    Modifier.size(120.dp, 90.dp).graphicsLayer { translationX = at.x; translationY = at.y },
                    model.mediaRepository, elevation = 8.dp)
            }
        } } }
        for ((index, position) in listOf(Offset(18f,340f), Offset(162f,340f), Offset(150f,250f)).withIndex()) {
            compose.runOnIdle { at = position }
            val file = "build/previews/quick-bottom-shadow-$index.png"
            compose.onNodeWithTag("shadow-stage").captureRoboImage(file)
            val bitmap = BitmapFactory.decodeFile(file)
            try {
                val row = position.y.toInt() + 94
                val dark = (0 until bitmap.width).filter { android.graphics.Color.red(bitmap.getPixel(it, row)) < 252 }
                assertTrue("shadow remains visible", dark.isNotEmpty())
                val center = (dark.first() + dark.last()) / 2f
                assertEquals(position.x + 60f, center, 3f)
            } finally { bitmap.recycle() }
        }
    }
}

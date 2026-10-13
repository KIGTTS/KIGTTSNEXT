package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.cards.polaroidBranding
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidFrameStyle
import com.kigtts.plugin.api.PolaroidFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidPaperUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun previewPaperPixelsMatchWhiteDefaultsAndPreserveCustomBrandPaper() {
        var card by mutableStateOf(PolaroidCard(title="纯白"))
        var frame by mutableStateOf<PolaroidFrameStyle?>(null)
        compose.setContent {MaterialTheme {PolaroidCanvas(card,{null},Modifier.width(280.dp),frame=frame)}}
        fun pixel():Int {
            val output=File("build/previews/polaroid-paper-preview.png")
            compose.onNodeWithTag("polaroid/"+card.id).captureRoboImage(output.absolutePath)
            val image=output.inputStream().use {requireNotNull(BitmapFactory.decodeStream(it))}
            return try {image.getPixel(image.width/2,20)} finally {image.recycle()}
        }
        assertEquals(android.graphics.Color.WHITE,pixel())
        compose.runOnIdle {card=card.copy(frameStyleId="classic",frameColor=0xFFF7F2E8)}
        assertEquals(android.graphics.Color.WHITE,pixel())
        compose.runOnIdle {card=card.copy(frameColor=0xFF123456)}
        assertEquals(0xFF123456.toInt(),pixel())
        compose.runOnIdle {
            frame=PolaroidFrameStyle("brand.paper","品牌",owner="brand",color=0xFFF7F2E8)
            card=card.copy(frameStyleId="brand.paper",frameColor=0xFFFFFFFF)
        }
        assertEquals(0xFFF7F2E8.toInt(),pixel())
    }
    @Test fun nativePreviewUsesTheSameBottomRightLogoGeometryAndOmitsItOnBrandFrames() {
        var card by mutableStateOf(PolaroidCard(title="拍立得"))
        var frame by mutableStateOf<PolaroidFrameStyle?>(null)
        compose.setContent {MaterialTheme {PolaroidCanvas(card,{null},Modifier.width(280.dp),frame=frame)}}
        val ids=listOf("classic","instax.mini.classic","instax.square.classic","instax.wide.classic")
        ids.forEachIndexed {index,id->
            compose.runOnIdle {card=card.copy(frameStyleId=id)}
            compose.onNodeWithTag("polaroid-brand-logo").assertExists()
            val output=File("build/previews/polaroid-logo-$index.png")
            compose.onNodeWithTag("polaroid/"+card.id).captureRoboImage(output.absolutePath)
            val image=output.inputStream().use {requireNotNull(BitmapFactory.decodeStream(it))}
            val format=when(index) {0->PolaroidFormat.LEGACY;1->PolaroidFormat.MINI;2->PolaroidFormat.SQUARE;else->PolaroidFormat.WIDE}
            val box=requireNotNull(polaroidBranding(card));val unit=image.width/format.geometry.paperWidth
            try {
                var ink=0
                for(y in floor(box.y*unit).toInt() until ceil((box.y+box.height)*unit).toInt())
                    for(x in floor(box.x*unit).toInt() until ceil((box.x+box.width)*unit).toInt())
                        if(image.getPixel(x,y)!=android.graphics.Color.WHITE)ink++
                assertTrue("Native preview logo pixels missing for $id",ink>20)
            } finally {image.recycle()}
        }
        compose.runOnIdle {
            frame=PolaroidFrameStyle("brand.paper","合作款",owner="brand",format=PolaroidFormat.SQUARE)
            card=card.copy(frameStyleId="brand.paper")
        }
        compose.onNodeWithTag("polaroid-brand-logo").assertDoesNotExist()
    }
}

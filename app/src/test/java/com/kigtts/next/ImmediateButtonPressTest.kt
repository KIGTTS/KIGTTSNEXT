@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.components.*
import com.kigtts.next.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class ImmediateButtonPressTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun step(ms:Long) { repeat(((ms+15)/16).toInt()) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
    @Test fun aScrollableButtonReactsOnDownAndShortTapsAndCancellationNeverLeaveItPressed() {
        val source=MutableInteractionSource();var pressed=false;var clicks=0;val events=mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val value=source.collectIsPressedAsState().value;SideEffect { pressed=value }
            LaunchedEffect(source) { source.interactions.collect { events+=it.javaClass.simpleName } }
            Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
                ToolButton("立即按压",Icons.Default.Add,{clicks++},Modifier.testTag("immediate"),source)
                Spacer(Modifier.height(1200.dp))
            }
        } }
        compose.mainClock.autoAdvance=false
        val button=compose.onNodeWithTag("immediate")
        button.performTouchInput { down(center) };step(32);assertTrue("events=$events tree="+compose.onRoot().printToString(),pressed)
        button.performTouchInput { up() };step(16);assertTrue(pressed);assertEquals(1,clicks)
        step(500);assertFalse(pressed)
        repeat(8) { button.performTouchInput { down(center);advanceEventTime(8);up() };step(16) }
        step(500);assertFalse(pressed);assertEquals(9,clicks)
        button.performTouchInput { down(center);cancel() };step(500)
        assertFalse(pressed);assertEquals(9,clicks)
        compose.mainClock.autoAdvance=true
    }
    @Test fun selectedTabsAreCapsulesEvenWithNeighborsOnBothSides() {
        var selected by mutableStateOf(false);val source=MutableInteractionSource()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Surface(color=Color.Red) { ElasticTab(selected,{},source,CornerNeighbors(start=true,end=true),false,
                Modifier.testTag("capsule-tab"),icon={ Icon(Icons.Default.Add,null) },label={ Text("分组") }) }
        } }
        fun pixels(name:String):Triple<Int,Int,Int> {
            val path="build/previews/tab-$name.png";compose.onNodeWithTag("capsule-tab").captureRoboImage(path)
            val image=BitmapFactory.decodeFile(path);val density=compose.activity.resources.displayMetrics.density
            val top=((image.height/density-32f)/2f).coerceAtLeast(0f)
            return Triple(image.getPixel((4*density).toInt(),((top+3)*density).toInt()),
                image.getPixel(image.width-(4*density).toInt(),((top+3)*density).toInt()),
                image.getPixel(image.width/2,image.height/2)).also { image.recycle() }
        }
        pixels("connected")
        compose.runOnIdle { selected=true };compose.waitForIdle()
        val capsule=pixels("selected-capsule")
        assertEquals(android.graphics.Color.RED,capsule.first);assertEquals(android.graphics.Color.RED,capsule.second)
        assertNotEquals(android.graphics.Color.RED,capsule.third)
    }
}

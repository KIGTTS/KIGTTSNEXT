package com.kigtts.next

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.kigtts.next.ui.home.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SubtitlePresentationTest {
    private fun frame(text: String, revision: Int = 0) = SubtitleRenderFrame(AnnotatedString(text), TextStyle(fontSize = 40.sp), text.isEmpty(), revision)

    @Test fun incomingQuickTextIsTransparentFromItsFirstFrameAndInterruptedLayersStayVisible() = runBlocking {
        val memory = SubtitlePresentationMemory()
        assertEquals(1f, memory.change(frame("旧字幕")).alpha.value, 0f)
        val second = memory.change(frame("第二条", 1))
        assertEquals(0f, second.alpha.value, 0f)
        assertEquals(1f, second.leaving.single().opacity, 0f)
        second.alpha.snapTo(.35f)
        val third = memory.change(frame("第三条", 2))
        assertEquals(0f, third.alpha.value, 0f)
        assertEquals(listOf("旧字幕", "第二条"), third.leaving.map { it.frame.paragraph.text })
        assertEquals(1f, third.leaving.sumOf { it.opacity.toDouble() }.toFloat(), .001f)
    }
    @Test fun reducedMotionKeepsClearAndPresentationFadesButSkipsOrdinaryUpdates() = runBlocking {
        val memory = SubtitlePresentationMemory()
        memory.change(frame("甲"), true)
        assertEquals(1f, memory.change(frame("甲乙"), true).alpha.value, 0f)
        val clear = memory.change(frame(""), true)
        assertEquals(0f, clear.alpha.value, 0f)
        clear.alpha.snapTo(1f)
        assertEquals(0f, memory.change(frame("快捷文本", 1), true).alpha.value, 0f)
    }
}

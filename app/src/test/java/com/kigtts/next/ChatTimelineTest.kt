package com.kigtts.next

import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class ChatTimelineTest {
    @Test fun fileOnlyAssistantResultRemainsAnAnswer() {
        val file = ChatFile(PluginResource("workspace/result.gltf"), "result.gltf", 100)
        val message = ChatMessage("file", ChatRole.ASSISTANT, "", files = listOf(file))
        assertEquals(message, chatTimeline(listOf(message)).single().answer)
    }
    @Test fun onlyFinishedAdjacentThoughtsAndToolsMergeAndAnswersRemainIndependent() {
        val thought = ChatMessage("plan", ChatRole.ASSISTANT, "", reasoning = "检查资源", hasReasoning = true)
        val tool = ChatMessage("read", ChatRole.TOOL, "完成", toolName = "读取文件")
        val live = ChatMessage("download", ChatRole.TOOL, "", state = ChatMessageState.STREAMING)
        val thinking = ChatMessage("next", ChatRole.ASSISTANT, "", thinking = true, state = ChatMessageState.STREAMING)
        val rows = chatTimeline(listOf(thought, tool, live, thinking))
        assertEquals(listOf(2, 1, 1), rows.map { it.steps.size })
        assertTrue(rows[0].steps.none { it.running }); assertTrue(rows[1].steps.single().running)
        assertTrue(rows[2].steps.single().running)
        val complete = chatTimeline(listOf(thought, tool, live.copy(state = ChatMessageState.COMPLETE),
            thinking.copy(thinking = false, hasReasoning = true, text = "结果", state = ChatMessageState.STREAMING)))
        assertEquals(rows.first().key, complete.first().key)
        assertEquals(4, complete.first().steps.size)
        assertEquals("结果", complete.last().answer?.text)
        assertTrue(complete.last().steps.isEmpty())
    }
    @Test fun userAndAnswerBoundariesNeverMergeUnrelatedExecutionSequences() {
        val thought = ChatMessage("first", ChatRole.ASSISTANT, "回答", hasReasoning = true)
        val user = ChatMessage("user", ChatRole.USER, "下一问")
        val tool = ChatMessage("tool", ChatRole.TOOL, "完成")
        val rows = chatTimeline(listOf(thought, user, tool))
        assertEquals(4, rows.size)
        assertEquals("answer/first", rows[1].key)
        assertEquals("answer/user", rows[2].key)
        assertEquals(1, rows.last().steps.size)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}

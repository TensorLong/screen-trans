package com.yiqun.translator.data.remote.ai.chatgpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies that `parseSenseGroupResponse` tolerates markdown-fenced JSON
 * responses (Claude / Gemini / DeepSeek / Kimi / Qwen frequently wrap their
 * JSON in ```json ... ``` despite the system prompt forbidding it). The fence
 * strip is universal — model-agnostic — so we cover several wrapping styles
 * and confirm plain JSON still parses unchanged.
 */
class ChatGPTKitFenceStripTest {

    private val sentence = "They will make careful choices about the project."
    private val word = "make"
    private val offset = sentence.indexOf("make")
    private val expectedChunk = "make careful choices"
    private val expectedTranslation = "做出谨慎选择"
    private val innerJson =
        """{"source_chunk":"$expectedChunk","target_chunk":"$expectedTranslation"}"""

    @Test
    fun plainJsonPassesThroughUnchanged() {
        val group = ChatGPTKit.parseSenseGroupResponse(
            raw = innerJson,
            sentence = sentence,
            word = word,
            pointedTokenOffset = offset,
        )
        assertNotNull(group)
        assertEquals(expectedChunk, group?.text)
        assertEquals(expectedTranslation, group?.translation)
    }

    @Test
    fun fenceWithJsonLanguageTagIsStripped() {
        val raw = "```json\n$innerJson\n```"
        val group = ChatGPTKit.parseSenseGroupResponse(
            raw = raw,
            sentence = sentence,
            word = word,
            pointedTokenOffset = offset,
        )
        assertNotNull(group)
        assertEquals(expectedChunk, group?.text)
        assertEquals(expectedTranslation, group?.translation)
    }

    @Test
    fun fenceWithoutLanguageTagIsStripped() {
        val raw = "```\n$innerJson\n```"
        val group = ChatGPTKit.parseSenseGroupResponse(
            raw = raw,
            sentence = sentence,
            word = word,
            pointedTokenOffset = offset,
        )
        assertNotNull(group)
        assertEquals(expectedChunk, group?.text)
    }

    @Test
    fun fenceWithSurroundingWhitespaceIsTolerated() {
        val raw = "   \n   ```json\n$innerJson\n```   \n   "
        val group = ChatGPTKit.parseSenseGroupResponse(
            raw = raw,
            sentence = sentence,
            word = word,
            pointedTokenOffset = offset,
        )
        assertNotNull(group)
        assertEquals(expectedChunk, group?.text)
    }

    @Test
    fun fenceWithoutClosingBackticksReturnsNull() {
        // Malformed fence — the helper returns the raw string unchanged, and
        // parseSenseGroupResponse catches the JsonSyntaxException upstream
        // (the suspend wrapper). At the static-method level we verify the
        // direct call surfaces the parser failure (i.e. either throws or
        // returns null) rather than silently succeeding on garbage.
        val raw = "```json\n$innerJson"
        val result = runCatching {
            ChatGPTKit.parseSenseGroupResponse(
                raw = raw,
                sentence = sentence,
                word = word,
                pointedTokenOffset = offset,
            )
        }
        // Either the parser throws (caller wraps in try/catch) or it returns
        // null because the leading ``` makes the chunk lookup fail. Both are
        // acceptable; what we forbid is a *successful* parse of malformed input.
        if (result.isSuccess) {
            assertNull("malformed fence must not yield a sense group", result.getOrNull())
        }
    }

    @Test
    fun stripHelperReturnsInputUnchangedWhenNoFence() {
        assertEquals(innerJson, ChatGPTKit.stripMarkdownFence(innerJson))
    }

    @Test
    fun stripHelperRemovesJsonFence() {
        assertEquals(innerJson, ChatGPTKit.stripMarkdownFence("```json\n$innerJson\n```"))
    }

    @Test
    fun stripHelperRemovesPlainFence() {
        assertEquals(innerJson, ChatGPTKit.stripMarkdownFence("```\n$innerJson\n```"))
    }

    @Test
    fun stripHelperToleratesSurroundingWhitespace() {
        assertEquals(
            innerJson,
            ChatGPTKit.stripMarkdownFence("   \n   ```json\n$innerJson\n```   \n   ")
        )
    }
}

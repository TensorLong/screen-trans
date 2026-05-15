package com.yiqun.translator.data.remote.ai.chatgpt

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenseGroupRequestCacheTest {

    @After
    fun tearDown() {
        SenseGroupRequestCache.clear()
    }

    @Test
    fun cacheReturnsOnlyExactSenseGroupRequestMatches() {
        val group = SenseGroup("careful decision", "", 25..41)

        SenseGroupRequestCache.put(
            model = "openai/gpt-4o-mini",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            word = "decision",
            sentence = "The committee made a careful decision after reviewing the evidence.",
            pointedTokenOffset = 30,
            sourceLanguageCode = "en",
            targetLanguageCode = "zh-CN",
            senseGroup = group,
        )

        assertEquals(
            group,
            SenseGroupRequestCache.get(
                model = "openai/gpt-4o-mini",
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                word = "decision",
                sentence = "The committee made a careful decision after reviewing the evidence.",
                pointedTokenOffset = 30,
                sourceLanguageCode = "en",
                targetLanguageCode = "zh-CN",
            )
        )
        assertNull(
            SenseGroupRequestCache.get(
                model = "openai/gpt-4o-mini",
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                word = "careful",
                sentence = "The committee made a careful decision after reviewing the evidence.",
                pointedTokenOffset = 30,
                sourceLanguageCode = "en",
                targetLanguageCode = "zh-CN",
            )
        )
    }
}

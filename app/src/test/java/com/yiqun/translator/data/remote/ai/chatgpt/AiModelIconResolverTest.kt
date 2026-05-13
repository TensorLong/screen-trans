package com.yiqun.translator.data.remote.ai.chatgpt

import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelIconResolverTest {

    @Test
    fun qwenModelsUseQwenBadge() {
        val icon = AiModelIconResolver.resolve("qwen-max")

        assertEquals("千", icon.label)
        assertEquals("Qwen", icon.contentDescription)
    }

    @Test
    fun tongyiModelsUseQwenBadge() {
        val icon = AiModelIconResolver.resolve("通义千问")

        assertEquals("千", icon.label)
        assertEquals("Qwen", icon.contentDescription)
    }

    @Test
    fun openAiModelsUseGptBadge() {
        val icon = AiModelIconResolver.resolve("openai/gpt-4o-mini")

        assertEquals("GPT", icon.label)
        assertEquals("OpenAI", icon.contentDescription)
    }

    @Test
    fun blankModelUsesGenericAiBadge() {
        val icon = AiModelIconResolver.resolve(" ")

        assertEquals("AI", icon.label)
        assertEquals("AI model", icon.contentDescription)
    }
}

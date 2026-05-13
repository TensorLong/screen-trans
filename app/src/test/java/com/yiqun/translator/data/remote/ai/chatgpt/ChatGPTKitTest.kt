package com.yiqun.translator.data.remote.ai.chatgpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGPTKitTest {

    @Test
    fun defaultModel_isGpt4oMini() {
        assertEquals("gpt-4o-mini", ChatGPTKit.DEFAULT_MODEL)
    }

    @Test
    fun endpointUrl_appendsChatCompletionsPathOnce() {
        assertEquals(
            "https://gateway.example.com/api/v1/chat/completions",
            ChatGPTKit.endpointUrl("https://gateway.example.com/api/", ChatGPTKit.CHAT_COMPLETIONS_PATH)
        )
    }

    @Test
    fun endpointUrl_appendsModelsPathOnce() {
        assertEquals(
            "https://gateway.example.com/api/v1/models",
            ChatGPTKit.endpointUrl("https://gateway.example.com/api", ChatGPTKit.MODELS_PATH)
        )
    }

    @Test
    fun modelIds_areSortedAndBlankIdsAreRemoved() {
        val ids = ChatGPTKit.modelIds(
            ModelListResponse(
                data = listOf(
                    ModelInfo(id = "z-model"),
                    ModelInfo(id = ""),
                    ModelInfo(id = "a-model"),
                )
            )
        )

        assertEquals(listOf("a-model", "z-model"), ids)
    }

    @Test
    fun configuredModelFallsBackToDefaultWhenBlank() {
        assertEquals(ChatGPTKit.DEFAULT_MODEL, ChatGPTKit.configuredModel(" "))
        assertTrue(ChatGPTKit.configuredModel("custom-model") == "custom-model")
    }
}

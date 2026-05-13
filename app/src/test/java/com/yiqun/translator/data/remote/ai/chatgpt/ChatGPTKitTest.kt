package com.yiqun.translator.data.remote.ai.chatgpt

import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun endpointUrl_doesNotDuplicateV1WhenBaseUrlAlreadyContainsV1() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ChatGPTKit.endpointUrl("https://openrouter.ai/api/v1", ChatGPTKit.CHAT_COMPLETIONS_PATH)
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

    @Test
    fun configuredModelUsesOpenRouterDefaultWhenBlank() {
        assertEquals(
            "openai/gpt-4o-mini",
            ChatGPTKit.configuredModel(" ", "https://openrouter.ai/api")
        )
    }

    @Test
    fun configuredModelNormalizesLegacyDefaultForOpenRouter() {
        assertEquals(
            "openai/gpt-4o-mini",
            ChatGPTKit.configuredModel("gpt-4o-mini", "https://openrouter.ai/api/")
        )
    }

    @Test
    fun aiResponseDtoFieldsHaveStableSerializedNames() {
        assertSerializedName(ChatGPTResponse::class.java, "choices", "choices")
        assertSerializedName(Choice::class.java, "message", "message")
        assertSerializedName(Message::class.java, "role", "role")
        assertSerializedName(Message::class.java, "content", "content")
        assertSerializedName(ModelListResponse::class.java, "data", "data")
        assertSerializedName(ModelInfo::class.java, "id", "id")
    }

    private fun assertSerializedName(clazz: Class<*>, fieldName: String, expectedName: String) {
        val annotation = clazz.getDeclaredField(fieldName).getAnnotation(SerializedName::class.java)
        assertNotNull("$fieldName should keep its JSON field name in release builds", annotation)
        assertEquals(expectedName, annotation?.value)
    }
}

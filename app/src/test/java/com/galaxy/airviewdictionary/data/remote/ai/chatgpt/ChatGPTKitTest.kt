package com.galaxy.airviewdictionary.data.remote.ai.chatgpt

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGPTKitTest {

    @Test
    fun defaultModel_isGpt4oMini() {
        assertEquals("gpt-4o-mini", ChatGPTKit.DEFAULT_MODEL)
    }
}

package com.yiqun.translator.data.remote.ai

import com.yiqun.translator.data.AVDRepository
import com.yiqun.translator.data.remote.ai.chatgpt.ChatGPTKit
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroupErrorEvent
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SenseGroupRepository @Inject constructor(
    private val chatGPTKit: ChatGPTKit,
) : AVDRepository() {

    /**
     * Side-channel of failure events emitted by [senseGroupAt] when the
     * underlying model call cannot return a usable chunk. The UI layer
     * collects this to surface a localized snackbar/toast.
     */
    val senseGroupErrors: SharedFlow<SenseGroupErrorEvent> = chatGPTKit.senseGroupErrors

    suspend fun senseGroupAt(
        word: String,
        sentence: String,
        pointedTokenOffset: Int,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): SenseGroup? {
        return chatGPTKit.senseGroupAt(
            word = word,
            sentence = sentence,
            pointedTokenOffset = pointedTokenOffset,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCode = targetLanguageCode,
        )
    }

    override fun onZeroReferences() {

    }
}

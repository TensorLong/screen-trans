package com.yiqun.translator.data.remote.ai

import com.yiqun.translator.data.AVDRepository
import com.yiqun.translator.data.remote.ai.chatgpt.ChatGPTKit
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SenseGroupRepository @Inject constructor(
    private val chatGPTKit: ChatGPTKit,
) : AVDRepository() {

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

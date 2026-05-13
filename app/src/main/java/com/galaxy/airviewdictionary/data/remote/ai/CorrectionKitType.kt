package com.galaxy.airviewdictionary.data.remote.ai

import com.galaxy.airviewdictionary.R

enum class CorrectionKitType(
    val text: String,
    val ciResourceId: Int,
    val providersUrl: String,
) {
    CHAT_GPT(
        text = "Chat GPT",
        ciResourceId = R.drawable.ci_openai,
        providersUrl = "https://openai.com/news/",
    )
}

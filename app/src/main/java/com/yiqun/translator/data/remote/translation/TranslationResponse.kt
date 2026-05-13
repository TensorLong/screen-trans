package com.yiqun.translator.data.remote.translation

sealed interface TranslationResponse {
    data class Success(val result: Transaction) : TranslationResponse
    data class Error(val t: Throwable) : TranslationResponse
}


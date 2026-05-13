package com.galaxy.airviewdictionary.data.remote.translation

sealed interface TranslationResponse {
    data class Success(val result: Transaction) : TranslationResponse
    data class Error(val t: Throwable) : TranslationResponse
}


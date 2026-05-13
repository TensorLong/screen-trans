package com.galaxy.airviewdictionary.data.local.vision.model

sealed interface VisionResponse {
    data class Success(val result: Transaction) : VisionResponse
    data class Error(val t: Throwable) : VisionResponse
}


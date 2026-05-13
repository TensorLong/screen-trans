package com.galaxy.airviewdictionary.data.local.capture

import android.graphics.Bitmap

sealed interface CaptureResponse {
    data class Success(val bitmap: Bitmap) : CaptureResponse
    data class Error(val t: Throwable) : CaptureResponse
}
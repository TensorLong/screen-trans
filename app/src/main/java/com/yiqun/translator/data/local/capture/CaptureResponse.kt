package com.yiqun.translator.data.local.capture

import android.graphics.Bitmap
import android.graphics.Rect

sealed interface CaptureResponse {
    data class Success(
        val bitmap: Bitmap,
        val screenRect: Rect = Rect().apply {
            left = 0
            top = 0
            right = bitmap.width
            bottom = bitmap.height
        },
    ) : CaptureResponse

    data class Error(val t: Throwable) : CaptureResponse
}

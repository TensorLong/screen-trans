package com.yiqun.translator.data.local.capture

import android.graphics.Bitmap

class NoMediaProjectionTokenException(message: String) : Exception(message)

class CapturedImageInvalidException : Exception()

class CapturePreventedException(val checkerBitmap: Bitmap) : Exception()

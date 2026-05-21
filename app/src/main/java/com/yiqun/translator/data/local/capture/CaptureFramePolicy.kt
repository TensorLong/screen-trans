package com.yiqun.translator.data.local.capture

object CaptureFramePolicy {
    private const val CLEAN_CAPTURE_DISCARD_FRAME_COUNT = 1
    private const val IMAGE_READER_MAX_IMAGES = 1

    fun cleanCaptureDiscardFrameCount(): Int = CLEAN_CAPTURE_DISCARD_FRAME_COUNT

    fun imageReaderMaxImages(): Int = IMAGE_READER_MAX_IMAGES
}

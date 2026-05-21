package com.yiqun.translator.data.local.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureFramePolicyTest {

    @Test
    fun cleanCaptureDropsOnePossiblyStaleFrameWithoutGrowingImageReaderBuffers() {
        assertEquals(1, CaptureFramePolicy.cleanCaptureDiscardFrameCount())
        assertEquals(1, CaptureFramePolicy.imageReaderMaxImages())
    }
}

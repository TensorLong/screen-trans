package com.yiqun.translator.ui.screen.overlay.fixedarea

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedAreaRecognitionPolicyTest {

    @Test
    fun firstRecognitionRunsImmediatelyAndSubsequentPollingKeepsExistingCadence() {
        assertEquals(0L, FixedAreaRecognitionPolicy.firstRecognitionDelayMs())
        assertEquals(100L, FixedAreaRecognitionPolicy.pollingDelayMs())
    }
}

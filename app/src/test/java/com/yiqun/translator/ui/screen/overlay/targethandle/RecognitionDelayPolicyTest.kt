package com.yiqun.translator.ui.screen.overlay.targethandle

import com.yiqun.translator.data.local.vision.TextDetectMode
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionDelayPolicyTest {

    @Test
    fun pointedModesStartAfterLessThanHalfThePreviousDelay() {
        val previousDelayMs = 130L
        val optimizedModes = listOf(
            TextDetectMode.WORD,
            TextDetectMode.SENTENCE,
            TextDetectMode.SENSE_GROUP,
            TextDetectMode.PARAGRAPH,
        )

        optimizedModes.forEach { mode ->
            assertTrue(
                "$mode should begin recognition in less than half the old delay",
                RecognitionDelayPolicy.pointerStoppedDelayMs(mode) < previousDelayMs / 2,
            )
        }
    }

    @Test
    fun areaModesKeepTheirLongerGestureSettleDelay() {
        assertTrue(
            RecognitionDelayPolicy.pointerStoppedDelayMs(TextDetectMode.SELECT) >
                    RecognitionDelayPolicy.pointerStoppedDelayMs(TextDetectMode.WORD)
        )
    }

    @Test
    fun captureBeginsImmediatelyAfterStablePointerEmission() {
        assertTrue(RecognitionDelayPolicy.captureStartDelayMs() == 0L)
    }
}

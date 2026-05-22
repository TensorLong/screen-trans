package com.yiqun.translator.ui.screen.overlay.targethandle

import com.yiqun.translator.data.local.vision.TextDetectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionDelayPolicyTest {

    @Test
    fun pointedModesWaitForStablePointerDwell() {
        val pointedModes = listOf(
            TextDetectMode.WORD,
            TextDetectMode.SENTENCE,
            TextDetectMode.SENSE_GROUP,
            TextDetectMode.PARAGRAPH,
        )

        pointedModes.forEach { mode ->
            assertEquals(
                "$mode should wait for stable pointer dwell before capture",
                200L,
                RecognitionDelayPolicy.pointerStoppedDelayMs(mode),
            )
        }
    }

    @Test
    fun areaModesKeepTheirGestureSettleDelay() {
        assertEquals(
            90L,
            RecognitionDelayPolicy.pointerStoppedDelayMs(TextDetectMode.SELECT),
        )
        assertEquals(
            90L,
            RecognitionDelayPolicy.pointerStoppedDelayMs(TextDetectMode.FIXED_AREA),
        )
    }

    @Test
    fun captureBeginsImmediatelyAfterStablePointerEmission() {
        assertTrue(RecognitionDelayPolicy.captureStartDelayMs() == 0L)
    }

    @Test
    fun dwellTimerRestartsOnlyAfterSignificantPointerMove() {
        assertFalse(
            PointerDwellPolicy.hasSignificantPointerMove(
                previousX = 10,
                previousY = 10,
                currentX = 15,
                currentY = 10,
                marginDistance = 6,
            )
        )
        assertTrue(
            PointerDwellPolicy.hasSignificantPointerMove(
                previousX = 10,
                previousY = 10,
                currentX = 17,
                currentY = 10,
                marginDistance = 6,
            )
        )
    }

    @Test
    fun duplicateStoppedPositionsStaySuppressedWithinMargin() {
        assertFalse(
            PointerDwellPolicy.shouldEmitPosition(
                currentX = 15,
                currentY = 10,
                lastEmittedX = 10,
                lastEmittedY = 10,
                marginDistance = 6,
            )
        )
        assertTrue(
            PointerDwellPolicy.shouldEmitPosition(
                currentX = 17,
                currentY = 10,
                lastEmittedX = 10,
                lastEmittedY = 10,
                marginDistance = 6,
            )
        )
    }
}

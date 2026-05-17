package com.yiqun.translator.ui.screen.overlay.targethandle

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointedRecognitionLifecyclePolicyTest {

    @Test
    fun preservesActiveRecognitionWhenPointerIsReleased() {
        assertTrue(
            PointedRecognitionLifecyclePolicy.shouldKeepActiveRecognition(
                motionEventAction = MotionEvent.ACTION_UP,
                recognitionInFlight = true,
            )
        )
    }

    @Test
    fun cancelsActiveRecognitionWhenPointerMovesAwayBeforeRelease() {
        assertFalse(
            PointedRecognitionLifecyclePolicy.shouldKeepActiveRecognition(
                motionEventAction = MotionEvent.ACTION_MOVE,
                recognitionInFlight = true,
            )
        )
    }

    @Test
    fun acceptsResolvedRecognitionForTheSameRequestAfterPointerRelease() {
        assertTrue(
            PointedRecognitionLifecyclePolicy.shouldAcceptResolvedRecognition(
                motionEventAction = MotionEvent.ACTION_UP,
                requestGeneration = 11L,
                activeGeneration = 11L,
            )
        )
    }

    @Test
    fun rejectsResolvedRecognitionAfterANewerPointingRequestStarts() {
        assertFalse(
            PointedRecognitionLifecyclePolicy.shouldAcceptResolvedRecognition(
                motionEventAction = MotionEvent.ACTION_UP,
                requestGeneration = 11L,
                activeGeneration = 12L,
            )
        )
    }
}

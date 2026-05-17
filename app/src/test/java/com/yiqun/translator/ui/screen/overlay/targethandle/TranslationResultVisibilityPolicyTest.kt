package com.yiqun.translator.ui.screen.overlay.targethandle

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationResultVisibilityPolicyTest {

    @Test
    fun acceptsMatchingResultAfterPointerRelease() {
        assertTrue(
            TranslationResultVisibilityPolicy.shouldDisplayResolvedTranslation(
                motionEventAction = MotionEvent.ACTION_UP,
                requestedSourceText = "Sentence detection joins words",
                activeSourceText = "Sentence detection joins words",
                translatedSourceText = "Sentence detection joins words",
                requestGeneration = 7L,
                activeGeneration = 7L,
            )
        )
    }

    @Test
    fun rejectsResultFromPreviousPointingRequest() {
        assertFalse(
            TranslationResultVisibilityPolicy.shouldDisplayResolvedTranslation(
                motionEventAction = MotionEvent.ACTION_UP,
                requestedSourceText = "Sentence detection joins words",
                activeSourceText = "The quick brown fox jumps",
                translatedSourceText = "Sentence detection joins words",
                requestGeneration = 7L,
                activeGeneration = 8L,
            )
        )
    }

    @Test
    fun rejectsResultWhenPointerIsCancelledBeforeResolution() {
        assertFalse(
            TranslationResultVisibilityPolicy.shouldDisplayResolvedTranslation(
                motionEventAction = MotionEvent.ACTION_CANCEL,
                requestedSourceText = "Sentence detection joins words",
                activeSourceText = "Sentence detection joins words",
                translatedSourceText = "Sentence detection joins words",
                requestGeneration = 7L,
                activeGeneration = 7L,
            )
        )
    }
}

package com.yiqun.translator.ui.screen.overlay.targethandle

import android.view.MotionEvent

object PointedRecognitionLifecyclePolicy {
    fun shouldKeepActiveRecognition(
        motionEventAction: Int?,
        recognitionInFlight: Boolean,
    ): Boolean {
        return recognitionInFlight && motionEventAction == MotionEvent.ACTION_UP
    }

    fun shouldAcceptResolvedRecognition(
        motionEventAction: Int?,
        requestGeneration: Long,
        activeGeneration: Long,
    ): Boolean {
        return requestGeneration == activeGeneration &&
                (TranslationResultVisibilityPolicy.isPointingAction(motionEventAction) ||
                        motionEventAction == MotionEvent.ACTION_UP)
    }
}

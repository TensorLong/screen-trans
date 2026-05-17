package com.yiqun.translator.ui.screen.overlay.targethandle

import android.view.MotionEvent

object TranslationResultVisibilityPolicy {
    fun isPointingAction(motionEventAction: Int?): Boolean {
        return motionEventAction == MotionEvent.ACTION_DOWN ||
                motionEventAction == MotionEvent.ACTION_MOVE
    }

    fun shouldDisplayResolvedTranslation(
        motionEventAction: Int?,
        requestedSourceText: String,
        activeSourceText: String?,
        translatedSourceText: String?,
        requestGeneration: Long,
        activeGeneration: Long,
    ): Boolean {
        if (!isPointingAction(motionEventAction) && motionEventAction != MotionEvent.ACTION_UP) {
            return false
        }
        return requestGeneration == activeGeneration &&
                requestedSourceText == activeSourceText &&
                requestedSourceText == translatedSourceText
    }
}

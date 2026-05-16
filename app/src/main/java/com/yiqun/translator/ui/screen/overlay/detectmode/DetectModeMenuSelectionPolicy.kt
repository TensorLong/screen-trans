package com.yiqun.translator.ui.screen.overlay.detectmode

import com.yiqun.translator.data.local.vision.TextDetectMode

object DetectModeMenuSelectionPolicy {
    fun isSelected(rowMode: TextDetectMode, currentMode: TextDetectMode?): Boolean {
        return rowMode == currentMode
    }
}

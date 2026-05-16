package com.yiqun.translator.ui.screen.overlay.detectmode

import com.yiqun.translator.data.local.vision.TextDetectMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectModeMenuSelectionPolicyTest {

    @Test
    fun onlyCurrentModeShowsSelectionIndicator() {
        assertTrue(
            DetectModeMenuSelectionPolicy.isSelected(
                rowMode = TextDetectMode.SENSE_GROUP,
                currentMode = TextDetectMode.SENSE_GROUP,
            )
        )
        assertFalse(
            DetectModeMenuSelectionPolicy.isSelected(
                rowMode = TextDetectMode.WORD,
                currentMode = TextDetectMode.SENSE_GROUP,
            )
        )
    }

    @Test
    fun noModeIsSelectedBeforeCurrentModeLoads() {
        assertFalse(
            DetectModeMenuSelectionPolicy.isSelected(
                rowMode = TextDetectMode.SENTENCE,
                currentMode = null,
            )
        )
    }
}

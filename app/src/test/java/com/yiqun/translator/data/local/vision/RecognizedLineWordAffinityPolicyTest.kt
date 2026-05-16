package com.yiqun.translator.data.local.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedLineWordAffinityPolicyTest {

    @Test
    fun acceptsWordsFromSameRecognizedLineAtRelaxedSimilarityBoundary() {
        assertTrue(
            RecognizedLineWordAffinityPolicy.accepts(
                axisSimilarityRatio = 0.5,
                fontHeightSimilarityRatio = 0.5,
            )
        )
    }

    @Test
    fun rejectsWordsFromSameRecognizedLineWhenAxisOrFontIsTooDifferent() {
        assertFalse(
            RecognizedLineWordAffinityPolicy.accepts(
                axisSimilarityRatio = 0.49,
                fontHeightSimilarityRatio = 0.5,
            )
        )
        assertFalse(
            RecognizedLineWordAffinityPolicy.accepts(
                axisSimilarityRatio = 0.5,
                fontHeightSimilarityRatio = 0.49,
            )
        )
    }

    @Test
    fun acceptsSplitSourceLineFragmentsOnSameBaselineWithWideSpacing() {
        assertTrue(
            RecognizedLineWordAffinityPolicy.acceptsSplitSourceLineFragment(
                axisSimilarityRatio = 0.9,
                fontHeightSimilarityRatio = 0.95,
                writeDirectionDistanceFontHeightRatio = 4.5,
            )
        )
    }

    @Test
    fun rejectsSplitSourceLineFragmentsWhenTooFarOrOffAxis() {
        assertFalse(
            RecognizedLineWordAffinityPolicy.acceptsSplitSourceLineFragment(
                axisSimilarityRatio = 0.74,
                fontHeightSimilarityRatio = 0.95,
                writeDirectionDistanceFontHeightRatio = 4.5,
            )
        )
        assertFalse(
            RecognizedLineWordAffinityPolicy.acceptsSplitSourceLineFragment(
                axisSimilarityRatio = 0.9,
                fontHeightSimilarityRatio = 0.95,
                writeDirectionDistanceFontHeightRatio = 8.1,
            )
        )
    }
}

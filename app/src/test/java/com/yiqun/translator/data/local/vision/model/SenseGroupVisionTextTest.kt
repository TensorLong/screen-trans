package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SenseGroupVisionTextTest {

    @Test
    fun fromCreatesSeparateHighlightBoxesForCrossLineChunk() {
        val sentence = Sentence(
            lines = mutableListOf(
                Line(
                    mutableListOf(
                        word("I", Rect(0, 0, 5, 10)),
                        word("saw", Rect(10, 0, 30, 10)),
                        word("the", Rect(35, 0, 55, 10)),
                    ),
                    WritingDirection.LTR,
                ),
                Line(
                    mutableListOf(
                        word("the", Rect(0, 20, 20, 30)),
                        word("dog", Rect(25, 20, 45, 30)),
                    ),
                    WritingDirection.LTR,
                ),
            ),
            writingDirection = WritingDirection.LTR,
            fontHeight = 10.0,
        )
        val chunkStart = sentence.representation.indexOf("the the")
        val chunkEnd = chunkStart + "the the".length

        val visionText = SenseGroupVisionText.from(
            parentSentence = sentence,
            senseGroup = SenseGroup(
                text = "the the",
                translation = "那两个 the",
                charRange = chunkStart..chunkEnd,
            ),
        )

        assertEquals(2, visionText.highlightBoxes.size)
        assertRect(Rect(35, 0, 55, 10), visionText.highlightBoxes[0])
        assertRect(Rect(0, 20, 20, 30), visionText.highlightBoxes[1])
        assertRect(Rect(0, 0, 55, 30), visionText.boundingBox)
    }

    private fun word(text: String, rect: Rect): Word {
        return Word(
            boundingBox = rect,
            representation = text,
            writingDirection = WritingDirection.LTR,
            chars = emptyList(),
        )
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left)
        assertEquals(expected.top, actual.top)
        assertEquals(expected.right, actual.right)
        assertEquals(expected.bottom, actual.bottom)
    }
}

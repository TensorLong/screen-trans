package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup
import com.yiqun.translator.extensions._unionWith


/**
 * A [VisionText] that wraps a single [SenseGroup] (semantic chunk inside a [Sentence]),
 * used when the user is in SENSE_GROUP detection mode.
 *
 * - [representation] is the chunk's source text (verbatim from the sentence).
 * - [boundingBox] is the union of the bounding boxes of all words that fall inside
 *   the chunk's character range.
 * - [precomputedTranslation] is the translation returned by ChatGPTKit.chunk();
 *   when non-empty, downstream code skips the normal translation engine and uses
 *   this value directly.
 *
 * Downstream UI ([VisionTextView]) treats this as the "else" branch (not Sentence,
 * not Paragraph), which renders a single overlay box over the union region — exactly
 * what we want for a chunk highlight.
 */
data class SenseGroupVisionText(
    val parentSentence: Sentence,
    val senseGroup: SenseGroup,
    override val boundingBox: Rect,
    val highlightBoxes: List<Rect>,
    val pointedWordBox: Rect?,
    override val writingDirection: WritingDirection,
    override val fontHeight: Double,
) : VisionText {

    override val representation: String
        get() = senseGroup.text

    val precomputedTranslation: String
        get() = senseGroup.translation

    companion object {
        fun from(
            parentSentence: Sentence,
            senseGroup: SenseGroup,
            fallbackBox: Rect? = null,
        ): SenseGroupVisionText {
            val boxes = highlightBoxesFor(parentSentence, senseGroup.charRange)
            val safeBoxes = if (boxes.isNotEmpty()) {
                boxes
            } else {
                listOfNotNull(fallbackBox)
            }
            val boundingBox = safeBoxes
                .takeIf { it.isNotEmpty() }
                ?.reduce { acc, rect -> acc._unionWith(rect) }
                ?: Rect()

            return SenseGroupVisionText(
                parentSentence = parentSentence,
                senseGroup = senseGroup,
                boundingBox = boundingBox,
                highlightBoxes = safeBoxes,
                pointedWordBox = fallbackBox,
                writingDirection = parentSentence.writingDirection,
                fontHeight = parentSentence.fontHeight,
            )
        }

        private fun highlightBoxesFor(sentence: Sentence, range: IntRange): List<Rect> {
            val boxes = mutableListOf<Rect>()
            for (line in sentence.lines) {
                val lineRects = mutableListOf<Rect>()
                for (word in line.words) {
                    val wordOffset = sentence.wordCharOffset(word) ?: continue
                    val wordEnd = wordOffset + word.representation.length
                    if (wordOffset < range.last && wordEnd > range.first) {
                        val charRects = charRectsInRange(word, wordOffset, range)
                        if (charRects.isNotEmpty()) {
                            lineRects.addAll(charRects)
                        } else {
                            lineRects.add(word.boundingBox)
                        }
                    }
                }
                if (lineRects.isNotEmpty()) {
                    boxes.add(lineRects.reduce { acc, rect -> acc._unionWith(rect) })
                }
            }
            return boxes
        }

        private fun charRectsInRange(word: Word, wordOffset: Int, range: IntRange): List<Rect> {
            if (word.chars.isEmpty()) return emptyList()

            val rects = mutableListOf<Rect>()
            var charOffset = wordOffset
            for (char in word.chars) {
                val charEnd = charOffset + char.representation.length
                if (charOffset < range.last && charEnd > range.first) {
                    rects.add(char.boundingBox)
                }
                charOffset = charEnd
            }
            return rects
        }
    }
}

package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.extensions._unionWith


/**
 */
data class Paragraph(
    val lines: MutableList<Line>,
    override val writingDirection: WritingDirection
) : VisionText {

    private var boundingBoxCache: Rect? = null
    private var linesHashCodeCache: Int? = null

    override val boundingBox: Rect
        get() {
            val currentWordsHashCode = lines.hashCode()
            if (boundingBoxCache == null || linesHashCodeCache != currentWordsHashCode) {
                boundingBoxCache = if (lines.isEmpty()) {
                    Rect()
                } else {
                    lines.map { it.boundingBox }.reduce { acc, rect -> acc._unionWith(rect) }
                }
                linesHashCodeCache = currentWordsHashCode
            }
            return boundingBoxCache!!
        }

    override val representation: String
        get() = lines.joinToString(separator = " ") { it.representation }

    override val fontHeight: Double
        get() = lines.map { it.fontHeight }.average()

    /**
     */
    var hasParallelLines = false

    private var _sentences: List<Sentence>? = null
    val sentences: List<Sentence>
        get() {
            if (_sentences == null) {
                _sentences = toSentences()
            }
            return _sentences!!
        }

    /**
     */
    fun averageLineHeight(): Double {
        return if (lines.isEmpty()) {
            0.0
        } else {
            lines.map { it.fontHeight }.average()
        }
    }

    /**
     */
    fun areAllInLine(): Boolean {
        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                if (!lines[i].isLineReturnDirectionOverlaps(lines[j])) {
                    return false
                }
            }
        }
        return true
    }

    /**
     */
    private fun toSentences(): List<Sentence> {
        val endPunctuation = setOf('.', '?', '!', '。', '።', '।', '།', '؟', ';')
        val sentences = mutableListOf<Sentence>()
        val remainingLines = lines.toMutableList()
        val fontHeight = fontHeight

        while (remainingLines.isNotEmpty()) {
            val sentenceLines = mutableListOf<Line>()
            var sentenceEndFound = false
            var sentenceEndIndex = -1
            var currentLineIndex = 0

            for ((lineIndex, line) in remainingLines.withIndex()) {
                val sentenceLineWords = mutableListOf<Word>()

                for ((wordIndex, word) in line.words.withIndex()) {
                    sentenceLineWords.add(word)
                    val isEndPunctuation = if (writingDirection == WritingDirection.RTL) {
                        word.representation.firstOrNull() in endPunctuation
                    } else {
                        word.representation.lastOrNull() in endPunctuation
                    }
                    if (isEndPunctuation) {
                        sentenceEndFound = true
                        sentenceEndIndex = wordIndex
                        break
                    }
                }

                if (sentenceLineWords.isNotEmpty()) {
                    val sentenceLine = Line(sentenceLineWords, writingDirection)
                    sentenceLines.add(sentenceLine)
                }

                currentLineIndex = lineIndex
                if (sentenceEndFound) break
            }

            if (sentenceLines.isNotEmpty()) {
                sentences.add(Sentence(sentenceLines, writingDirection, fontHeight))
            }

            if (sentenceEndFound && currentLineIndex < remainingLines.size) {
                val remainingWords = remainingLines[currentLineIndex].words.drop(sentenceEndIndex + 1).toMutableList()

                if (remainingWords.isNotEmpty()) {
                    remainingLines[currentLineIndex] = Line(remainingWords, writingDirection)
                } else {
                    remainingLines.removeAt(currentLineIndex)
                }

                remainingLines.subList(0, currentLineIndex).clear()
            } else {
                break
            }
        }

        return sentences
    }

    override fun toString(): String {
        return "Paragraph(boundingBox=$boundingBox, representation='$representation', lines=${lines.joinToString(separator = "\n", prefix = "[", postfix = "]") { it.toString() }})"
    }
}

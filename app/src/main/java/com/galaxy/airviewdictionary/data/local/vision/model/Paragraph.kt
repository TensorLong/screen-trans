package com.galaxy.airviewdictionary.data.local.vision.model

import android.graphics.Rect
import com.galaxy.airviewdictionary.data.local.vision.WritingDirection
import com.galaxy.airviewdictionary.extensions._unionWith


/**
 * 어러 개의 [Line] 으로 이루어진 문단
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
     * 복수의 Line 들이 하나의 행을 이루는 것이 있는지의 여부
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
     * lines의 모든 Line 높이의 평균을 반환
     */
    fun averageLineHeight(): Double {
        return if (lines.isEmpty()) {
            0.0
        } else {
            lines.map { it.fontHeight }.average()
        }
    }

    /**
     * 모든 Line이 줄바꿈 방향에서 겹치는지 확인 (모든 Line 이 하나의 행을 이루는지 확인)
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
     * 문장의 끝을 나타내는 기호를 포함하는 Word를 찾고, 해당 Word까지의 Line들을 포함하는 Sentence 생성
     */
    private fun toSentences(): List<Sentence> {
        val endPunctuation = setOf('.', '?', '!', '。', '።', '।', '།', '؟', ';')
        val sentences = mutableListOf<Sentence>()
        val remainingLines = lines.toMutableList()
        val fontHeight = fontHeight

        // 남아있는 lines를 순회
        while (remainingLines.isNotEmpty()) {
            val sentenceLines = mutableListOf<Line>()
            var sentenceEndFound = false
            var sentenceEndIndex = -1
            var currentLineIndex = 0

            // 각 line을 순회하며 문장의 끝을 나타내는 부호를 찾음
            for ((lineIndex, line) in remainingLines.withIndex()) {
                val sentenceLineWords = mutableListOf<Word>()

                // 각 word를 순회하며 문장의 끝을 나타내는 부호를 찾음
                for ((wordIndex, word) in line.words.withIndex()) {
                    sentenceLineWords.add(word)
                    val isEndPunctuation = if (writingDirection == WritingDirection.RTL) {
                        word.representation.firstOrNull() in endPunctuation
                    } else {
                        word.representation.lastOrNull() in endPunctuation
                    }
                    // 문장의 끝이 확인되면 break
                    if (isEndPunctuation) {
                        sentenceEndFound = true
                        sentenceEndIndex = wordIndex
                        break
                    }
                }

                // 문장의 끝이 확인된 line을 추가
                if (sentenceLineWords.isNotEmpty()) {
                    val sentenceLine = Line(sentenceLineWords, writingDirection)
                    sentenceLines.add(sentenceLine)
                }

                currentLineIndex = lineIndex
                if (sentenceEndFound) break
            }

            // Sentence를 생성하여 추가
            if (sentenceLines.isNotEmpty()) {
                sentences.add(Sentence(sentenceLines, writingDirection, fontHeight))
            }

            // 문장의 끝이 확인된 후 남아있는 words를 처리
            if (sentenceEndFound && currentLineIndex < remainingLines.size) {
                val remainingWords = remainingLines[currentLineIndex].words.drop(sentenceEndIndex + 1).toMutableList()

                // 남아있는 words가 있다면 새로운 line으로 추가
                if (remainingWords.isNotEmpty()) {
                    remainingLines[currentLineIndex] = Line(remainingWords, writingDirection)
                } else {
                    remainingLines.removeAt(currentLineIndex)
                }

                // 문장의 끝까지의 line들을 제거
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

package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup
import com.yiqun.translator.extensions._unionWith


/**
 */
data class Sentence(
    val lines: MutableList<Line>,
    override val writingDirection: WritingDirection,
    override val fontHeight: Double
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

    /**
     */
    val boundingPolygon: Polygon
        get() {
            return Polygon.fromRects(lines.map { it.boundingBox })
        }

    override val representation: String
        get() = lines.joinToString(separator = " ") { it.representation }

    /**
     *
     *
     */
    val senseGroupCache: java.util.concurrent.ConcurrentHashMap<Int, SenseGroup> =
        java.util.concurrent.ConcurrentHashMap()

    /**
     * Returns the character offset of [word] inside [representation], or `null`
     * if the word is not part of this sentence. Offsets follow the same join
     * convention used by [representation] / [Line.representation] — a single
     * space between adjacent words (and between adjacent lines).
     */
    fun wordCharOffset(word: Word): Int? {
        var offset = 0
        for ((lineIndex, line) in lines.withIndex()) {
            if (lineIndex > 0) offset += 1 // separator between lines
            for ((wordIndex, w) in line.words.withIndex()) {
                if (wordIndex > 0) offset += 1 // separator between words
                if (w === word) return offset
                offset += w.representation.length
            }
        }
        return null
    }
}
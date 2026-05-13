package com.yiqun.translator.data.local.vision.model

import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.extensions._divideByLarger
import kotlin.math.abs

/**
 */
interface VisionSingleLineText : VisionText {

    /**
     */
    val startPosition: Int
        get() = when (writingDirection) {
            WritingDirection.LTR -> boundingBox.left
            WritingDirection.RTL -> boundingBox.right
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> boundingBox.top
        }

    /**
     */
    override val fontHeight: Double
        get() = when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> boundingBox.height().toDouble()
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> boundingBox.width().toDouble()
        }

    /**
     */
    fun getAverageFontHeight(other: VisionSingleLineText): Double {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> (height + other.height).toDouble() / 2
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> (width + other.width).toDouble() / 2
        }
    }

    /**
     */
    fun getFontHeightSimilarityRatio(other: VisionSingleLineText): Double {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> height.toDouble()._divideByLarger(other.height.toDouble())
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> width.toDouble()._divideByLarger(other.width.toDouble())
        }
    }

    /**
     */
    fun getAxisDistance(other: VisionSingleLineText): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> abs(boundingBox.centerY() - other.boundingBox.centerY())
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> abs(boundingBox.centerX() - other.boundingBox.centerX())
        }
    }

    /**
     */
    fun getAxisSimilarityRatio(other: VisionSingleLineText): Double {
        val averageFontHeight: Double = getAverageFontHeight(other)
        val axisDistance = getAxisDistance(other)
        return averageFontHeight / (averageFontHeight + axisDistance)
    }

    companion object {
        @JvmStatic
        fun getComparator(writingDirection: WritingDirection): Comparator<VisionSingleLineText> {
            return when (writingDirection) {
                WritingDirection.LTR -> {
                    Comparator { singleLineText1, singleLineText2 ->
                        val topComparison = singleLineText1.boundingBox.top.compareTo(singleLineText2.boundingBox.top)
                        if (topComparison != 0) topComparison else singleLineText1.boundingBox.left.compareTo(singleLineText2.boundingBox.left)
                    }
                }

                WritingDirection.RTL -> {
                    Comparator { singleLineText1, singleLineText2 ->
                        val topComparison = singleLineText1.boundingBox.top.compareTo(singleLineText2.boundingBox.top)
                        if (topComparison != 0) topComparison else singleLineText2.boundingBox.right.compareTo(singleLineText1.boundingBox.right)
                    }
                }

                WritingDirection.TTB_LTR -> {
                    Comparator { singleLineText1, singleLineText2 ->
                        val leftComparison = singleLineText1.boundingBox.left.compareTo(singleLineText2.boundingBox.left)
                        if (leftComparison != 0) leftComparison else singleLineText1.boundingBox.top.compareTo(singleLineText2.boundingBox.top)
                    }
                }

                WritingDirection.TTB_RTL -> {
                    Comparator { singleLineText1, singleLineText2 ->
                        val rightComparison = singleLineText2.boundingBox.right.compareTo(singleLineText1.boundingBox.right)
                        if (rightComparison != 0) rightComparison else singleLineText1.boundingBox.top.compareTo(singleLineText2.boundingBox.top)
                    }
                }
            }
        }
    }
}

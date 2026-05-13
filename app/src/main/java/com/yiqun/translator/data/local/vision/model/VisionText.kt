package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection
import kotlin.math.min


/**
 */
interface VisionText {

    val boundingBox: Rect

    val representation: String

    val writingDirection: WritingDirection

    val fontHeight: Double

    override fun equals(other: Any?): Boolean

    val width: Int
        get() = boundingBox.width()

    val height: Int
        get() = boundingBox.height()

    val start: Int
        get() = if (writingDirection == WritingDirection.LTR) boundingBox.left else boundingBox.right

    val end: Int
        get() = if (writingDirection == WritingDirection.LTR) boundingBox.right else boundingBox.left

    val relativeBoundingBox: (Rect) -> Rect
        get() = { parentBoundingBox: Rect ->
            Rect(
                boundingBox.left - parentBoundingBox.left,
                boundingBox.top - parentBoundingBox.top,
                boundingBox.right - parentBoundingBox.left,
                boundingBox.bottom - parentBoundingBox.top
            )
        }

    /**
     */
    private fun isHorizontalOverlap(other: VisionText): Boolean {
        return boundingBox.left <= other.boundingBox.right && boundingBox.right >= other.boundingBox.left
    }

    /**
     */
    private fun horizontalOverlapRatio(other: VisionText): Double {
        val horizontalOverlapStart = maxOf(boundingBox.left, other.boundingBox.left)
        val horizontalOverlapEnd = minOf(boundingBox.right, other.boundingBox.right)
        val horizontalOverlapLength = horizontalOverlapEnd - horizontalOverlapStart
        return horizontalOverlapLength.toDouble() / min(boundingBox.width(), other.boundingBox.width()).toDouble()
    }

    /**
     */
    fun horizontalDistance(other: VisionText): Int {
        return if (boundingBox.right < other.boundingBox.left) {
            other.boundingBox.left - boundingBox.right
        } else if (other.boundingBox.right < boundingBox.left) {
            boundingBox.left - other.boundingBox.right
        } else {
            0
        }
    }

    /**
     */
    private fun isVerticalOverlap(other: VisionText): Boolean {
        return boundingBox.top <= other.boundingBox.bottom && boundingBox.bottom >= other.boundingBox.top
    }

    /**
     */
    private fun verticalOverlapRatio(other: VisionText): Double {
        val verticalOverlapStart = maxOf(boundingBox.top, other.boundingBox.top)
        val verticalOverlapEnd = minOf(boundingBox.bottom, other.boundingBox.bottom)
        val verticalOverlapLength = verticalOverlapEnd - verticalOverlapStart
        return verticalOverlapLength.toDouble() / min(boundingBox.height(), other.boundingBox.height()).toDouble()
    }

    /**
     */
    fun verticalDistance(other: VisionText): Int {
        return if (boundingBox.bottom < other.boundingBox.top) {
            other.boundingBox.top - boundingBox.bottom
        } else if (other.boundingBox.bottom < boundingBox.top) {
            boundingBox.top - other.boundingBox.bottom
        } else {
            0
        }
    }

    /**
     */
    fun isWriteDirectionOverlaps(other: VisionText): Boolean {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> isHorizontalOverlap(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> isVerticalOverlap(other)
        }
    }

    /**
     */
    fun getWriteDirectionOverlapRatio(other: VisionText): Double {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> horizontalOverlapRatio(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> verticalOverlapRatio(other)
        }
    }

    /**
     */
    fun getWriteDirectionDistance(other: VisionText): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> horizontalDistance(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> verticalDistance(other)
        }
    }

    /**
     */
    fun isLineReturnDirectionOverlaps(other: VisionText): Boolean {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> isVerticalOverlap(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> isHorizontalOverlap(other)
        }
    }

    /**
     */
    fun getLineReturnDirectionDistance(other: VisionText): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> verticalDistance(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> horizontalDistance(other)
        }
    }
}

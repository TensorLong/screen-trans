package com.galaxy.airviewdictionary.data.local.vision.model

import android.graphics.Rect
import com.galaxy.airviewdictionary.data.local.vision.WritingDirection
import kotlin.math.min


/**
 * Vision API 에서 인식된 텍스트를 나타내는 인터페이스.
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
     * 다른 VisionText 와 x축 방향으로 겹치는지의 여부
     */
    private fun isHorizontalOverlap(other: VisionText): Boolean {
        return boundingBox.left <= other.boundingBox.right && boundingBox.right >= other.boundingBox.left
    }

    /**
     * 다른 VisionText 과 width 가 작은 것이 큰 것에 x축 방향으로 겹치는 비율
     */
    private fun horizontalOverlapRatio(other: VisionText): Double {
        val horizontalOverlapStart = maxOf(boundingBox.left, other.boundingBox.left) // 겹침 시작
        val horizontalOverlapEnd = minOf(boundingBox.right, other.boundingBox.right) // 겹침 끝
        val horizontalOverlapLength = horizontalOverlapEnd - horizontalOverlapStart // 겹치는 길이
        return horizontalOverlapLength.toDouble() / min(boundingBox.width(), other.boundingBox.width()).toDouble()
    }

    /**
     * 다른 VisionText 와의 x축 거리
     */
    fun horizontalDistance(other: VisionText): Int {
        return if (boundingBox.right < other.boundingBox.left) {
            // this가 other의 왼쪽에 있는 경우
            other.boundingBox.left - boundingBox.right
        } else if (other.boundingBox.right < boundingBox.left) {
            // other가 this의 왼쪽에 있는 경우
            boundingBox.left - other.boundingBox.right
        } else {
            // this와 other가 겹치는 경우
            0
        }
    }

    /**
     * 다른 VisionText 와 y축 방향으로 겹치는지의 여부
     */
    private fun isVerticalOverlap(other: VisionText): Boolean {
        return boundingBox.top <= other.boundingBox.bottom && boundingBox.bottom >= other.boundingBox.top
    }

    /**
     * 다른 VisionText 과 height 가 작은 것이 큰 것에 y축 방향으로 겹치는 비율
     */
    private fun verticalOverlapRatio(other: VisionText): Double {
        val verticalOverlapStart = maxOf(boundingBox.top, other.boundingBox.top) // 겹침 시작
        val verticalOverlapEnd = minOf(boundingBox.bottom, other.boundingBox.bottom) // 겹침 끝
        val verticalOverlapLength = verticalOverlapEnd - verticalOverlapStart // 겹치는 길이
        return verticalOverlapLength.toDouble() / min(boundingBox.height(), other.boundingBox.height()).toDouble()
    }

    /**
     * 다른 VisionText 와의 y축 거리
     */
    fun verticalDistance(other: VisionText): Int {
        return if (boundingBox.bottom < other.boundingBox.top) {
            // this가 other의 위쪽에 있는 경우
            other.boundingBox.top - boundingBox.bottom
        } else if (other.boundingBox.bottom < boundingBox.top) {
            // other가 this의 위쪽에 있는 경우
            boundingBox.top - other.boundingBox.bottom
        } else {
            // this와 other가 겹치는 경우
            0
        }
    }

    /**
     * 다른 VisionText 와의 읽기방향 겹침 여부
     */
    fun isWriteDirectionOverlaps(other: VisionText): Boolean {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> isHorizontalOverlap(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> isVerticalOverlap(other)
        }
    }

    /**
     * 다른 VisionText 와의 읽기방향 겹침 비율
     */
    fun getWriteDirectionOverlapRatio(other: VisionText): Double {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> horizontalOverlapRatio(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> verticalOverlapRatio(other)
        }
    }

    /**
     * 다른 VisionSingleLineText 와의 읽기방향 거리
     */
    fun getWriteDirectionDistance(other: VisionText): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> horizontalDistance(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> verticalDistance(other)
        }
    }

    /**
     * 다른 VisionText 와의 줄바꿈 방향 겹침 여부
     */
    fun isLineReturnDirectionOverlaps(other: VisionText): Boolean {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> isVerticalOverlap(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> isHorizontalOverlap(other)
        }
    }

    /**
     * 다른 VisionSingleLineText 와의 줄바꿈 방향 거리 (행간)
     */
    fun getLineReturnDirectionDistance(other: VisionText): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> verticalDistance(other)
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> horizontalDistance(other)
        }
    }
}

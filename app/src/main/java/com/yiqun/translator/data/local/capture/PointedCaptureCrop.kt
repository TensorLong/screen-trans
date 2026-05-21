package com.yiqun.translator.data.local.capture

import android.graphics.Point
import android.graphics.Rect
import com.yiqun.translator.data.local.vision.TextDetectMode

object PointedCaptureCrop {
    private const val LOCAL_BAND_SCREEN_RATIO_NUMERATOR = 2
    private const val LOCAL_BAND_SCREEN_RATIO_DENOMINATOR = 3
    private const val MIN_LOCAL_BAND_HEIGHT_PX = 960

    fun boundsFor(
        screenWidth: Int,
        screenHeight: Int,
        pointer: Point,
        textDetectMode: TextDetectMode = TextDetectMode.WORD,
    ): Rect = boundsFor(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        pointerX = pointer.x,
        pointerY = pointer.y,
        textDetectMode = textDetectMode,
    )

    fun boundsFor(
        screenWidth: Int,
        screenHeight: Int,
        pointerX: Int,
        pointerY: Int,
        textDetectMode: TextDetectMode = TextDetectMode.WORD,
    ): Rect {
        if (screenWidth <= 0 || screenHeight <= 0) return Rect()
        if (textDetectMode == TextDetectMode.PARAGRAPH) {
            return rectOf(0, 0, screenWidth, screenHeight)
        }

        val targetHeight = maxOf(
            MIN_LOCAL_BAND_HEIGHT_PX,
            (screenHeight * LOCAL_BAND_SCREEN_RATIO_NUMERATOR) / LOCAL_BAND_SCREEN_RATIO_DENOMINATOR,
        ).coerceAtMost(screenHeight)
        if (targetHeight >= screenHeight) {
            return rectOf(0, 0, screenWidth, screenHeight)
        }

        val safePointerY = pointerY.coerceIn(0, screenHeight - 1)
        val top = (safePointerY - targetHeight / 2).coerceIn(0, screenHeight - targetHeight)
        return rectOf(0, top, screenWidth, top + targetHeight)
    }

    private fun rectOf(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }
}

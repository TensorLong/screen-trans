package com.yiqun.translator.data.local.capture

import android.graphics.Point
import android.graphics.Rect

object PointedCaptureCrop {
    private const val CROP_HEIGHT_DIVISOR = 3
    private const val SMALL_SCREEN_WIDTH = 480
    private const val SMALL_SCREEN_HEIGHT = 640

    fun boundsFor(
        screenWidth: Int,
        screenHeight: Int,
        pointer: Point,
    ): Rect = boundsFor(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        pointerX = pointer.x,
        pointerY = pointer.y,
    )

    fun boundsFor(
        screenWidth: Int,
        screenHeight: Int,
        pointerX: Int,
        pointerY: Int,
    ): Rect {
        if (screenWidth <= 0 || screenHeight <= 0) return Rect()

        if (screenWidth <= SMALL_SCREEN_WIDTH || screenHeight <= SMALL_SCREEN_HEIGHT) {
            return rectOf(0, 0, screenWidth, screenHeight)
        }

        val cropHeight = (screenHeight / CROP_HEIGHT_DIVISOR)
            .coerceAtLeast(1)
            .coerceAtMost(screenHeight)
        val top = (pointerY - cropHeight / 2)
            .coerceIn(0, screenHeight - cropHeight)

        return rectOf(0, top, screenWidth, top + cropHeight)
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

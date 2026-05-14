package com.yiqun.translator.data.local.capture

import android.graphics.Point
import android.graphics.Rect

object PointedCaptureCrop {
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
        return rectOf(0, 0, screenWidth, screenHeight)
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

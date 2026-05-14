package com.yiqun.translator.data.local.vision

import android.graphics.Rect

object VisionCoordinateMapper {
    fun toScreenRect(rect: Rect, offsetX: Int, offsetY: Int): Rect {
        return rectOf(
            left = rect.left + offsetX,
            top = rect.top + offsetY,
            right = rect.right + offsetX,
            bottom = rect.bottom + offsetY,
        )
    }

    fun toLocalRect(rect: Rect, offsetX: Int, offsetY: Int): Rect {
        return rectOf(
            left = rect.left - offsetX,
            top = rect.top - offsetY,
            right = rect.right - offsetX,
            bottom = rect.bottom - offsetY,
        )
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

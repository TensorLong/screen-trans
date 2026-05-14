package com.yiqun.translator.data.local.vision

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class VisionCoordinateMapperTest {

    @Test
    fun rectToScreenAddsCaptureOrigin() {
        val localRect = rectOf(10, 20, 110, 44)

        val screenRect = VisionCoordinateMapper.toScreenRect(
            rect = localRect,
            offsetX = 0,
            offsetY = 624,
        )

        assertRect(rectOf(10, 644, 110, 668), screenRect)
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left)
        assertEquals(expected.top, actual.top)
        assertEquals(expected.right, actual.right)
        assertEquals(expected.bottom, actual.bottom)
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

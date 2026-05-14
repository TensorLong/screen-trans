package com.yiqun.translator.data.local.capture

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointedCaptureCropTest {

    @Test
    fun boundsForPointedTranslationStayUnderOneThirdScreenArea() {
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = 1080,
            screenHeight = 2400,
            pointerX = 540,
            pointerY = 1200,
        )

        assertTrue(width(bounds) * height(bounds) <= 1080 * 2400 / 3)
        assertRect(rectOf(0, 800, 1080, 1600), bounds)
    }

    @Test
    fun boundsKeepRequestedAreaWhenPointerIsNearTopEdge() {
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = 1080,
            screenHeight = 2400,
            pointerX = 540,
            pointerY = 12,
        )

        assertRect(rectOf(0, 0, 1080, 800), bounds)
        assertTrue(540 >= bounds.left && 540 < bounds.right)
        assertTrue(12 >= bounds.top && 12 < bounds.bottom)
    }

    @Test
    fun boundsCoverWholeSmallScreens() {
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = 320,
            screenHeight = 480,
            pointerX = 160,
            pointerY = 240,
        )

        assertRect(rectOf(0, 0, 320, 480), bounds)
    }

    @Test
    fun pointedCropCutsOcrPixelsAndReturnedBitmapMemoryByMoreThanHalf() {
        val screenWidth = 1080
        val screenHeight = 2400
        val fullScreenPixels = screenWidth * screenHeight
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pointerX = 540,
            pointerY = 1200,
        )
        val croppedPixels = width(bounds) * height(bounds)

        val oldReturnedRgb565Bytes = fullScreenPixels * RGB_565_BYTES_PER_PIXEL
        val newReturnedRgb565Bytes = croppedPixels * RGB_565_BYTES_PER_PIXEL

        assertTrue(croppedPixels <= fullScreenPixels / 3)
        assertTrue(newReturnedRgb565Bytes <= oldReturnedRgb565Bytes / 3)
    }

    private fun width(rect: Rect): Int = rect.right - rect.left

    private fun height(rect: Rect): Int = rect.bottom - rect.top

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

    private companion object {
        const val RGB_565_BYTES_PER_PIXEL = 2
    }
}

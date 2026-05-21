package com.yiqun.translator.data.local.capture

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.TextDetectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointedCaptureCropTest {

    @Test
    fun boundsForPointedTranslationUsesBroadLocalBand() {
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = 1080,
            screenHeight = 2400,
            pointerX = 540,
            pointerY = 1200,
        )

        assertEquals(1080, width(bounds))
        assertEquals(1600, height(bounds))
        assertRect(rectOf(0, 400, 1080, 2000), bounds)
    }

    @Test
    fun boundsKeepRequestedAreaWhenPointerIsNearTopEdge() {
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = 1080,
            screenHeight = 2400,
            pointerX = 540,
            pointerY = 12,
        )

        assertRect(rectOf(0, 0, 1080, 1600), bounds)
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
    fun paragraphModePreservesFullScreenContext() {
        val bounds = PointedCaptureCrop.boundsFor(
            screenWidth = 1080,
            screenHeight = 2400,
            pointerX = 540,
            pointerY = 1200,
            textDetectMode = TextDetectMode.PARAGRAPH,
        )

        assertRect(rectOf(0, 0, 1080, 2400), bounds)
    }

    @Test
    fun pointedBoundsReduceOcrPixelsOnTallScreens() {
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

        assertTrue(croppedPixels < fullScreenPixels)
        assertEquals((fullScreenPixels * 2) / 3, croppedPixels)
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

}

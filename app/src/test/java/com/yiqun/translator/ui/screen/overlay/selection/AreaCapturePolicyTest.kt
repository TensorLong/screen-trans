package com.yiqun.translator.ui.screen.overlay.selection

import org.junit.Assert.assertEquals
import org.junit.Test

class AreaCapturePolicyTest {

    @Test
    fun selectedAreaIsUsedAsTheCaptureBoundsAndCoordinateOffset() {
        val captureBounds = AreaCapturePolicy.captureBounds(120, 300, 820, 620)

        assertEquals(120, captureBounds.left)
        assertEquals(300, captureBounds.top)
        assertEquals(820, captureBounds.right)
        assertEquals(620, captureBounds.bottom)
        assertEquals(120, AreaCapturePolicy.coordinateOffsetX(captureBounds))
        assertEquals(300, AreaCapturePolicy.coordinateOffsetY(captureBounds))
    }
}

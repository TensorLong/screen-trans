package com.yiqun.translator.ui.screen.overlay.detectmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectModeMenuPositionPolicyTest {

    // Representative phone-sized window: 1080x2400 screen, 480x660 menu, 24 margin.
    private val screenWidth = 1080
    private val screenHeight = 2400
    private val menuWidth = 480
    private val menuHeight = 660
    private val margin = 24
    private val handleRadius = 40

    private fun resolve(anchorX: Int?, anchorY: Int?) =
        DetectModeMenuPositionPolicy.resolve(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            margin = margin,
            handleRadius = handleRadius,
            anchorCenterX = anchorX,
            anchorCenterY = anchorY,
        )

    @Test
    fun menuIsPlacedAboveAnchorWhenThereIsRoom() {
        val position = resolve(anchorX = 540, anchorY = 1800)
        // Horizontally centered on the anchor.
        assertEquals(540 - menuWidth / 2, position.x)
        // Fully above the handle: anchorY - handleRadius - menuHeight - margin.
        assertEquals(1800 - handleRadius - menuHeight - margin, position.y)
    }

    @Test
    fun menuFlipsBelowAnchorWhenNoRoomAbove() {
        val position = resolve(anchorX = 540, anchorY = 200)
        // Anchor too close to the top edge, so the menu drops below the handle.
        assertEquals(200 + handleRadius + margin, position.y)
    }

    @Test
    fun menuIsClampedInsideRightEdge() {
        val position = resolve(anchorX = 1060, anchorY = 1800)
        val maxX = screenWidth - menuWidth - margin
        assertEquals(maxX, position.x)
        assertTrue(position.x + menuWidth <= screenWidth)
    }

    @Test
    fun menuIsClampedInsideLeftEdge() {
        val position = resolve(anchorX = 10, anchorY = 1800)
        assertEquals(margin, position.x)
    }

    @Test
    fun menuIsCenteredWhenNoAnchorProvided() {
        val position = resolve(anchorX = null, anchorY = null)
        assertEquals((screenWidth - menuWidth) / 2, position.x)
        assertEquals((screenHeight - menuHeight) / 2, position.y)
    }

    @Test
    fun menuNeverExceedsBoundsOnScreenSmallerThanMenu() {
        val position = DetectModeMenuPositionPolicy.resolve(
            screenWidth = 200,
            screenHeight = 300,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            margin = margin,
            handleRadius = handleRadius,
            anchorCenterX = 100,
            anchorCenterY = 150,
        )
        // Range collapses to the margin; the policy must still return a valid position.
        assertEquals(margin, position.x)
        assertEquals(margin, position.y)
    }
}

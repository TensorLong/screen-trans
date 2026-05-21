package com.yiqun.translator.ui.screen.overlay.detectmode

/**
 * Pure positioning math for the detect-mode overlay menu.
 *
 * Keeps the menu fully on screen regardless of where the pointer handle was
 * double-clicked: the menu is placed above the anchor when there is room,
 * flips below when the anchor sits too close to the top edge, and is always
 * clamped within the screen margins on both axes so it can never be clipped.
 *
 * All values are in pixels. Extracted from the view so the boundary behaviour
 * can be unit tested without an Android runtime.
 */
object DetectModeMenuPositionPolicy {

    /** Top-left placement of the menu window. */
    data class Position(val x: Int, val y: Int)

    /**
     * Resolves the menu's top-left position.
     *
     * @param anchorCenterX/[anchorCenterY] center of the pointer handle that opened the
     *   menu, or `null` to fall back to a centered placement.
     * @param handleRadius half the pointer handle size, used as a gap so the menu does
     *   not overlap the handle it springs from.
     */
    fun resolve(
        screenWidth: Int,
        screenHeight: Int,
        menuWidth: Int,
        menuHeight: Int,
        margin: Int,
        handleRadius: Int,
        anchorCenterX: Int?,
        anchorCenterY: Int?,
    ): Position {
        // coerceAtLeast(margin) keeps the range valid even on screens smaller than the menu.
        val maxX = (screenWidth - menuWidth - margin).coerceAtLeast(margin)
        val maxY = (screenHeight - menuHeight - margin).coerceAtLeast(margin)

        val rawX = if (anchorCenterX != null) {
            anchorCenterX - menuWidth / 2
        } else {
            (screenWidth - menuWidth) / 2
        }

        val rawY = if (anchorCenterY != null) {
            val aboveY = anchorCenterY - handleRadius - menuHeight - margin
            val belowY = anchorCenterY + handleRadius + margin
            when {
                // Preferred: fully above the handle.
                aboveY >= margin -> aboveY
                // No room above: flip below the handle if it fits on screen.
                belowY <= maxY -> belowY
                // Neither side fits cleanly: keep above and let the clamp below handle it.
                else -> aboveY
            }
        } else {
            (screenHeight - menuHeight) / 2
        }

        return Position(
            x = rawX.coerceIn(margin, maxX),
            y = rawY.coerceIn(margin, maxY),
        )
    }
}

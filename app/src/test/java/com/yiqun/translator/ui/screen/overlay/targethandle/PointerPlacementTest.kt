package com.yiqun.translator.ui.screen.overlay.targethandle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerPlacementTest {

    @Test
    fun defaultOffsetKeepsExistingPointerHitPoint() {
        val hitPoint = PointerCoordinateMapper.toOcrPoint(
            visualPointerX = 120,
            visualPointerY = 240,
            offset = PointerOffset.DEFAULT,
        )

        assertEquals(120 to 240, hitPoint)
    }

    @Test
    fun coordinateMapperAppliesIndependentSideOffsets() {
        val leftHitPoint = PointerCoordinateMapper.toOcrPoint(
            visualPointerX = 300,
            visualPointerY = 500,
            offset = PointerOffset(x = -24, y = 36),
        )
        val rightHitPoint = PointerCoordinateMapper.toOcrPoint(
            visualPointerX = 300,
            visualPointerY = 500,
            offset = PointerOffset(x = 42, y = 18),
        )

        assertEquals(276 to 536, leftHitPoint)
        assertEquals(342 to 518, rightHitPoint)
    }

    @Test
    fun pointerOffsetRoundTripsThroughPreferenceValue() {
        val offset = PointerOffset(x = -18, y = 27)

        assertEquals(offset, PointerOffset.decode(offset.encode()))
    }

    @Test
    fun pointerDisplayPolicyDefaultsToDualPointerOnlyOnLargeDevices() {
        assertFalse(
            PointerDisplayPolicy.defaultDualPointerEnabled(
                DeviceFormFactorResolver.resolve(widthDp = 393, heightDp = 873, smallestWidthDp = 393)
            )
        )
        assertTrue(
            PointerDisplayPolicy.defaultDualPointerEnabled(
                DeviceFormFactorResolver.resolve(widthDp = 820, heightDp = 1180, smallestWidthDp = 820)
            )
        )
        assertTrue(
            PointerDisplayPolicy.defaultDualPointerEnabled(
                DeviceFormFactorResolver.resolve(widthDp = 674, heightDp = 842, smallestWidthDp = 500)
            )
        )
    }

    @Test
    fun calibrationSessionUsesTemporaryOffsetUntilConfirmed() {
        val session = PointerCalibrationSession(
            savedLeft = PointerOffset(x = 0, y = 0),
            savedRight = PointerOffset(x = 8, y = 12),
        )

        session.drag(PointerSide.LEFT, dx = -30, dy = 20)
        session.reset(PointerSide.RIGHT)

        assertEquals(PointerOffset(x = 30, y = -20), session.current(PointerSide.LEFT))
        assertEquals(PointerOffset.DEFAULT, session.current(PointerSide.RIGHT))
        assertEquals(PointerOffset(x = 0, y = 0), session.saved(PointerSide.LEFT))
        assertEquals(PointerOffset(x = 8, y = 12), session.saved(PointerSide.RIGHT))

        val confirmed = session.confirm()
        assertEquals(PointerOffset(x = 30, y = -20), confirmed.left)
        assertEquals(PointerOffset.DEFAULT, confirmed.right)
    }
}

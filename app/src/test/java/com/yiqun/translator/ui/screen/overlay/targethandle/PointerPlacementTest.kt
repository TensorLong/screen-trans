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
    fun pointerOffsetRepresentsPointerCenterToTargetBoxCenter() {
        val offset = PointerOffset.fromTargetAndPointerCenters(
            targetCenterX = 100,
            targetCenterY = 80,
            pointerCenterX = 70,
            pointerCenterY = 110,
        )

        assertEquals(PointerOffset(x = 30, y = -30), offset)
        assertEquals(
            100 to 80,
            PointerCoordinateMapper.toOcrPoint(
                visualPointerX = 70,
                visualPointerY = 110,
                offset = offset,
            )
        )
    }

    @Test
    fun systemDefaultOffsetMatchesCurrentHandleAndTargetIconLayout() {
        val offset = PointerOffset.defaultTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            pointerThumbSpace = 18,
        )

        assertEquals(PointerOffset(x = 0, y = -65), offset)
        assertEquals(
            100 to 135,
            PointerCoordinateMapper.toOcrPoint(
                visualPointerX = 100,
                visualPointerY = 200,
                offset = offset,
            )
        )
    }

    @Test
    fun pointerOffsetRoundTripsThroughPreferenceValue() {
        val offset = PointerOffset(x = -18, y = 27)

        assertEquals(offset, PointerOffset.decodeTargetFromHandle(offset.encode(), PointerOffset(x = 0, y = -65)))
    }

    @Test
    fun legacyStoredDefaultOffsetMigratesToVisibleTargetIconPosition() {
        val defaultOffset = PointerOffset(x = 0, y = -65)
        val migratedOffset = PointerOffset.decodeTargetFromHandle("0,0", defaultOffset)

        assertEquals(defaultOffset, migratedOffset)
        assertEquals(
            0 to 0,
            PointerIconPlacement.targetIconOffset(
                edgeCorrectionX = 0,
                edgeCorrectionY = 0,
                targetFromHandleOffset = migratedOffset,
                defaultTargetFromHandleOffset = defaultOffset,
            )
        )
    }

    @Test
    fun versionedTargetFromHandleOffsetIsNotMigratedAgain() {
        val defaultOffset = PointerOffset(x = 0, y = -65)
        val savedOffset = PointerOffset(x = 6, y = -72)

        assertEquals(savedOffset, PointerOffset.decodeTargetFromHandle(savedOffset.encode(), defaultOffset))
    }

    @Test
    fun unversionedTargetFromHandleOffsetThatKeepsIconVisibleIsNotMigratedAgain() {
        val defaultOffset = PointerOffset(x = 0, y = -65)

        assertEquals(defaultOffset, PointerOffset.decodeTargetFromHandle("0,-65", defaultOffset))
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

    @Test
    fun calibrationResetRestoresSystemDefaultOffset() {
        val defaultOffset = PointerOffset(x = 0, y = -65)
        val session = PointerCalibrationSession(
            savedLeft = PointerOffset(x = 12, y = -40),
            savedRight = PointerOffset(x = 8, y = 12),
            defaultOffset = defaultOffset,
        )

        session.reset(PointerSide.LEFT)

        assertEquals(defaultOffset, session.current(PointerSide.LEFT))
    }
}

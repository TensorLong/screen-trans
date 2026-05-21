package com.yiqun.translator.ui.screen.overlay.targethandle

import android.view.MotionEvent
import com.yiqun.translator.data.local.vision.TextDetectMode
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
    fun fixedOverlayReproducesCustomizedTargetIconClipping() {
        val defaultOffset = PointerOffset(x = 0, y = -65)
        val customizedOffset = PointerOffset(x = 0, y = -95)
        val (_, targetIconOffsetY) = PointerIconPlacement.targetIconOffset(
            edgeCorrectionX = 0,
            edgeCorrectionY = 0,
            targetFromHandleOffset = customizedOffset,
            defaultTargetFromHandleOffset = defaultOffset,
        )

        assertTrue(targetIconOffsetY < 0)
    }

    @Test
    fun overlayLayoutKeepsTargetIconVisibleAfterCustomizedDistance() {
        val layout = PointerOverlayLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -95),
        )

        assertEquals(70, layout.width)
        assertEquals(142, layout.height)
        assertEquals(23 to 0, layout.targetIconTopLeft)
        assertEquals(35 to 107, layout.handleCenter)
    }

    @Test
    fun overlayLayoutKeepsHorizontallyCustomizedTargetIconVisible() {
        val layout = PointerOverlayLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 60, y = -65),
        )

        assertEquals(107, layout.width)
        assertEquals(112, layout.height)
        assertEquals(83 to 0, layout.targetIconTopLeft)
        assertEquals(35 to 77, layout.handleCenter)
    }

    @Test
    fun handleHitTestAcceptsPointsInsideRoundHandle() {
        val layout = PointerOverlayLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -95),
        )

        assertTrue(
            PointerOverlayHitTest.isHandleHit(
                layout = layout,
                touchX = layout.handleCenterX.toFloat(),
                touchY = layout.handleCenterY.toFloat(),
            )
        )
    }

    @Test
    fun handleHitTestRejectsTransparentSpaceBetweenTargetIconAndHandle() {
        val layout = PointerOverlayLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -95),
        )
        val gapCenterX = layout.handleCenterX.toFloat()
        val gapCenterY = (layout.targetIconTopLeftY + 24 + layout.handleCenterY - layout.handleWidth / 2) / 2f

        assertFalse(
            PointerOverlayHitTest.isHandleHit(
                layout = layout,
                touchX = gapCenterX,
                touchY = gapCenterY,
            )
        )
    }

    @Test
    fun handleHitTestRejectsTargetIconCenter() {
        val layout = PointerOverlayLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 60, y = -65),
        )

        assertFalse(
            PointerOverlayHitTest.isHandleHit(
                layout = layout,
                touchX = layout.targetIconTopLeftX + 12f,
                touchY = layout.targetIconTopLeftY + 12f,
            )
        )
    }

    @Test
    fun passThroughWindowsKeepTargetIconOutsideTouchableHandleWindow() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -95),
        )

        assertEquals(70, layout.touchableWidth)
        assertEquals(70, layout.touchableHeight)
        assertEquals(35 to 35, layout.handleCenter)
        assertEquals(23 to -72, layout.targetIconTopLeftFromHandle)
        assertEquals(35 to -60, layout.targetIconCenterFromHandle)
        assertEquals(0, layout.visualLeft())
        assertEquals(-72, layout.visualTop())
        assertEquals(70, layout.visualWidth())
        assertEquals(142, layout.visualHeight())
        assertFalse(layout.touchableWindowContains(35, -24))
        assertFalse(layout.touchableWindowContains(35, -60))
    }

    @Test
    fun passThroughWindowsDoNotExpandTouchableBoundsForHorizontalTargetOffsets() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 60, y = -65),
        )

        assertEquals(70, layout.touchableWidth)
        assertEquals(70, layout.touchableHeight)
        assertEquals(83 to -42, layout.targetIconTopLeftFromHandle)
        assertEquals(95 to -30, layout.targetIconCenterFromHandle)
        assertEquals(107, layout.visualWidth())
        assertEquals(112, layout.visualHeight())
        assertFalse(layout.touchableWindowContains(95, -30))
    }

    @Test
    fun dragBoundsClampKeepsPassThroughVisualAreaOnScreen() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -65),
        )

        val clamped = PointerWindowBounds.clampLayoutPosition(
            x = -932,
            y = -827,
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
        )

        assertEquals(0 to 42, clamped)
    }

    @Test
    fun dragBoundsClampRechecksVisualAreaAfterEdgeCorrection() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = 300),
        )

        val edgeCorrection = PointerDragEdgeCorrection.calculate(
            x = 200,
            y = 1940,
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            isRtl = false,
        )
        val clamped = PointerWindowBounds.clampLayoutPosition(
            x = 200,
            y = 1940,
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            edgeCorrectionX = edgeCorrection.x,
            edgeCorrectionY = edgeCorrection.y,
        )

        assertEquals(0, edgeCorrection.x)
        assertEquals(117, edgeCorrection.y)
        assertEquals(200 to 1936, clamped)
        assertEquals(2376, clamped.second + layout.targetIconTopLeftY(edgeCorrection.y))
        assertEquals(2400, clamped.second + layout.visualBottom(edgeCorrection.y))
    }

    @Test
    fun defaultSinglePointerPlacementUsesHorizontalCenterAndOneThirdFromBottom() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -65),
        )

        val placement = PointerDefaultPlacement.layoutTopLeft(
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            side = PointerSide.LEFT,
            dualPointerMode = false,
        )

        assertEquals(505 to 1565, placement)
        assertEquals(540 to 1600, (layout.handleCenter.first + placement.first) to (layout.handleCenter.second + placement.second))
        assertEquals(540 to 1535, (placement.first + layout.targetIconCenterFromHandle.first) to (placement.second + layout.targetIconCenterFromHandle.second))
    }

    @Test
    fun defaultPlacementIgnoresStaleDragEdgeCorrection() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = 300),
        )
        val staleCorrection = PointerDragEdgeCorrection.calculate(
            x = 200,
            y = 1940,
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            isRtl = false,
        )

        val correctionForDefault = PointerDefaultPlacement.edgeCorrection()
        val placement = PointerDefaultPlacement.layoutTopLeft(
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            side = PointerSide.LEFT,
            dualPointerMode = false,
        )

        assertEquals(PointerEdgeCorrection(x = 0, y = 117), staleCorrection)
        assertEquals(PointerEdgeCorrection(x = 0, y = 0), correctionForDefault)
        assertEquals(540 to 1600, (layout.handleCenter.first + placement.first) to (layout.handleCenter.second + placement.second))
        assertEquals(1888, placement.second + layout.targetIconTopLeftY(correctionForDefault.y))
        assertEquals(2005, placement.second + layout.targetIconTopLeftY(staleCorrection.y))
    }

    @Test
    fun defaultDualPointerPlacementUsesQuarterWidthsAndOneThirdFromBottom() {
        val layout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = 24,
            handleWidth = 70,
            targetFromHandleOffset = PointerOffset(x = 0, y = -65),
        )

        val left = PointerDefaultPlacement.layoutTopLeft(
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            side = PointerSide.LEFT,
            dualPointerMode = true,
        )
        val right = PointerDefaultPlacement.layoutTopLeft(
            screenWidth = 1080,
            screenHeight = 2400,
            layout = layout,
            side = PointerSide.RIGHT,
            dualPointerMode = true,
        )

        assertEquals(235 to 1565, left)
        assertEquals(775 to 1565, right)
        assertEquals(270, left.first + layout.handleCenter.first)
        assertEquals(810, right.first + layout.handleCenter.first)
        assertEquals(1600, left.second + layout.handleCenter.second)
        assertEquals(1600, right.second + layout.handleCenter.second)
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
    fun screenInfoRefreshIsRequiredBeforeDefaultPlacementWhenScreenSizeIsUnknown() {
        assertTrue(PointerScreenInfoRefreshPolicy.requiresRefresh(screenWidth = 0, screenHeight = 2400))
        assertTrue(PointerScreenInfoRefreshPolicy.requiresRefresh(screenWidth = 1080, screenHeight = 0))
        assertFalse(PointerScreenInfoRefreshPolicy.requiresRefresh(screenWidth = 1080, screenHeight = 2400))
    }

    @Test
    fun initialActionUpDoesNotScheduleDocking() {
        val tracker = PointerDockingReleaseTracker()

        assertFalse(
            tracker.shouldScheduleDockAfterRelease(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_UP,
                translationActive = false,
                menuOperating = false,
            )
        )
    }

    @Test
    fun tapWithoutMoveDoesNotScheduleDocking() {
        val tracker = PointerDockingReleaseTracker()

        tracker.shouldScheduleDockAfterRelease(
            side = PointerSide.LEFT,
            activeSide = PointerSide.LEFT,
            motionEventAction = MotionEvent.ACTION_DOWN,
            translationActive = false,
            menuOperating = false,
        )

        assertFalse(
            tracker.shouldScheduleDockAfterRelease(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_UP,
                translationActive = false,
                menuOperating = false,
            )
        )
    }

    @Test
    fun draggedReleaseSchedulesDockingOnlyWhenNoTranslationIsShown() {
        val tracker = PointerDockingReleaseTracker()

        assertFalse(
            tracker.shouldScheduleDockAfterRelease(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_MOVE,
                translationActive = false,
                menuOperating = false,
            )
        )
        assertTrue(
            tracker.shouldScheduleDockAfterRelease(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_UP,
                translationActive = false,
                menuOperating = false,
            )
        )

        tracker.shouldScheduleDockAfterRelease(
            side = PointerSide.LEFT,
            activeSide = PointerSide.LEFT,
            motionEventAction = MotionEvent.ACTION_MOVE,
            translationActive = false,
            menuOperating = false,
        )
        assertFalse(
            tracker.shouldScheduleDockAfterRelease(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_UP,
                translationActive = true,
                menuOperating = false,
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

    @Test
    fun pointerInteractionKeepsOnlyActiveSideVisibleUntilReleased() {
        assertTrue(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_DOWN,
            )
        )
        assertFalse(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.RIGHT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_DOWN,
            )
        )
        assertTrue(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.LEFT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_MOVE,
            )
        )
        assertFalse(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.RIGHT,
                activeSide = PointerSide.LEFT,
                motionEventAction = MotionEvent.ACTION_MOVE,
            )
        )
    }

    @Test
    fun pointerInteractionRestoresAllSidesAfterInteractionEnds() {
        assertTrue(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.LEFT,
                activeSide = PointerSide.RIGHT,
                motionEventAction = MotionEvent.ACTION_UP,
            )
        )
        assertTrue(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.RIGHT,
                activeSide = PointerSide.RIGHT,
                motionEventAction = MotionEvent.ACTION_UP,
            )
        )
        assertTrue(
            PointerInteractionVisibilityPolicy.visibleForSide(
                side = PointerSide.LEFT,
                activeSide = PointerSide.RIGHT,
                motionEventAction = MotionEvent.ACTION_CANCEL,
            )
        )
    }

    @Test
    fun passThroughOverlayAlphaStaysInsideAndroidTouchableSafetyLimit() {
        assertEquals(0.8f, OverlayWindowAlpha.passThroughVisibleAlpha, 0.0f)
        assertEquals(0.8f, OverlayWindowAlpha.forPassThroughVisibility(true), 0.0f)
        assertEquals(0.0f, OverlayWindowAlpha.forPassThroughVisibility(false), 0.0f)
    }

    @Test
    fun visibilityStateIgnoresRepeatedWindowVisibilityUpdates() {
        val state = StableWindowVisibilityState()

        assertTrue(state.markIfChanged(true))
        assertFalse(state.markIfChanged(true))
        assertTrue(state.markIfChanged(false))
        assertFalse(state.markIfChanged(false))
        assertTrue(state.markIfChanged(true))
    }

    @Test
    fun targetIconStateKeepsProgressVisibleAfterPointerRelease() {
        val state = TargetIconRenderPolicy.stateFor(
            side = PointerSide.LEFT,
            activeSide = PointerSide.LEFT,
            motionEventAction = MotionEvent.ACTION_UP,
            textDetectMode = TextDetectMode.SENTENCE,
            captureStatus = CaptureStatus.Idle,
            fixedAreaTranslating = false,
            translateStatus = TranslateStatus.Requested,
            areaSelecting = false,
            writingRtl = false,
        )

        assertFalse(state.pointerVisible)
        assertTrue(state.progressVisible)
        assertEquals(TargetIconTint.NONE, state.tint)
    }

    @Test
    fun targetIconStatePreservesSelectionTintAndRtl() {
        val state = TargetIconRenderPolicy.stateFor(
            side = PointerSide.LEFT,
            activeSide = PointerSide.LEFT,
            motionEventAction = MotionEvent.ACTION_MOVE,
            textDetectMode = TextDetectMode.SELECT,
            captureStatus = CaptureStatus.Idle,
            fixedAreaTranslating = false,
            translateStatus = TranslateStatus.Idle,
            areaSelecting = true,
            writingRtl = true,
        )

        assertTrue(state.pointerVisible)
        assertFalse(state.progressVisible)
        assertTrue(state.writingRtl)
        assertEquals(TargetIconTint.SELECT, state.tint)
    }

    @Test
    fun targetIconContentDimsDuringCaptureWithoutChangingWindowVisibility() {
        val state = TargetIconRenderPolicy.stateFor(
            side = PointerSide.LEFT,
            activeSide = PointerSide.RIGHT,
            motionEventAction = MotionEvent.ACTION_MOVE,
            textDetectMode = TextDetectMode.SENTENCE,
            captureStatus = CaptureStatus.Requested,
            fixedAreaTranslating = false,
            translateStatus = TranslateStatus.Idle,
            areaSelecting = false,
            writingRtl = false,
        )

        assertFalse(state.pointerVisible)
        assertTrue(state.dimmed)
        assertTrue(state.captureRequested)
        assertEquals(0.01f, TargetIconRenderPolicy.contentAlpha(state), 0.0f)
    }

    @Test
    fun activeDraggedPointerIconDimsDuringCapture() {
        val state = TargetIconRenderPolicy.stateFor(
            side = PointerSide.LEFT,
            activeSide = PointerSide.LEFT,
            motionEventAction = MotionEvent.ACTION_MOVE,
            textDetectMode = TextDetectMode.SENTENCE,
            captureStatus = CaptureStatus.Requested,
            fixedAreaTranslating = false,
            translateStatus = TranslateStatus.Idle,
            areaSelecting = false,
            writingRtl = false,
        )

        assertTrue(state.pointerVisible)
        assertTrue(state.dimmed)
        assertTrue(state.captureRequested)
        assertEquals(0.01f, TargetIconRenderPolicy.contentAlpha(state), 0.0f)
    }

    @Test
    fun activeDraggedPointerIconStaysOpaqueWhenOnlyFixedAreaIsTranslating() {
        val state = TargetIconRenderPolicy.stateFor(
            side = PointerSide.LEFT,
            activeSide = PointerSide.LEFT,
            motionEventAction = MotionEvent.ACTION_MOVE,
            textDetectMode = TextDetectMode.SENTENCE,
            captureStatus = CaptureStatus.Idle,
            fixedAreaTranslating = true,
            translateStatus = TranslateStatus.Idle,
            areaSelecting = false,
            writingRtl = false,
        )

        assertTrue(state.pointerVisible)
        assertTrue(state.dimmed)
        assertFalse(state.captureRequested)
        assertEquals(1.0f, TargetIconRenderPolicy.contentAlpha(state), 0.0f)
    }
}

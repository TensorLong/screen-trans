package com.yiqun.translator.ui.screen.overlay.targethandle

import android.content.res.Configuration
import android.graphics.Point
import android.view.MotionEvent
import com.yiqun.translator.data.local.vision.TextDetectMode

enum class PointerSide {
    LEFT,
    RIGHT,
}

data class PointerOffset(
    val x: Int,
    val y: Int,
) {
    fun encode(): String = "${Companion.TARGET_FROM_HANDLE_PREFIX}$x,$y"

    companion object {
        val DEFAULT = PointerOffset(0, 0)
        private const val TARGET_FROM_HANDLE_PREFIX = "target-from-handle:"

        fun fromTargetAndPointerCenters(
            targetCenterX: Int,
            targetCenterY: Int,
            pointerCenterX: Int,
            pointerCenterY: Int,
        ): PointerOffset {
            return PointerOffset(
                x = targetCenterX - pointerCenterX,
                y = targetCenterY - pointerCenterY,
            )
        }

        fun defaultTargetFromHandleOffset(
            pointerDimen: Int,
            handleWidth: Int,
            pointerThumbSpace: Int,
        ): PointerOffset {
            return PointerOffset(
                x = 0,
                y = -(pointerDimen / 2 + pointerThumbSpace + handleWidth / 2),
            )
        }

        fun decode(
            value: String?,
            defaultValue: PointerOffset = DEFAULT,
        ): PointerOffset {
            if (value.isNullOrBlank()) return defaultValue
            val rawValue = value.removePrefix(TARGET_FROM_HANDLE_PREFIX)
            val parts = rawValue.split(",")
            if (parts.size != 2) return defaultValue
            val x = parts[0].trim().toIntOrNull() ?: return defaultValue
            val y = parts[1].trim().toIntOrNull() ?: return defaultValue
            return PointerOffset(x, y)
        }

        fun decodeTargetFromHandle(
            value: String?,
            defaultValue: PointerOffset,
        ): PointerOffset {
            if (value.isNullOrBlank()) return defaultValue
            if (value.startsWith(TARGET_FROM_HANDLE_PREFIX)) {
                return decode(value, defaultValue)
            }

            val legacyOffset = decode(value, DEFAULT)
            // v2.7.2 saved unversioned target-from-handle values; migrate only legacy values that would hide the icon behind the handle.
            if (legacyOffset.y <= defaultValue.y) {
                return legacyOffset
            }
            return PointerOffset(
                x = defaultValue.x + legacyOffset.x,
                y = defaultValue.y + legacyOffset.y,
            )
        }
    }
}

data class PointerOffsetPair(
    val left: PointerOffset,
    val right: PointerOffset,
) {
    companion object {
        val DEFAULT = PointerOffsetPair(PointerOffset.DEFAULT, PointerOffset.DEFAULT)

        fun default(defaultOffset: PointerOffset): PointerOffsetPair {
            return PointerOffsetPair(defaultOffset, defaultOffset)
        }
    }
}

object PointerCoordinateMapper {
    fun toOcrPoint(
        visualPointerX: Int,
        visualPointerY: Int,
        offset: PointerOffset,
    ): Pair<Int, Int> = visualPointerX + offset.x to visualPointerY + offset.y

    fun toOcrPoint(
        visualPointerPoint: Point,
        offset: PointerOffset,
    ): Point {
        val (x, y) = toOcrPoint(visualPointerPoint.x, visualPointerPoint.y, offset)
        return Point(x, y)
    }
}

object PointerIconPlacement {
    fun targetIconOffset(
        edgeCorrectionX: Int,
        edgeCorrectionY: Int,
        targetFromHandleOffset: PointerOffset,
        defaultTargetFromHandleOffset: PointerOffset,
    ): Pair<Int, Int> {
        return edgeCorrectionX + targetFromHandleOffset.x - defaultTargetFromHandleOffset.x to
                edgeCorrectionY + targetFromHandleOffset.y - defaultTargetFromHandleOffset.y
    }
}

data class PointerOverlayLayout(
    val width: Int,
    val height: Int,
    val handleCenterX: Int,
    val handleCenterY: Int,
    val targetIconTopLeftX: Int,
    val targetIconTopLeftY: Int,
    val handleWidth: Int,
) {
    val handleCenter: Pair<Int, Int>
        get() = handleCenterX to handleCenterY

    val handleTopLeft: Pair<Int, Int>
        get() = handleCenterX - handleWidth / 2 to handleCenterY - handleWidth / 2

    val targetIconTopLeft: Pair<Int, Int>
        get() = targetIconTopLeftX to targetIconTopLeftY

    companion object {
        fun fromTargetFromHandleOffset(
            pointerDimen: Int,
            handleWidth: Int,
            targetFromHandleOffset: PointerOffset,
        ): PointerOverlayLayout {
            val pointerHalf = pointerDimen / 2
            val handleHalf = handleWidth / 2
            val left = minOf(-handleHalf, targetFromHandleOffset.x - pointerHalf)
            val top = minOf(-handleHalf, targetFromHandleOffset.y - pointerHalf)
            val right = maxOf(handleWidth - handleHalf, targetFromHandleOffset.x - pointerHalf + pointerDimen)
            val bottom = maxOf(handleWidth - handleHalf, targetFromHandleOffset.y - pointerHalf + pointerDimen)

            return PointerOverlayLayout(
                width = right - left,
                height = bottom - top,
                handleCenterX = -left,
                handleCenterY = -top,
                targetIconTopLeftX = targetFromHandleOffset.x - pointerHalf - left,
                targetIconTopLeftY = targetFromHandleOffset.y - pointerHalf - top,
                handleWidth = handleWidth,
            )
        }
    }
}

data class PointerPassThroughWindowLayout(
    val touchableWidth: Int,
    val touchableHeight: Int,
    val targetIconWindowWidth: Int,
    val targetIconWindowHeight: Int,
    val handleCenterX: Int,
    val handleCenterY: Int,
    val targetIconTopLeftXFromHandle: Int,
    val targetIconTopLeftYFromHandle: Int,
) {
    val handleCenter: Pair<Int, Int>
        get() = handleCenterX to handleCenterY

    val targetIconTopLeftFromHandle: Pair<Int, Int>
        get() = targetIconTopLeftXFromHandle to targetIconTopLeftYFromHandle

    val targetIconCenterFromHandle: Pair<Int, Int>
        get() = targetIconTopLeftXFromHandle + targetIconWindowWidth / 2 to
                targetIconTopLeftYFromHandle + targetIconWindowHeight / 2

    fun targetIconTopLeftX(edgeCorrectionX: Int): Int = targetIconTopLeftXFromHandle + edgeCorrectionX

    fun targetIconTopLeftY(edgeCorrectionY: Int): Int = targetIconTopLeftYFromHandle + edgeCorrectionY

    fun visualLeft(edgeCorrectionX: Int = 0): Int = minOf(0, targetIconTopLeftX(edgeCorrectionX))

    fun visualTop(edgeCorrectionY: Int = 0): Int = minOf(0, targetIconTopLeftY(edgeCorrectionY))

    fun visualRight(edgeCorrectionX: Int = 0): Int =
        maxOf(touchableWidth, targetIconTopLeftX(edgeCorrectionX) + targetIconWindowWidth)

    fun visualBottom(edgeCorrectionY: Int = 0): Int =
        maxOf(touchableHeight, targetIconTopLeftY(edgeCorrectionY) + targetIconWindowHeight)

    fun visualWidth(edgeCorrectionX: Int = 0): Int = visualRight(edgeCorrectionX) - visualLeft(edgeCorrectionX)

    fun visualHeight(edgeCorrectionY: Int = 0): Int = visualBottom(edgeCorrectionY) - visualTop(edgeCorrectionY)

    fun touchableWindowContains(x: Int, y: Int): Boolean {
        return x in 0 until touchableWidth && y in 0 until touchableHeight
    }

    companion object {
        fun fromTargetFromHandleOffset(
            pointerDimen: Int,
            handleWidth: Int,
            targetFromHandleOffset: PointerOffset,
        ): PointerPassThroughWindowLayout {
            val handleCenter = handleWidth / 2
            val pointerHalf = pointerDimen / 2
            return PointerPassThroughWindowLayout(
                touchableWidth = handleWidth,
                touchableHeight = handleWidth,
                targetIconWindowWidth = pointerDimen,
                targetIconWindowHeight = pointerDimen,
                handleCenterX = handleCenter,
                handleCenterY = handleCenter,
                targetIconTopLeftXFromHandle = handleCenter + targetFromHandleOffset.x - pointerHalf,
                targetIconTopLeftYFromHandle = handleCenter + targetFromHandleOffset.y - pointerHalf,
            )
        }
    }
}

object PointerWindowBounds {
    fun clampLayoutPosition(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        layout: PointerPassThroughWindowLayout,
        edgeCorrectionX: Int = 0,
        edgeCorrectionY: Int = 0,
    ): Pair<Int, Int> {
        val minX = -layout.visualLeft(edgeCorrectionX)
        val maxX = screenWidth - layout.visualRight(edgeCorrectionX)
        val minY = -layout.visualTop(edgeCorrectionY)
        val maxY = screenHeight - layout.visualBottom(edgeCorrectionY)
        return x.coerceIn(minX, maxX.coerceAtLeast(minX)) to
                y.coerceIn(minY, maxY.coerceAtLeast(minY))
    }
}

data class PointerEdgeCorrection(
    val x: Int,
    val y: Int,
)

object PointerDragEdgeCorrection {
    fun calculate(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        layout: PointerPassThroughWindowLayout,
        handleWidth: Int = layout.touchableWidth,
        isRtl: Boolean,
    ): PointerEdgeCorrection {
        val centerX = x + layout.handleCenterX
        val horizontalAdjustmentThreshold = handleWidth * 6 / 10
        val screenStartAdjustmentPosition = horizontalAdjustmentThreshold
        val screenEndAdjustmentPosition = screenWidth - horizontalAdjustmentThreshold
        val horizontalCorrection = when {
            centerX < screenStartAdjustmentPosition -> screenStartAdjustmentPosition - centerX
            centerX > screenEndAdjustmentPosition -> screenEndAdjustmentPosition - centerX
            else -> 0
        } * if (isRtl) 1 else -1

        val visualBottom = y + layout.visualBottom()
        val visualHeight = layout.visualHeight()
        val screenBottomStart = screenHeight - visualHeight
        val verticalCorrection = if (visualBottom > screenBottomStart) {
            (visualBottom - screenBottomStart) / 2
        } else {
            0
        }

        return PointerEdgeCorrection(horizontalCorrection, verticalCorrection)
    }
}

object PointerDefaultPlacement {
    fun edgeCorrection(): PointerEdgeCorrection = PointerEdgeCorrection(0, 0)

    fun layoutTopLeft(
        screenWidth: Int,
        screenHeight: Int,
        layout: PointerPassThroughWindowLayout,
        side: PointerSide,
        dualPointerMode: Boolean,
    ): Pair<Int, Int> {
        val handleCenterX = if (!dualPointerMode) {
            screenWidth / 2
        } else {
            when (side) {
                PointerSide.LEFT -> screenWidth / 4
                PointerSide.RIGHT -> screenWidth * 3 / 4
            }
        }
        val handleCenterY = screenHeight * 2 / 3
        return PointerWindowBounds.clampLayoutPosition(
            x = handleCenterX - layout.handleCenterX,
            y = handleCenterY - layout.handleCenterY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            layout = layout,
        )
    }
}

object PointerScreenInfoRefreshPolicy {
    fun requiresRefresh(screenWidth: Int, screenHeight: Int): Boolean {
        return screenWidth <= 0 || screenHeight <= 0
    }
}

object PointerOverlayHitTest {
    fun isHandleHit(
        layout: PointerOverlayLayout,
        touchX: Float,
        touchY: Float,
    ): Boolean = isHandleHit(
        handleCenterX = layout.handleCenterX,
        handleCenterY = layout.handleCenterY,
        handleWidth = layout.handleWidth,
        touchX = touchX,
        touchY = touchY,
    )

    fun isHandleHit(
        handleCenterX: Int,
        handleCenterY: Int,
        handleWidth: Int,
        touchX: Float,
        touchY: Float,
    ): Boolean {
        val radius = handleWidth / 2f
        val dx = touchX - handleCenterX
        val dy = touchY - handleCenterY
        return dx * dx + dy * dy <= radius * radius
    }
}

enum class DeviceFormFactor {
    PHONE,
    FOLDABLE,
    TABLET,
}

object DeviceFormFactorResolver {
    fun resolve(configuration: Configuration): DeviceFormFactor {
        return resolve(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            smallestWidthDp = configuration.smallestScreenWidthDp,
        )
    }

    fun resolve(
        widthDp: Int,
        heightDp: Int,
        smallestWidthDp: Int,
    ): DeviceFormFactor {
        val minDp = minOf(widthDp, heightDp)
        val maxDp = maxOf(widthDp, heightDp)
        if (smallestWidthDp >= 600 || minDp >= 600) return DeviceFormFactor.TABLET
        if (smallestWidthDp >= 500 || (minDp >= 500 && maxDp.toFloat() / minDp.toFloat() >= 1.25f)) {
            return DeviceFormFactor.FOLDABLE
        }
        return DeviceFormFactor.PHONE
    }
}

object PointerDisplayPolicy {
    fun defaultDualPointerEnabled(formFactor: DeviceFormFactor): Boolean {
        return formFactor == DeviceFormFactor.FOLDABLE || formFactor == DeviceFormFactor.TABLET
    }
}

object PointerInteractionVisibilityPolicy {
    fun isInteractionActive(motionEventAction: Int): Boolean {
        return motionEventAction == MotionEvent.ACTION_DOWN || motionEventAction == MotionEvent.ACTION_MOVE
    }

    fun visibleForSide(
        side: PointerSide,
        activeSide: PointerSide,
        motionEventAction: Int,
    ): Boolean {
        return !isInteractionActive(motionEventAction) || side == activeSide
    }
}

object RecognitionDelayPolicy {
    private const val POINTED_MODE_DWELL_DELAY_MS = 150L
    private const val AREA_MODE_SETTLE_DELAY_MS = 90L

    fun pointerStoppedDelayMs(textDetectMode: TextDetectMode): Long {
        return when (textDetectMode) {
            TextDetectMode.SELECT,
            TextDetectMode.FIXED_AREA -> AREA_MODE_SETTLE_DELAY_MS
            else -> POINTED_MODE_DWELL_DELAY_MS
        }
    }

    fun captureStartDelayMs(): Long = 0L
}

object PointerDwellPolicy {
    fun shouldRestartDwell(
        previous: Point?,
        current: Point,
        marginDistance: Int,
    ): Boolean {
        return previous == null || hasSignificantPointerMove(
            previousX = previous.x,
            previousY = previous.y,
            currentX = current.x,
            currentY = current.y,
            marginDistance = marginDistance,
        )
    }

    fun hasSignificantPointerMove(
        previousX: Int,
        previousY: Int,
        currentX: Int,
        currentY: Int,
        marginDistance: Int,
    ): Boolean {
        return distanceSquared(previousX, previousY, currentX, currentY) >
                marginDistance.toLong() * marginDistance.toLong()
    }

    fun shouldEmitPosition(
        current: Point,
        lastEmittedPoint: Point?,
        marginDistance: Int,
    ): Boolean {
        return lastEmittedPoint?.let {
            shouldEmitPosition(
                currentX = current.x,
                currentY = current.y,
                lastEmittedX = it.x,
                lastEmittedY = it.y,
                marginDistance = marginDistance,
            )
        } ?: true
    }

    fun shouldEmitPosition(
        currentX: Int,
        currentY: Int,
        lastEmittedX: Int,
        lastEmittedY: Int,
        marginDistance: Int,
    ): Boolean {
        return distanceSquared(currentX, currentY, lastEmittedX, lastEmittedY) >
                marginDistance.toLong() * marginDistance.toLong()
    }

    private fun distanceSquared(
        point1X: Int,
        point1Y: Int,
        point2X: Int,
        point2Y: Int,
    ): Long {
        val dx = point1X.toLong() - point2X.toLong()
        val dy = point1Y.toLong() - point2Y.toLong()
        return dx * dx + dy * dy
    }
}

class PointerDockingReleaseTracker {
    private var draggedSide: PointerSide? = null

    fun shouldScheduleDockAfterRelease(
        side: PointerSide,
        activeSide: PointerSide,
        motionEventAction: Int,
        translationActive: Boolean,
        menuOperating: Boolean,
    ): Boolean {
        if (activeSide != side || menuOperating) {
            reset()
            return false
        }

        return when (motionEventAction) {
            MotionEvent.ACTION_MOVE -> {
                draggedSide = side
                false
            }

            MotionEvent.ACTION_UP -> {
                val shouldSchedule = draggedSide == side && !translationActive
                reset()
                shouldSchedule
            }

            MotionEvent.ACTION_CANCEL -> {
                reset()
                false
            }

            else -> false
        }
    }

    fun reset() {
        draggedSide = null
    }
}

object OverlayWindowAlpha {
    const val passThroughVisibleAlpha: Float = 0.8f

    fun forPassThroughVisibility(visible: Boolean): Float {
        return if (visible) passThroughVisibleAlpha else 0.0f
    }

    fun forTouchableVisibility(visible: Boolean): Float {
        return if (visible) 1.0f else 0.0f
    }
}

enum class TargetIconTint {
    NONE,
    SELECT,
    FIXED_AREA,
}

data class TargetIconRenderState(
    val pointerVisible: Boolean = false,
    val progressVisible: Boolean = false,
    val dimmed: Boolean = false,
    val captureRequested: Boolean = false,
    val writingRtl: Boolean = false,
    val tint: TargetIconTint = TargetIconTint.NONE,
)

object TargetIconRenderPolicy {
    const val CAPTURE_ALPHA = 0.0f

    fun stateFor(
        side: PointerSide,
        activeSide: PointerSide,
        motionEventAction: Int,
        textDetectMode: TextDetectMode,
        captureStatus: CaptureStatus,
        fixedAreaTranslating: Boolean,
        translateStatus: TranslateStatus,
        areaSelecting: Boolean,
        writingRtl: Boolean,
    ): TargetIconRenderState {
        return TargetIconRenderState(
            pointerVisible = PointerInteractionVisibilityPolicy.isInteractionActive(motionEventAction) &&
                    side == activeSide,
            progressVisible = translateStatus == TranslateStatus.Requested &&
                    textDetectMode != TextDetectMode.SELECT,
            dimmed = captureStatus == CaptureStatus.Requested || fixedAreaTranslating,
            captureRequested = captureStatus == CaptureStatus.Requested,
            writingRtl = writingRtl,
            tint = when {
                !areaSelecting -> TargetIconTint.NONE
                textDetectMode == TextDetectMode.SELECT -> TargetIconTint.SELECT
                else -> TargetIconTint.FIXED_AREA
            },
        )
    }

    fun contentAlpha(state: TargetIconRenderState): Float {
        if (state.captureRequested) return CAPTURE_ALPHA
        if (state.pointerVisible) return 1.0f
        return if (state.dimmed) CAPTURE_ALPHA else 1.0f
    }
}

class StableWindowVisibilityState {
    private var visible: Boolean? = null

    fun markIfChanged(newVisible: Boolean): Boolean {
        if (visible == newVisible) return false
        visible = newVisible
        return true
    }

    fun reset() {
        visible = null
    }
}

class PointerCalibrationSession(
    savedLeft: PointerOffset,
    savedRight: PointerOffset,
    private val defaultOffset: PointerOffset = PointerOffset.DEFAULT,
) {
    private var savedOffsets = PointerOffsetPair(savedLeft, savedRight)
    private var currentOffsets = savedOffsets

    fun drag(side: PointerSide, dx: Int, dy: Int) {
        val current = current(side)
        setCurrent(side, PointerOffset(current.x - dx, current.y - dy))
    }

    fun reset(side: PointerSide) {
        setCurrent(side, defaultOffset)
    }

    fun current(side: PointerSide): PointerOffset = when (side) {
        PointerSide.LEFT -> currentOffsets.left
        PointerSide.RIGHT -> currentOffsets.right
    }

    fun saved(side: PointerSide): PointerOffset = when (side) {
        PointerSide.LEFT -> savedOffsets.left
        PointerSide.RIGHT -> savedOffsets.right
    }

    fun confirm(): PointerOffsetPair {
        savedOffsets = currentOffsets
        return savedOffsets
    }

    private fun setCurrent(side: PointerSide, offset: PointerOffset) {
        currentOffsets = when (side) {
            PointerSide.LEFT -> currentOffsets.copy(left = offset)
            PointerSide.RIGHT -> currentOffsets.copy(right = offset)
        }
    }
}

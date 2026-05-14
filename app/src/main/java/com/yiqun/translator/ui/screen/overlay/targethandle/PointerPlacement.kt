package com.yiqun.translator.ui.screen.overlay.targethandle

import android.content.res.Configuration
import android.graphics.Point

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

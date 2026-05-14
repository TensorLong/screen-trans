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
    fun encode(): String = "$x,$y"

    companion object {
        val DEFAULT = PointerOffset(0, 0)

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
            val parts = value.split(",")
            if (parts.size != 2) return defaultValue
            val x = parts[0].trim().toIntOrNull() ?: return defaultValue
            val y = parts[1].trim().toIntOrNull() ?: return defaultValue
            return PointerOffset(x, y)
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

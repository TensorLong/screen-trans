package com.yiqun.translator.data.local.vision.model

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.yiqun.translator.data.local.vision.VisionCoordinateMapper
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.extensions._cutDecimal
import com.yiqun.translator.extensions._unionWith
import timber.log.Timber
import java.util.Locale
import kotlin.math.abs
import kotlin.properties.Delegates

private const val TRACE_LINE_COLOR_LOGS = false

/**
 */
data class Line(
    val words: MutableList<Word>,
    override val writingDirection: WritingDirection
) : VisionSingleLineText {

    private var boundingBoxCache: Rect? = null

    override val boundingBox: Rect
        get() {
            if (boundingBoxCache == null) {
                boundingBoxCache = if (words.isEmpty()) {
                    Rect()
                } else {
                    words.map { it.boundingBox }.reduce { acc, rect -> acc._unionWith(rect) }
                }
            }
            return boundingBoxCache!!
        }

    override val representation: String
        get() = words.joinToString(separator = " ") { it.representation }

    /**
     */
    fun addWord(newWord: Word) {
        var left = 0
        var right = words.size

        when (writingDirection) {
            WritingDirection.LTR -> { // LTR and default case
                while (left < right) {
                    val mid = (left + right) / 2
                    if (words[mid].boundingBox.left < newWord.boundingBox.left) {
                        left = mid + 1
                    } else {
                        right = mid
                    }
                }
            }

            WritingDirection.RTL -> {
                while (left < right) {
                    val mid = (left + right) / 2
                    if (words[mid].boundingBox.left > newWord.boundingBox.left) {
                        left = mid + 1
                    } else {
                        right = mid
                    }
                }
            }

            else -> { // TTB_LTR, TTB_RTL
                while (left < right) {
                    val mid = (left + right) / 2
                    if (words[mid].boundingBox.top < newWord.boundingBox.top) {
                        left = mid + 1
                    } else {
                        right = mid
                    }
                }
            }

        }
        words.add(left, newWord)
        boundingBoxCache = boundingBoxCache
            ?.let { cached -> cached._unionWith(newWord.boundingBox) }
    }

    var fontColor by Delegates.notNull<Int>()

    var backgroundColor by Delegates.notNull<Int>()

    /**
     */
    fun setFontAndBackgroundColors(
        bitmap: Bitmap,
        coordinateOffsetX: Int = 0,
        coordinateOffsetY: Int = 0,
    ) {
        val localBoundingBox = VisionCoordinateMapper.toLocalRect(boundingBox, coordinateOffsetX, coordinateOffsetY)

        val left = localBoundingBox.left - 2
        val right = localBoundingBox.right + 2
        val top = localBoundingBox.top - 2
        val bottom = localBoundingBox.bottom + 2

        val x1 = (localBoundingBox.left + localBoundingBox.width() / 3).coerceIn(0, bitmap.width - 1)
        val x2 = (localBoundingBox.left + 2 * localBoundingBox.width() / 3).coerceIn(0, bitmap.width - 1)
        val y1 = (localBoundingBox.top + localBoundingBox.height() / 3).coerceIn(0, bitmap.height - 1)
        val y2 = (localBoundingBox.top + 2 * localBoundingBox.height() / 3).coerceIn(0, bitmap.height - 1)

        // Guards must be two-sided: ML Kit bounding boxes may extend past the bitmap
        // (the vertical-writing path passes them unclamped), and a one-sided check let
        // getPixel throw IllegalArgumentException on edge-touching boxes.
        val leftPixels = listOfNotNull(
            if (left in 0 until bitmap.width) bitmap.getPixel(left, y1) else null,
            if (left in 0 until bitmap.width) bitmap.getPixel(left, y2) else null
        )

        val rightPixels = listOfNotNull(
            if (right in 0 until bitmap.width) bitmap.getPixel(right, y1) else null,
            if (right in 0 until bitmap.width) bitmap.getPixel(right, y2) else null
        )

        val topPixels = listOfNotNull(
            if (top in 0 until bitmap.height) bitmap.getPixel(x1, top) else null,
            if (top in 0 until bitmap.height) bitmap.getPixel(x2, top) else null
        )

        val bottomPixels = listOfNotNull(
            if (bottom in 0 until bitmap.height) bitmap.getPixel(x1, bottom) else null,
            if (bottom in 0 until bitmap.height) bitmap.getPixel(x2, bottom) else null
        )

        val pixelColors = leftPixels + rightPixels + topPixels + bottomPixels
        val backgroundColor = mostFrequentColor(pixelColors) ?: Color.WHITE

        val allChars = words.flatMap { it.chars }
        val midIndex = allChars.size / 2
        val nonSpecialChar = if (allChars.isEmpty()) {
            null
        } else {
            (0..midIndex).firstNotNullOfOrNull { index ->
                listOf(allChars.getOrNull(midIndex + index), allChars.getOrNull(midIndex - index)).find {
                    it?.representation?.any { ch -> ch.isLetterOrDigit() } == true
                }
            } ?: allChars[midIndex]
        } ?: run {
            fontColor = Color.BLACK
            this.fontColor = fontColor
            this.backgroundColor = backgroundColor
            return
        }


        val charBoundingBox = VisionCoordinateMapper.toLocalRect(nonSpecialChar.boundingBox, coordinateOffsetX, coordinateOffsetY)
        if (TRACE_LINE_COLOR_LOGS) Timber.tag("Line").d("charBoundingBox $nonSpecialChar ${nonSpecialChar.boundingBox}")

        val charWidth = charBoundingBox.width().coerceIn(1, bitmap.width)
        val charHeight = charBoundingBox.height().coerceIn(1, bitmap.height)

        val horizontalPixelData1 = IntArray(charWidth)
        val horizontalY1 = (charBoundingBox.top + charBoundingBox.height() / 3).coerceIn(0, bitmap.height - 1)
        bitmap.getPixels(
            horizontalPixelData1,
            0,
            charWidth,
            charBoundingBox.left.coerceIn(0, bitmap.width - charWidth),
            horizontalY1,
            charWidth,
            1,
        )

        val horizontalPixelData2 = IntArray(charWidth)
        val horizontalY2 = (charBoundingBox.top + 2 * charBoundingBox.height() / 3).coerceIn(0, bitmap.height - 1)
        bitmap.getPixels(
            horizontalPixelData2,
            0,
            charWidth,
            charBoundingBox.left.coerceIn(0, bitmap.width - charWidth),
            horizontalY2,
            charWidth,
            1,
        )

        val verticalPixelData1 = IntArray(charHeight)
        val verticalX1 = (charBoundingBox.left + charBoundingBox.width() / 3).coerceIn(0, bitmap.width - 1)
        bitmap.getPixels(
            verticalPixelData1,
            0,
            1,
            verticalX1,
            charBoundingBox.top.coerceIn(0, bitmap.height - charHeight),
            1,
            charHeight,
        )

        val verticalPixelData2 = IntArray(charHeight)
        val verticalX2 = (charBoundingBox.left + 2 * charBoundingBox.width() / 3).coerceIn(0, bitmap.width - 1)
        bitmap.getPixels(
            verticalPixelData2,
            0,
            1,
            verticalX2,
            charBoundingBox.top.coerceIn(0, bitmap.height - charHeight),
            1,
            charHeight,
        )

        val colorCountMap = mutableMapOf<Int, Int>()
        countColors(horizontalPixelData1, colorCountMap)
        countColors(horizontalPixelData2, colorCountMap)
        countColors(verticalPixelData1, colorCountMap)
        countColors(verticalPixelData2, colorCountMap)

        val sortedColors = colorCountMap.entries.sortedByDescending { it.value }

        var fontColor = Color.BLACK
        var leastSimilarity = Double.MAX_VALUE

        sortedColors.forEach { (color, _) ->
            val similarity = calculateColorSimilarity(backgroundColor, color)
            if (similarity < leastSimilarity) {
                leastSimilarity = similarity
                fontColor = color
            }
        }

        if (TRACE_LINE_COLOR_LOGS) {
            val colorFrequency = sortedColors.joinToString(", ") { entry ->
                "${String.format(Locale.US, "#%08X", entry.key)} - ${entry.value}"
            }

            val pixelColorsString = pixelColors.joinToString(", ") { entry ->
                "${String.format(Locale.US, "#%08X", entry)}"
            }

            Timber.tag("Line").d(
                "-------------- getFontAndBackgroundColors " +
                        "fontColor: ${String.format("#%08X", fontColor)}, " +
                        "backgroundColor: ${String.format("#%08X", backgroundColor)}, " +
                        "line: ${representation}, " +
                        "nonSpecialChar: ${nonSpecialChar.representation}, " +
                        "pixelColorsString: $pixelColorsString, " +
                        "charPixelData: $colorFrequency"
            )
        }

        this.fontColor = fontColor
        this.backgroundColor = backgroundColor
    }

    fun getColorSimilarity(other: Line): Double {

        if (TRACE_LINE_COLOR_LOGS) {
            Timber.tag("VisionRepository").i(
                "----- getColorSimilarity ${String.format("#%08X", fontColor)} "
                        + "${String.format("#%08X", backgroundColor)} "
                        + "${String.format("#%08X", other.fontColor)} "
                        + "${String.format("#%08X", other.backgroundColor)} "
            )
        }

        val returnThis = if (other.fontColor == fontColor) {
            if (other.backgroundColor == backgroundColor) {
                1.0
            } else {
                calculateColorSimilarity(other.backgroundColor, backgroundColor)
            }
        } else if (other.backgroundColor == backgroundColor) {
            calculateColorSimilarity(other.fontColor, fontColor)
        } else {
            val fontColorSimilarity = calculateColorSimilarity(other.fontColor, fontColor)
            val backgroundColorSimilarity = calculateColorSimilarity(other.backgroundColor, backgroundColor)
            (fontColorSimilarity + backgroundColorSimilarity) / 2
        }


        return returnThis
    }

    /**
     */
    private fun calculateColorSimilarity(color1: Int, color2: Int): Double {
        val red1 = Color.red(color1)
        val green1 = Color.green(color1)
        val blue1 = Color.blue(color1)

        val red2 = Color.red(color2)
        val green2 = Color.green(color2)
        val blue2 = Color.blue(color2)

        val redDifference = abs(red1 - red2) * 0.39
        val greenDifference = abs(green1 - green2) * 0.39
        val blueDifference = abs(blue1 - blue2) * 0.39

        val redSimilarity = (100 - redDifference) / 100
        val greenSimilarity = (100 - greenDifference) / 100
        val blueSimilarity = (100 - blueDifference) / 100

        if (TRACE_LINE_COLOR_LOGS) {
            Timber.tag("VisionRepository").d(
                "----- calculateColorSimilarity ${String.format("#%08X", color1)} $red1 $green1 $blue1 ${
                    String.format(
                        "#%08X",
                        color2
                    )
                } $red2 $green2 $blue2 -- ${redSimilarity._cutDecimal()}  ${greenSimilarity._cutDecimal()}  ${blueSimilarity._cutDecimal()}  "
            )
        }

        return (redSimilarity + greenSimilarity + blueSimilarity) / 3
    }

    private fun countColors(
        pixels: IntArray,
        colorCountMap: MutableMap<Int, Int>,
    ) {
        for (pixel in pixels) {
            // Map.getOrDefault needs API 24 / desugaring; the elvis form works on minSdk 23.
            colorCountMap[pixel] = (colorCountMap[pixel] ?: 0) + 1
        }
    }

    private fun mostFrequentColor(colors: List<Int>): Int? {
        if (colors.isEmpty()) return null
        val counts = mutableMapOf<Int, Int>()
        for (color in colors) {
            counts[color] = (counts[color] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }

}

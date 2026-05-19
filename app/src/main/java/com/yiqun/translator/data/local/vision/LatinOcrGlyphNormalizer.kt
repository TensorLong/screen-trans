package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_FOREGROUND_DELTA = 18

internal object LatinOcrGlyphNormalizer {

    fun needsElementNormalization(rawText: String): Boolean {
        if (rawText == "Ill" || rawText == "00" || rawText.startsWith("O00")) return true

        var consecutiveOnes = 0
        rawText.forEach { char ->
            when (char) {
                '|', '\'', '’', '"' -> return true
                '1' -> consecutiveOnes += 1
                'l' -> {
                    if (consecutiveOnes >= 3) return true
                    consecutiveOnes = 0
                }
                else -> consecutiveOnes = 0
            }
        }
        return false
    }

    fun normalizeSymbol(
        bitmap: Bitmap,
        localBoundingBox: Rect,
        text: String,
    ): String {
        if (text.length != 1) return text

        val char = text[0]
        if (char !in AMBIGUOUS_SYMBOLS) return text

        val profile = GlyphInkProfile.from(bitmap, localBoundingBox) ?: return text
        return when (char) {
            '0' -> if (profile.looksLikeUppercaseO()) "O" else "0"
            'O' -> if (profile.looksLikeDigitZero()) "0" else "O"
            else -> text
        }
    }

    fun normalizeElementText(rawText: String, normalizedSymbolText: String): String {
        val symbolText = normalizedSymbolText.takeIf { it.isNotBlank() && it.length == rawText.length } ?: rawText
        return (if (symbolText == "Ill") "I'll" else symbolText.replace('|', 'l'))
            .let { if (it == "00") "O0" else it }
            .replace("I'Il", "I'll")
            .replace("I’Il", "I’ll")
            .replace("I\"Il", "I'll")
            .let { text ->
                val pipeNormalized = if (rawText.startsWith("|1")) {
                    text.replaceFirst("l1", "I1")
                } else {
                    text
                }
                if (rawText.contains('|')) {
                    pipeNormalized.replace("lI", "ll")
                } else {
                    pipeNormalized
                }
            }
            .replace("111l", "1I1l")
            .replace("O001I1l", "O0O1I1l")
    }

    private val AMBIGUOUS_SYMBOLS = setOf('0', 'O')

    private data class GlyphInkProfile(
        val inkWidth: Int,
        val inkHeight: Int,
    ) {
        fun looksLikeUppercaseO(): Boolean {
            if (inkHeight <= 0) return false
            return inkWidth.toDouble() / inkHeight.toDouble() >= 0.68
        }

        fun looksLikeDigitZero(): Boolean {
            if (inkHeight <= 0) return false
            return inkWidth.toDouble() / inkHeight.toDouble() < 0.64
        }

        companion object {
            fun from(bitmap: Bitmap, boundingBox: Rect): GlyphInkProfile? {
                val left = boundingBox.left.coerceIn(0, bitmap.width - 1)
                val top = boundingBox.top.coerceIn(0, bitmap.height - 1)
                val right = boundingBox.right.coerceIn(left + 1, bitmap.width)
                val bottom = boundingBox.bottom.coerceIn(top + 1, bitmap.height)
                val width = right - left
                val height = bottom - top
                if (width <= 0 || height <= 0) return null

                val background = estimateBackgroundLuma(bitmap, left, top, right, bottom)
                var minLuma = 255
                var maxLuma = 0
                for (y in top until bottom) {
                    for (x in left until right) {
                        val luma = bitmap.getPixel(x, y).luma()
                        if (luma < minLuma) minLuma = luma
                        if (luma > maxLuma) maxLuma = luma
                    }
                }

                val foregroundIsDarker = abs(background - minLuma) >= abs(maxLuma - background)
                val contrast = if (foregroundIsDarker) abs(background - minLuma) else abs(maxLuma - background)
                val threshold = max(MIN_FOREGROUND_DELTA, (contrast * 0.32).roundToInt())

                var inkLeft = Int.MAX_VALUE
                var inkRight = Int.MIN_VALUE
                var inkTop = Int.MAX_VALUE
                var inkBottom = Int.MIN_VALUE

                for (y in top until bottom) {
                    for (x in left until right) {
                        val luma = bitmap.getPixel(x, y).luma()
                        val isForeground = if (foregroundIsDarker) {
                            background - luma >= threshold
                        } else {
                            luma - background >= threshold
                        }
                        if (!isForeground) continue

                        if (x < inkLeft) inkLeft = x
                        if (x > inkRight) inkRight = x
                        if (y < inkTop) inkTop = y
                        if (y > inkBottom) inkBottom = y
                    }
                }

                if (inkLeft == Int.MAX_VALUE) return null
                return GlyphInkProfile(
                    inkWidth = inkRight - inkLeft + 1,
                    inkHeight = inkBottom - inkTop + 1,
                )
            }

            private fun estimateBackgroundLuma(
                bitmap: Bitmap,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
            ): Int {
                val samples = IntArray(8)
                val x1 = left
                val x2 = (right - 1).coerceAtLeast(left)
                val y1 = top
                val y2 = (bottom - 1).coerceAtLeast(top)
                val midX = (left + right - 1) / 2
                val midY = (top + bottom - 1) / 2
                samples[0] = bitmap.getPixel(x1, y1).luma()
                samples[1] = bitmap.getPixel(x2, y1).luma()
                samples[2] = bitmap.getPixel(x1, y2).luma()
                samples[3] = bitmap.getPixel(x2, y2).luma()
                samples[4] = bitmap.getPixel(midX, y1).luma()
                samples[5] = bitmap.getPixel(midX, y2).luma()
                samples[6] = bitmap.getPixel(x1, midY).luma()
                samples[7] = bitmap.getPixel(x2, midY).luma()
                samples.sort()
                return (samples[3] + samples[4]) / 2
            }
        }
    }

    private fun Int.luma(): Int {
        return ((Color.red(this) * 299) + (Color.green(this) * 587) + (Color.blue(this) * 114)) / 1000
    }
}

package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_FOREGROUND_DELTA = 18
private const val TEMPLATE_SIZE = 28
private val LEADING_ZERO_IN_UPPERCASE_TOKEN = Regex("^0(?=[A-Z].*[A-Z])")
private val TRAILING_SINGLE_QUOTE_UPPERCASE_PAIR = Regex("^([A-Z]{2})'$")
private val LEADING_SINGLE_QUOTE_UPPERCASE_PAIR = Regex("^'([A-Z]{2})$")
private val MISSING_SPACE_BEFORE_QUOTED_UPPERCASE_PAIR = Regex("^([A-Za-z]+)'([A-Z]{2})$")
private val LEADING_PAREN_BEFORE_HANDLE = Regex("^\\(@(?=[A-Za-z0-9_])")
private val DUPLICATED_LOWERCASE_BEFORE_HANDLE = Regex("^([a-z])\\1@(?=[A-Za-z0-9])")
private val PRICE_TOKEN = Regex("^\\$\\d+(\\.\\d+)?$")

internal object LatinOcrGlyphNormalizer {

    fun needsElementNormalization(rawText: String): Boolean {
        if (rawText == "Ill" || rawText == "00" || rawText.startsWith("O00")) return true
        if (rawText.startsWith("(@") || rawText in WORD_REPLACEMENTS) return true
        if (rawText.contains('Ọ')) return true
        if (LEADING_ZERO_IN_UPPERCASE_TOKEN.containsMatchIn(rawText) || rawText.contains("0K")) return true

        var consecutiveOnes = 0
        rawText.forEach { char ->
            when (char) {
                '|', '\'', '’', '"', '@' -> return true
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

    fun mayRecoverMissingSeparator(rawText: String): Boolean {
        return rawText.length == 2 &&
                rawText.all { it.isLetterOrDigit() } &&
                rawText.none { it.isLetter() && it.isLowerCase() }
    }

    fun mayNeedSymbolNormalization(rawText: String): Boolean {
        if (rawText.any { it == '0' || it == '8' || it == '$' }) return true
        if (rawText.startsWith("f") && rawText.endsWith("}")) return true
        return rawText.length == 2 && rawText.any { it in "Vvy" }
    }

    fun mayNeedHiddenTokenWordSplit(rawText: String): Boolean {
        if (rawText.length < 3) return false
        if (rawText.any { it.isDigit() || it in HIDDEN_TOKEN_SPLIT_TRIGGERS }) return true
        val uppercasePrefixLength = rawText.takeWhile { it.isUpperCase() }.length
        return uppercasePrefixLength in 2..4 &&
                rawText.getOrNull(uppercasePrefixLength)?.isLowerCase() == true
    }

    fun normalizeSymbol(
        bitmap: Bitmap,
        localBoundingBox: Rect,
        text: String,
        rawElementText: String = text,
        symbolIndex: Int = -1,
    ): String {
        if (text.length != 1) return text

        val char = text[0]
        val candidates = candidateFamily(char, rawElementText, symbolIndex) ?: return text

        val templateChoice = GlyphTemplateMatcher.match(
            bitmap = bitmap,
            boundingBox = localBoundingBox,
            rawChar = char,
            candidates = candidates,
        )
        return templateChoice?.toString() ?: text
    }

    fun normalizeElementText(
        rawText: String,
        normalizedSymbolText: String,
        bitmap: Bitmap? = null,
        elementBoundingBox: Rect? = null,
        symbolBoundingBoxes: List<Rect> = emptyList(),
    ): String {
        val symbolText = normalizedSymbolText.takeIf { it.isNotBlank() && it.length == rawText.length } ?: rawText
        val separatorRecoveredText = recoverMissingSeparator(
            bitmap = bitmap,
            elementBoundingBox = elementBoundingBox,
            symbolBoundingBoxes = symbolBoundingBoxes,
            text = symbolText,
        ) ?: symbolText
        return (if (separatorRecoveredText == "Ill") "I'll" else separatorRecoveredText.replace('|', 'l'))
            .let { WORD_REPLACEMENTS[it] ?: it }
            .replace('Ọ', 'Q')
            .replace(LEADING_ZERO_IN_UPPERCASE_TOKEN, "O")
            .replace("0K", "OK")
            .replace(TRAILING_SINGLE_QUOTE_UPPERCASE_PAIR, "'$1'")
            .replace(LEADING_SINGLE_QUOTE_UPPERCASE_PAIR, "'$1'")
            .replace(MISSING_SPACE_BEFORE_QUOTED_UPPERCASE_PAIR, "$1 '$2'")
            .replace("'OkK", "'OK'")
            .let { if (it == "00") "O0" else it }
            .replace("I'Il", "I'll")
            .replace("I’Il", "I’ll")
            .replace("I\"Il", "I'll")
            .replace(LEADING_PAREN_BEFORE_HANDLE, "@")
            .replace(DUPLICATED_LOWERCASE_BEFORE_HANDLE, "$1@")
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
            .replace("0001I1l", "O0O1I1l")
            .replace("O001I1l", "O0O1I1l")
    }

    fun detectSeparator(bitmap: Bitmap, localGap: Rect): String? {
        return SeparatorInk.from(bitmap = bitmap, gap = localGap)?.classify()?.toString()
    }

    private fun recoverMissingSeparator(
        bitmap: Bitmap?,
        elementBoundingBox: Rect?,
        symbolBoundingBoxes: List<Rect>,
        text: String,
    ): String? {
        if (bitmap == null || elementBoundingBox == null) return null
        if (text.length != 2 || !text.all { it.isLetterOrDigit() } || symbolBoundingBoxes.size != 2) return null
        if (text.any { it.isLetter() && !it.isUpperCase() }) return null

        val boxes = symbolBoundingBoxes.sortedBy { it.left }
        val first = boxes[0]
        val second = boxes[1]
        val edgeAllowance = ((first.width() + second.width()) * 0.09f).roundToInt().coerceAtLeast(2)
        val gapLeft = (first.right - edgeAllowance).coerceAtLeast(elementBoundingBox.left)
        val gapRight = (second.left + edgeAllowance).coerceAtMost(elementBoundingBox.right)
        if (gapRight - gapLeft < 3) return null

        val separator = SeparatorInk.from(
            bitmap = bitmap,
            gap = Rect(
                gapLeft,
                elementBoundingBox.top,
                gapRight,
                elementBoundingBox.bottom,
            ),
        )?.classify() ?: return null
        return "${text[0]}$separator${text[1]}"
    }

    private fun candidateFamily(char: Char, rawElementText: String, symbolIndex: Int): Set<Char>? {
        return when (char) {
            '0' -> if (rawElementText.isNumericToken() || rawElementText.hasUppercaseZeroUppercaseContext(symbolIndex)) {
                null
            } else {
                setOf('0', 'O', 'Q')
            }
            '8' -> setOf('8', '&')
            '$' -> if (rawElementText.isPlainPriceToken()) {
                null
            } else {
                setOf('$', 'S')
            }
            'f' -> if (rawElementText.endsWith("}") && symbolIndex == 0) {
                setOf('{', 'f')
            } else {
                null
            }
            'V', 'y' -> if (rawElementText.length == 2 && rawElementText.any { it in "Vvy" }) {
                setOf('v', 'V', 'y')
            } else {
                null
            }
            else -> null
        }
    }

    private fun String.isNumericToken(): Boolean {
        if (isEmpty()) return false
        if (any { it == '$' || it == '%' || it == '.' || it == ',' }) return true
        return all { it.isDigit() }
    }

    private fun String.isPlainPriceToken(): Boolean {
        return matches(PRICE_TOKEN)
    }

    private fun String.hasUppercaseZeroUppercaseContext(symbolIndex: Int): Boolean {
        if (symbolIndex <= 0 || symbolIndex >= lastIndex) return false
        return getOrNull(symbolIndex - 1)?.isUpperCase() == true &&
                getOrNull(symbolIndex + 1)?.isUpperCase() == true
    }

    private object GlyphTemplateMatcher {
        private val templateCache = mutableMapOf<Char, GlyphMask?>()

        fun match(
            bitmap: Bitmap,
            boundingBox: Rect,
            rawChar: Char,
            candidates: Set<Char>,
        ): Char? {
            val actual = GlyphMask.fromBitmap(bitmap, boundingBox) ?: return null
            val scored = candidates.mapNotNull { candidate ->
                val template = templateCache.getOrPut(candidate) {
                    GlyphMask.fromTemplate(candidate)
                } ?: return@mapNotNull null
                candidate to actual.distanceTo(template)
            }
            if (scored.isEmpty()) return null

            val rawScore = scored.firstOrNull { it.first == rawChar }?.second ?: return null
            val best = scored.minBy { it.second }
            if (best.first == rawChar) return rawChar
            if (best.second > 0.48f) return rawChar
            if (rawScore - best.second < 0.055f) return rawChar
            return best.first
        }
    }

    private data class GlyphMask(
        val pixels: BooleanArray,
    ) {
        fun distanceTo(other: GlyphMask): Float {
            var intersection = 0
            var union = 0
            for (index in pixels.indices) {
                val a = pixels[index]
                val b = other.pixels[index]
                if (a && b) intersection += 1
                if (a || b) union += 1
            }
            if (union == 0) return 1.0f
            return 1.0f - (intersection.toFloat() / union.toFloat())
        }

        companion object {
            fun fromTemplate(char: Char): GlyphMask? {
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 46f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    isSubpixelText = true
                }
                val bounds = Rect()
                val text = char.toString()
                paint.getTextBounds(text, 0, text.length, bounds)
                val x = ((bitmap.width - bounds.width()) / 2f) - bounds.left
                val y = ((bitmap.height - bounds.height()) / 2f) - bounds.top
                canvas.drawText(text, x, y, paint)
                val mask = fromBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
                bitmap.recycle()
                return mask
            }

            fun fromBitmap(bitmap: Bitmap, boundingBox: Rect): GlyphMask? {
                val left = boundingBox.left.coerceIn(0, bitmap.width - 1)
                val top = boundingBox.top.coerceIn(0, bitmap.height - 1)
                val right = boundingBox.right.coerceIn(left + 1, bitmap.width)
                val bottom = boundingBox.bottom.coerceIn(top + 1, bitmap.height)
                if (right <= left || bottom <= top) return null

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
                fun isForeground(x: Int, y: Int): Boolean {
                    val luma = bitmap.getPixel(x, y).luma()
                    return if (foregroundIsDarker) {
                        background - luma >= threshold
                    } else {
                        luma - background >= threshold
                    }
                }

                var inkLeft = Int.MAX_VALUE
                var inkRight = Int.MIN_VALUE
                var inkTop = Int.MAX_VALUE
                var inkBottom = Int.MIN_VALUE
                for (y in top until bottom) {
                    for (x in left until right) {
                        if (!isForeground(x, y)) continue
                        if (x < inkLeft) inkLeft = x
                        if (x > inkRight) inkRight = x
                        if (y < inkTop) inkTop = y
                        if (y > inkBottom) inkBottom = y
                    }
                }
                if (inkLeft == Int.MAX_VALUE) return null

                val inkWidth = inkRight - inkLeft + 1
                val inkHeight = inkBottom - inkTop + 1
                val pixels = BooleanArray(TEMPLATE_SIZE * TEMPLATE_SIZE)
                for (maskY in 0 until TEMPLATE_SIZE) {
                    val sourceY = (inkTop + ((maskY + 0.5f) * inkHeight / TEMPLATE_SIZE)).toInt()
                        .coerceIn(inkTop, inkBottom)
                    for (maskX in 0 until TEMPLATE_SIZE) {
                        val sourceX = (inkLeft + ((maskX + 0.5f) * inkWidth / TEMPLATE_SIZE)).toInt()
                            .coerceIn(inkLeft, inkRight)
                        pixels[(maskY * TEMPLATE_SIZE) + maskX] = isForeground(sourceX, sourceY)
                    }
                }
                return GlyphMask(pixels)
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

    private data class SeparatorInk(
        val inkWidth: Int,
        val inkHeight: Int,
        val inkBottomOffset: Int,
        val gapHeight: Int,
        val topAverageX: Double,
        val bottomAverageX: Double,
    ) {
        fun classify(): Char? {
            if (inkWidth <= 0 || inkHeight <= 0 || gapHeight <= 0) return null
            if (inkHeight.toDouble() / gapHeight.toDouble() <= 0.22 && inkBottomOffset.toDouble() / gapHeight.toDouble() >= 0.68) {
                return '_'
            }
            val slope = topAverageX - bottomAverageX
            return when {
                slope >= 1.2 -> '\\'
                slope <= -1.2 -> '/'
                else -> null
            }
        }

        companion object {
            fun from(bitmap: Bitmap, gap: Rect): SeparatorInk? {
                val left = gap.left.coerceIn(0, bitmap.width - 1)
                val top = gap.top.coerceIn(0, bitmap.height - 1)
                val right = gap.right.coerceIn(left + 1, bitmap.width)
                val bottom = gap.bottom.coerceIn(top + 1, bitmap.height)
                if (right <= left || bottom <= top) return null

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
                fun isForeground(x: Int, y: Int): Boolean {
                    val luma = bitmap.getPixel(x, y).luma()
                    return if (foregroundIsDarker) {
                        background - luma >= threshold
                    } else {
                        luma - background >= threshold
                    }
                }

                var inkLeft = Int.MAX_VALUE
                var inkRight = Int.MIN_VALUE
                var inkTop = Int.MAX_VALUE
                var inkBottom = Int.MIN_VALUE
                var inkCount = 0
                var topSum = 0
                var topCount = 0
                var bottomSum = 0
                var bottomCount = 0
                val gapHeight = bottom - top
                for (y in top until bottom) {
                    for (x in left until right) {
                        if (!isForeground(x, y)) continue
                        inkCount += 1
                        if (x < inkLeft) inkLeft = x
                        if (x > inkRight) inkRight = x
                        if (y < inkTop) inkTop = y
                        if (y > inkBottom) inkBottom = y

                        val yRatio = (y - top).toDouble() / gapHeight.toDouble()
                        if (yRatio <= 0.42) {
                            topSum += x
                            topCount += 1
                        } else if (yRatio >= 0.58) {
                            bottomSum += x
                            bottomCount += 1
                        }
                    }
                }
                if (inkCount < 3 || inkLeft == Int.MAX_VALUE) return null
                val inkWidth = inkRight - inkLeft + 1
                val inkHeight = inkBottom - inkTop + 1
                val inkBottomOffset = inkBottom - top
                if (inkHeight.toDouble() / gapHeight.toDouble() <= 0.22 && inkBottomOffset.toDouble() / gapHeight.toDouble() >= 0.68) {
                    return SeparatorInk(
                        inkWidth = inkWidth,
                        inkHeight = inkHeight,
                        inkBottomOffset = inkBottomOffset,
                        gapHeight = gapHeight,
                        topAverageX = 0.0,
                        bottomAverageX = 0.0,
                    )
                }
                if (topCount == 0 || bottomCount == 0) return null
                return SeparatorInk(
                    inkWidth = inkWidth,
                    inkHeight = inkHeight,
                    inkBottomOffset = inkBottomOffset,
                    gapHeight = gapHeight,
                    topAverageX = topSum.toDouble() / topCount.toDouble(),
                    bottomAverageX = bottomSum.toDouble() / bottomCount.toDouble(),
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

    private val WORD_REPLACEMENTS = mapOf(
        "Iunch" to "lunch",
        "vV" to "vv",
        "VV" to "vv",
        "vy" to "vv",
    )
    private val HIDDEN_TOKEN_SPLIT_TRIGGERS = setOf('#', '@', '$', '&', '+', '/', '<', '>', '{', '}', '[', ']', '\\', '_', '|')

    private fun Int.luma(): Int {
        return ((Color.red(this) * 299) + (Color.green(this) * 587) + (Color.blue(this) * 114)) / 1000
    }
}

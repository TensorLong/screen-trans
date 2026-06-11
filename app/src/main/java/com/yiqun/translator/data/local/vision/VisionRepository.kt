package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.Lifecycle
import com.yiqun.translator.data.local.vision.model.Char
import com.yiqun.translator.extensions._cutDecimal
import com.yiqun.translator.extensions._unionWith
import com.yiqun.translator.extensions.isValid
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.data.local.vision.model.Line
import com.yiqun.translator.data.local.vision.model.Paragraph
import com.yiqun.translator.data.local.vision.model.Transaction
import com.yiqun.translator.data.local.vision.model.VisionResponse
import com.yiqun.translator.data.local.vision.model.VisionSingleLineText
import com.yiqun.translator.data.local.vision.model.Word
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Collections
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private const val TRACE_VISION_LOGS = false
private const val SYMBOL_GAP_WORD_SPLIT_FONT_HEIGHT_RATIO = 6.0
private const val HIDDEN_TOKEN_WORD_SPLIT_FONT_HEIGHT_RATIO = 0.20

enum class AutoRecognitionPolicy {
    FULL,
    LATIN_FIRST,
}

private data class RecognizedSourceLineWords(
    val words: List<Word>,
    val boundingBox: Rect,
)

@Singleton
class VisionRepository @Inject constructor() {

    private val TAG = javaClass.simpleName

    private val textRecognitionClient: MyTextRecognizer = MyTextRecognizer(TextRecognizerType.TEXT)

    private val chineseRecognitionClient: MyTextRecognizer = MyTextRecognizer(TextRecognizerType.CHINESE)

    private val koreanRecognitionClient: MyTextRecognizer = MyTextRecognizer(TextRecognizerType.KOREAN)

    private val japaneseRecognitionClient: MyTextRecognizer = MyTextRecognizer(TextRecognizerType.JAPANESE)

    private val devanagariRecognitionClient: MyTextRecognizer = MyTextRecognizer(TextRecognizerType.DEVANAGARI)

    /**
     */
    private var WORD_AXIS_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO by Delegates.notNull<Double>()

    /**
     */
    private var WORD_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT by Delegates.notNull<Double>()

    /**
     */
    private var LINE_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO by Delegates.notNull<Double>()

    /**
     */
    private var LINE_WRITE_DIRECTION_OVERLAP_MINIMUM_RATIO by Delegates.notNull<Double>()

    /**
     */
    private var LINE_FONT_HEIGHT_COLOR_SPACING_AFFINITY_LIMIT by Delegates.notNull<Double>()

    /**
     */
    private var LINE_AXIS_HEIGHT_SIMILARITY_MINIMUM_RATIO by Delegates.notNull<Double>()

    /**
     */
    private var LINE_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT by Delegates.notNull<Double>()

    fun addObserver(lifecycle: Lifecycle) {
        textRecognitionClient.addObserver(lifecycle)
        chineseRecognitionClient.addObserver(lifecycle)
        koreanRecognitionClient.addObserver(lifecycle)
        japaneseRecognitionClient.addObserver(lifecycle)
        devanagariRecognitionClient.addObserver(lifecycle)
    }

    suspend fun request(
        bitmap: Bitmap,
        sourceLanguageCode: String,
        coordinateOffsetX: Int = 0,
        coordinateOffsetY: Int = 0,
        autoRecognitionPolicy: AutoRecognitionPolicy = AutoRecognitionPolicy.FULL,
    ): VisionResponse = coroutineScope {
        Timber.tag(TAG).i("#### request() ####  $sourceLanguageCode")
        val inputImage: InputImage = InputImage.fromBitmap(bitmap, 0)

        try {
            val processResults: List<Text?> = processRecognizers(
                inputImage = inputImage,
                sourceLanguageCode = sourceLanguageCode,
                autoRecognitionPolicy = autoRecognitionPolicy,
            )

            val text = selectBestTextResult(processResults, sourceLanguageCode) ?: throw Exception("No text recognized")

            val (detectedLanguageCode, analyzedParagraphs) = withContext(Dispatchers.Default) {
                var _sourceLanguageCode = sourceLanguageCode
                if (sourceLanguageCode == "auto") {
                    _sourceLanguageCode = identifyLanguage(text.text)
                    if (TRACE_VISION_LOGS) Timber.tag(TAG).i("_sourceLanguageCode : $_sourceLanguageCode")
                }

                val isVerticalWriting = detectVerticalWriting(text)
                val writingDirection = Language.writingDirection(_sourceLanguageCode, isVerticalWriting)
                if (writingDirection == WritingDirection.LTR || writingDirection == WritingDirection.RTL) {
                    _sourceLanguageCode to textToParagraphs(bitmap, text, _sourceLanguageCode, writingDirection, coordinateOffsetX, coordinateOffsetY)
                } else {
                    _sourceLanguageCode to textToVerticalParagraphs(bitmap, text, _sourceLanguageCode, writingDirection, coordinateOffsetX, coordinateOffsetY)
                }
            }

            VisionResponse.Success(Transaction(text, detectedLanguageCode, analyzedParagraphs))
        } catch (e: Exception) {
            Timber.tag(TAG).e("Exception message: ${e.message}")
            Timber.tag(TAG).e("Stack trace:")
            e.printStackTrace()

            val cause = e.cause
            if (cause != null) {
                Timber.tag(TAG).e("Cause: ${cause.message}")
                cause.printStackTrace()
            } else {
                Timber.tag(TAG).e("No underlying cause.")
            }

            VisionResponse.Error(e)
        }
    }

    /**
     */
    private fun getRecognizers(sourceLanguageCode: String): List<MyTextRecognizer> {
        return if (sourceLanguageCode == "auto") {
            listOf(
                textRecognitionClient,
                chineseRecognitionClient,
                koreanRecognitionClient,
                japaneseRecognitionClient,
                devanagariRecognitionClient
            )
        } else if (sourceLanguageCode.startsWith("zh")) {
            listOf(chineseRecognitionClient)
        } else if (sourceLanguageCode.startsWith("ko")) {
            listOf(koreanRecognitionClient)
        } else if (sourceLanguageCode.startsWith("ja")) {
            listOf(japaneseRecognitionClient)
        } else if (
            sourceLanguageCode == "mr" ||
            sourceLanguageCode == "sa" ||
            sourceLanguageCode == "hi" ||
            sourceLanguageCode == "ne"
        ) {
            listOf(devanagariRecognitionClient)
        } else {
            listOf(textRecognitionClient)
        }
    }

    private suspend fun processRecognizers(
        inputImage: InputImage,
        sourceLanguageCode: String,
        autoRecognitionPolicy: AutoRecognitionPolicy,
    ): List<Text?> = coroutineScope {
        val recognizers = if (sourceLanguageCode == "auto" && autoRecognitionPolicy == AutoRecognitionPolicy.LATIN_FIRST) {
            listOf(textRecognitionClient)
        } else {
            getRecognizers(sourceLanguageCode)
        }

        val deferredResults: List<Deferred<Text?>> = recognizers.map { recognizer ->
            async {
                try {
                    if (TRACE_VISION_LOGS) Timber.tag(TAG).d("recognizer.detectorType : ${recognizer.type}")
                    val text: Text = recognizer.process(inputImage)
                    if (TRACE_VISION_LOGS) Timber.tag(TAG).d("_processSuspend text : ${text.text}")
                    text
                } catch (e: Exception) {
                    Timber.tag(TAG).e("Error processing text recognition: ${e.message}")
                    null
                }
            }
        }

        deferredResults.awaitAll()
    }

    private fun selectBestTextResult(
        processResults: List<Text?>,
        sourceLanguageCode: String,
    ): Text? {
        return processResults
            .filterNotNull()
            .maxByOrNull { text ->
                val confidenceScore = text.textBlocks.sumOf { block ->
                    block.lines.sumOf { line ->
                        // ML Kit can report NaN confidence for still images; a single
                        // NaN would poison the sum and make maxByOrNull undefined.
                        line.confidence.toDouble().takeIf { !it.isNaN() } ?: 0.0
                    }
                }
                if (sourceLanguageCode == "auto") {
                    confidenceScore + text.text.scriptCoverageScore()
                } else {
                    confidenceScore
                }
            }
    }

    private suspend fun identifyLanguage(text: String): String = suspendCancellableCoroutine { continuation ->
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                continuation.resume(languageCode)
            }
            .addOnFailureListener { _ ->
                continuation.resume("und")
            }
    }

    /**
     * WritingDirection.LTR, WritingDirection.RTL
     */
    private fun textToParagraphs(
        bitmap: Bitmap,
        text: Text,
        sourceLanguageCode: String,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Paragraph> {
        if (TRACE_VISION_LOGS) Timber.tag(TAG).i("#### textToParagraphs() ####  ${"\n" + text.text}")
        setReferenceConstantValue(false, sourceLanguageCode)

        val textLines = sortTextLines(text.textBlocks.flatMap { textBlock -> textBlock.lines }, writingDirection)

        if (TRACE_VISION_LOGS) {
            textLines.flatMap { it.elements }.forEach {
                Timber.tag(TAG).i("element : ${it.boundingBox} ${it.text} ${(it.boundingBox!!.width().toDouble() / it.boundingBox!!.height())._cutDecimal()}")
            }
        }

        val sourceLineWords = convertTextLinesToWords(bitmap, textLines, writingDirection, coordinateOffsetX, coordinateOffsetY)

        val lines: List<Line> = groupWordsIntoLines(bitmap, sourceLineWords, writingDirection, coordinateOffsetX, coordinateOffsetY)

        if (TRACE_VISION_LOGS) {
            lines.forEach { Timber.tag(TAG).i("groupWordsIntoLines result : ${it.boundingBox}, ${it.representation}, ${it.words}") }
        }

        var paragraphs: List<Paragraph> = groupLinesIntoParagraphs(lines, writingDirection)

        if (TRACE_VISION_LOGS) {
            paragraphs.forEach { Timber.tag(TAG).i("groupLinesIntoParagraphs result : ${it.hasParallelLines} ${it.boundingBox} ${it.representation}") }
        }

        /**
         */
        paragraphs = paragraphs.flatMap { paragraph ->
            val splitParagraphs = detectAndSplitParagraphs(paragraph, writingDirection)
            if (TRACE_VISION_LOGS) {
                splitParagraphs.forEach {
                    Timber.tag(TAG).d("detectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
                }
            }
            val clusterParagraphs = correctDetectAndSplitParagraphs(splitParagraphs, writingDirection)
            if (TRACE_VISION_LOGS) {
                clusterParagraphs.forEach {
                    Timber.tag(TAG).d("correctDetectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
                }
            }
            clusterParagraphs
        }

        if (TRACE_VISION_LOGS) {
            paragraphs.forEach {
                Timber.tag(TAG).i("paragraphs ${it.boundingBox} ${it.representation} ")
            }
        }

        return paragraphs
    }

    /**
     * WritingDirection.TTB_LTR, WritingDirection.TTB_RTL
     */
    private fun textToVerticalParagraphs(
        bitmap: Bitmap,
        text: Text,
        sourceLanguageCode: String,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Paragraph> {
        if (TRACE_VISION_LOGS) Timber.tag(TAG).i("#### textToVerticalParagraphs() ####  ${"\n" + text.text}")

        val textLines: List<Text.Line> =
            text.textBlocks
                .flatMap { textBlock ->
                    textBlock.lines.filter { it.boundingBox.isValid() }
                }.sortedWith(
                    Comparator { line1, line2 ->
                        val rightComparison = line2.boundingBox!!.right.compareTo(line1.boundingBox!!.right)
                        if (rightComparison != 0) rightComparison else line1.boundingBox!!.top.compareTo(line2.boundingBox!!.top)
                    }
                )

        if (TRACE_VISION_LOGS) textLines.forEach { Timber.tag(TAG).i("textLine : ${it.boundingBox}, ${it.text}") }

        val verticalLines = mutableListOf<Line>()
        val horizontalTextLines = mutableListOf<Text.Line>()

        textLines
            .filter { it.boundingBox.isValid() }
            .forEach { textLine ->
                if (textLine.boundingBox!!.height() > textLine.boundingBox!!.width()) {
                    val line = textLine._toLine(writingDirection, coordinateOffsetX, coordinateOffsetY).apply {
                        setFontAndBackgroundColors(bitmap, coordinateOffsetX, coordinateOffsetY)
                    }
                    verticalLines.add(line)
                } else {
                    horizontalTextLines.add(textLine)
                }
            }

        /** ####################################### verticalParagraphs ###################################### */
        setReferenceConstantValue(true, sourceLanguageCode)
        var verticalParagraphs: MutableList<Paragraph> = groupLinesIntoParagraphs(verticalLines, writingDirection).toMutableList()
        if (TRACE_VISION_LOGS) {
            verticalParagraphs.forEach { Timber.tag(TAG).i("groupLinesIntoParagraphs result : ${it.boundingBox} ${it.representation}") }
        }

        /**
         */
        verticalParagraphs = verticalParagraphs.flatMap { paragraph ->
            val splitParagraphs = detectAndSplitParagraphs(paragraph, writingDirection)
            if (TRACE_VISION_LOGS) {
                splitParagraphs.forEach {
                    Timber.tag(TAG).d("detectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
                }
            }
            splitParagraphs
        }.toMutableList()

        /** ####################################### horizontalParagraphs ###################################### */
        setReferenceConstantValue(false, sourceLanguageCode)
        val horizontalWritingDirection = Language.writingDirection(sourceLanguageCode, false)
        val horizontalSortedTextLines = sortTextLines(horizontalTextLines, horizontalWritingDirection)
        val sourceLineWords = convertTextLinesToWords(bitmap, horizontalSortedTextLines, horizontalWritingDirection, coordinateOffsetX, coordinateOffsetY)
        val lines: List<Line> = groupWordsIntoLines(bitmap, sourceLineWords, horizontalWritingDirection, coordinateOffsetX, coordinateOffsetY)
        var horizontalParagraphs: List<Paragraph> = groupLinesIntoParagraphs(lines, horizontalWritingDirection)
        horizontalParagraphs = horizontalParagraphs.flatMap { paragraph ->
            val splitParagraphs = detectAndSplitParagraphs(paragraph, horizontalWritingDirection)
            if (TRACE_VISION_LOGS) {
                splitParagraphs.forEach {
                    Timber.tag(TAG).d("detectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
                }
            }
            val clusterParagraphs = correctDetectAndSplitParagraphs(splitParagraphs, horizontalWritingDirection)
            if (TRACE_VISION_LOGS) {
                clusterParagraphs.forEach {
                    Timber.tag(TAG).d("correctDetectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
                }
            }
            clusterParagraphs
        }

        if (TRACE_VISION_LOGS) {
            verticalParagraphs.forEach { Timber.tag(TAG).i("verticalParagraphs : ${it.boundingBox} ${it.representation}") }
            horizontalParagraphs.forEach { Timber.tag(TAG).i("horizontalParagraphs : ${it.boundingBox} ${it.representation}") }
        }

        verticalParagraphs.addAll(horizontalParagraphs)

        return verticalParagraphs
    }

    /**
     */
    private fun sortTextLines(textLines: List<Text.Line>, writingDirection: WritingDirection): List<Text.Line> {
        return when (writingDirection) {
            WritingDirection.LTR -> {
                textLines
                    .filter { it.boundingBox.isValid() }
                    .sortedWith(
                        Comparator { line1, line2 ->
                            compareHorizontalTextLineOrder(line1.boundingBox!!, line2.boundingBox!!, rtl = false)
                        }
                    )
            }

            WritingDirection.RTL -> {
                textLines
                    .filter { it.boundingBox.isValid() }
                    .sortedWith(
                        Comparator { line1, line2 ->
                            compareHorizontalTextLineOrder(line1.boundingBox!!, line2.boundingBox!!, rtl = true)
                        }
                    )
            }

            WritingDirection.TTB_LTR -> {
                textLines
                    .filter { it.boundingBox.isValid() }
                    .filter { it.boundingBox!!.width() < it.boundingBox!!.height() }
                    .sortedWith(
                        Comparator { line1, line2 ->
                            val leftComparison = line1.boundingBox!!.left.compareTo(line2.boundingBox!!.left)
                            if (leftComparison != 0) leftComparison else line1.boundingBox!!.top.compareTo(line2.boundingBox!!.top)
                        }
                    )
            }

            WritingDirection.TTB_RTL -> {
                textLines
                    .filter { it.boundingBox.isValid() }
                    .filter { it.boundingBox!!.width() < it.boundingBox!!.height() }
                    .sortedWith(
                        Comparator { line1, line2 ->
                            val rightComparison = line2.boundingBox!!.right.compareTo(line1.boundingBox!!.right)
                            if (rightComparison != 0) rightComparison else line1.boundingBox!!.top.compareTo(line2.boundingBox!!.top)
                        }
                    )
            }
        }
    }

    private fun compareHorizontalTextLineOrder(first: Rect, second: Rect, rtl: Boolean): Int {
        val averageHeight = (first.height() + second.height()).toDouble() / 2.0
        val sameBaselineBand = averageHeight > 0.0 && abs(first.centerY() - second.centerY()) <= averageHeight * 0.72
        if (sameBaselineBand) {
            return if (rtl) {
                second.right.compareTo(first.right)
            } else {
                first.left.compareTo(second.left)
            }
        }
        return first.top.compareTo(second.top)
    }

    /**
     */
    private fun convertTextLinesToWords(
        bitmap: Bitmap,
        textLines: List<Text.Line>,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<RecognizedSourceLineWords> {
        val sourceLineWords = ArrayList<RecognizedSourceLineWords>(textLines.size)
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        for (textLine in textLines) {
            val textLineBoundingBox = textLine.boundingBox ?: continue
            val words = ArrayList<Word>(textLine.elements.size)
            for (element in textLine.elements) {
                element.boundingBox?.let { boundingBox ->
                    val left = if (boundingBox.left < 0) 0 else boundingBox.left
                    val top = if (boundingBox.top < 0) 0 else boundingBox.top
                    val right = if (boundingBox.right > bitmapWidth) bitmapWidth else boundingBox.right
                    val bottom = if (boundingBox.bottom > bitmapHeight) bitmapHeight else boundingBox.bottom

                    val correctedBoundingBox = VisionCoordinateMapper.toScreenRect(
                        rectOf(left, top, right, bottom),
                        coordinateOffsetX,
                        coordinateOffsetY,
                    )

                    if (correctedBoundingBox.width() > 0 && correctedBoundingBox.height() > 0) {
                        var symbolTextChanged = false
                        val allowGlyphNormalization = bitmap.config == Bitmap.Config.ARGB_8888
                        val normalizeSymbols = allowGlyphNormalization &&
                                LatinOcrGlyphNormalizer.mayNeedSymbolNormalization(element.text)
                        val chars = element.symbols
                            .filter { it.boundingBox.isValid() }
                            .mapIndexed { symbolIndex, symbol ->
                                val localBoundingBox = symbol.boundingBox!!
                                val normalizedText = if (normalizeSymbols) {
                                    LatinOcrGlyphNormalizer.normalizeSymbol(
                                        bitmap = bitmap,
                                        localBoundingBox = localBoundingBox,
                                        text = symbol.text,
                                        rawElementText = element.text,
                                        symbolIndex = symbolIndex,
                                    )
                                } else {
                                    symbol.text
                                }
                                if (normalizedText != symbol.text) {
                                    symbolTextChanged = true
                                }
                                Char(
                                    VisionCoordinateMapper.toScreenRect(localBoundingBox, coordinateOffsetX, coordinateOffsetY),
                                    normalizedText,
                                    writingDirection
                                )
                            }

                        if (chars.isNotEmpty()) {
                            val needsElementNormalization = allowGlyphNormalization && (
                                    symbolTextChanged ||
                                            LatinOcrGlyphNormalizer.needsElementNormalization(element.text) ||
                                            LatinOcrGlyphNormalizer.mayRecoverMissingSeparator(element.text)
                                    )
                            val normalizedElementText = if (needsElementNormalization) {
                                LatinOcrGlyphNormalizer.normalizeElementText(
                                    rawText = element.text,
                                    normalizedSymbolText = buildString {
                                        chars.forEach { append(it.representation) }
                                    },
                                    bitmap = bitmap,
                                    elementBoundingBox = rectOf(left, top, right, bottom),
                                    symbolBoundingBoxes = element.symbols
                                        .mapNotNull { it.boundingBox }
                                        .filter { it.isValid() },
                                )
                            } else {
                                element.text
                            }
                            words.addAll(
                                splitElementIntoWords(
                                    elementBoundingBox = correctedBoundingBox,
                                    elementText = normalizedElementText,
                                    chars = chars,
                                    writingDirection = writingDirection,
                                    allowHiddenTokenWordSplit = allowGlyphNormalization &&
                                            LatinOcrGlyphNormalizer.mayNeedHiddenTokenWordSplit(element.text),
                                )
                            )
                        }
                    }
                }
            }
            if (words.isNotEmpty()) {
                if (words.size > 1) {
                    sortWordsInsideRecognizedSourceLine(words, writingDirection)
                }
                val normalizedWords = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                    mergeTokenFragments(
                        bitmap = bitmap,
                        words = mergeHiddenSeparatorWords(
                            bitmap = bitmap,
                            words = words,
                            writingDirection = writingDirection,
                            coordinateOffsetX = coordinateOffsetX,
                            coordinateOffsetY = coordinateOffsetY,
                        ),
                        writingDirection = writingDirection,
                        coordinateOffsetX = coordinateOffsetX,
                        coordinateOffsetY = coordinateOffsetY,
                    )
                } else {
                    words
                }
                sourceLineWords.add(
                    RecognizedSourceLineWords(
                        words = normalizedWords,
                        boundingBox = VisionCoordinateMapper.toScreenRect(
                            textLineBoundingBox,
                            coordinateOffsetX,
                            coordinateOffsetY,
                        ),
                    )
                )
            }
        }
        return sourceLineWords
    }

    private fun sortWordsInsideRecognizedSourceLine(
        words: MutableList<Word>,
        writingDirection: WritingDirection,
    ) {
        when (writingDirection) {
            WritingDirection.LTR -> words.sortBy { it.boundingBox.left }
            WritingDirection.RTL -> words.sortByDescending { it.boundingBox.right }
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> words.sortBy { it.boundingBox.top }
        }
    }

    private fun mergeHiddenSeparatorWords(
        bitmap: Bitmap,
        words: List<Word>,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Word> {
        if (writingDirection != WritingDirection.LTR || words.size < 2) return words

        val merged = ArrayList<Word>(words.size)
        var index = 0
        while (index < words.size) {
            val current = words[index]
            val next = words.getOrNull(index + 1)
            val separator = if (next != null && canMergeWithHiddenSeparator(current, next)) {
                val gap = Rect(
                    current.boundingBox.right - coordinateOffsetX,
                    (min(current.boundingBox.top, next.boundingBox.top) - coordinateOffsetY),
                    next.boundingBox.left - coordinateOffsetX,
                    (maxOf(current.boundingBox.bottom, next.boundingBox.bottom) - coordinateOffsetY +
                            (maxOf(current.boundingBox.height(), next.boundingBox.height()) * 0.35f).roundToInt()),
                )
                LatinOcrGlyphNormalizer.detectSeparator(bitmap, gap)
            } else {
                null
            }

            if (separator != null) {
                val separatorBoundingBox = Rect(
                    current.boundingBox.right,
                    min(current.boundingBox.top, next!!.boundingBox.top),
                    next.boundingBox.left,
                    maxOf(current.boundingBox.bottom, next.boundingBox.bottom),
                )
                merged.add(
                    Word(
                        boundingBox = current.boundingBox._unionWith(next.boundingBox),
                        representation = current.representation + separator + next.representation,
                        writingDirection = writingDirection,
                        chars = current.chars + Char(separatorBoundingBox, separator, writingDirection) + next.chars,
                    )
                )
                index += 2
            } else {
                merged.add(current)
                index += 1
            }
        }
        return merged
    }

    private fun mergeTokenFragments(
        bitmap: Bitmap,
        words: List<Word>,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Word> {
        if (writingDirection != WritingDirection.LTR || words.size < 2) {
            return words.map { it.withNormalizedTokenText() }
        }

        val merged = ArrayList<Word>(words.size)
        for (word in words.map { it.withNormalizedTokenText() }) {
            val previous = merged.lastOrNull()
            if (previous != null && shouldMergeTokenFragments(previous, word)) {
                merged[merged.lastIndex] = previous.mergeWith(word)
            } else {
                merged.add(word)
            }
        }
        return merged
    }

    private fun shouldMergeTokenFragments(previous: Word, next: Word): Boolean {
        val left = previous.representation
        val right = next.representation
        if (left.isBlank() || right.isBlank()) return false

        val gap = next.boundingBox.left - previous.boundingBox.right
        val averageFontHeight = previous.getAverageFontHeight(next)
        val gapRatio = if (averageFontHeight <= 0.0) 1.0 else gap.toDouble() / averageFontHeight

        if (left.isUrlFragmentBefore(right) || left.isEmailFragmentBefore(right)) return true
        if (left.isHandleMarkerBefore(right) || left.isHandleNameBeforeNumericSuffix(right)) return true
        if (left.endsWith("-0") && right.firstOrNull()?.isDigit() == true && right.contains('@')) return true
        if (right.startsWith("?") && (left.contains('/') || left.contains('.'))) return true
        if (left.endsWith(".") && right.isDomainOrPathTail() && left.contains('.')) return true

        if (gapRatio > 0.48) return false
        return left.isCompactAmbiguousTokenFragment() && right.isCompactAmbiguousTokenFragment()
    }

    private fun Word.withNormalizedTokenText(): Word {
        val normalized = LatinOcrGlyphNormalizer.normalizeTokenText(representation)
        return if (normalized == representation) {
            this
        } else {
            copy(representation = normalized)
        }
    }

    private fun Word.mergeWith(next: Word): Word {
        val mergedRepresentation = if (representation.isHandleNameBeforeNumericSuffix(next.representation)) {
            "${representation}_${next.representation}"
        } else {
            representation + next.representation
        }
        return Word(
            boundingBox = boundingBox._unionWith(next.boundingBox),
            representation = LatinOcrGlyphNormalizer.normalizeTokenText(mergedRepresentation),
            writingDirection = writingDirection,
            chars = chars + next.chars,
        )
    }

    private fun canMergeWithHiddenSeparator(current: Word, next: Word): Boolean {
        if (current.representation.length != 1 || next.representation.length != 1) return false
        val left = current.representation[0]
        val right = next.representation[0]
        if ((!left.isLetterOrDigit()) || (!right.isLetterOrDigit())) return false
        if (left.isLetter() && !left.isUpperCase()) return false
        if (right.isLetter() && !right.isUpperCase()) return false
        val gap = next.boundingBox.left - current.boundingBox.right
        if (gap <= 0) return false
        return gap <= maxOf(current.boundingBox.height(), next.boundingBox.height())
    }

    private fun splitElementIntoWords(
        elementBoundingBox: Rect,
        elementText: String,
        chars: List<Char>,
        writingDirection: WritingDirection,
        allowHiddenTokenWordSplit: Boolean,
    ): List<Word> {
        if (chars.size <= 1) {
            return listOf(Word(elementBoundingBox, elementText, writingDirection, chars))
        }

        val sortedChars = chars.sortedWith(VisionSingleLineText.getComparator(writingDirection))
        val groups = mutableListOf<MutableList<Char>>()
        var currentGroup = mutableListOf<Char>()

        sortedChars.forEach { char ->
            val previous = currentGroup.lastOrNull()
            if (previous != null) {
                val shouldSplit = if (allowHiddenTokenWordSplit) {
                    shouldSplitSymbolGapWithHiddenTokenBoundary(currentGroup, previous, char)
                } else {
                    shouldSplitSymbolGap(previous, char)
                }
                if (shouldSplit) {
                    groups.add(currentGroup)
                    currentGroup = mutableListOf()
                }
            }
            currentGroup.add(char)
        }
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        if (groups.size == 1) {
            return listOf(Word(elementBoundingBox, elementText, writingDirection, chars))
        }

        val compactElementText = elementText.filterNot { it.isWhitespace() }
        val canSliceNormalizedText = compactElementText.length == sortedChars.sumOf { it.representation.length }
        var normalizedTextOffset = 0

        return groups.map { group ->
            val groupTextLength = group.sumOf { it.representation.length }
            val representation = if (canSliceNormalizedText) {
                compactElementText.substring(normalizedTextOffset, normalizedTextOffset + groupTextLength)
            } else {
                group.joinToString(separator = "") { it.representation }
            }
            normalizedTextOffset += groupTextLength

            Word(
                boundingBox = group.map { it.boundingBox }.reduce { acc, rect -> acc._unionWith(rect) },
                representation = representation,
                writingDirection = writingDirection,
                chars = group,
            )
        }
    }

    private fun shouldSplitSymbolGap(previous: Char, next: Char): Boolean {
        val averageFontHeight = previous.getAverageFontHeight(next)
        if (averageFontHeight <= 0.0) return false
        val gapRatio = next.getWriteDirectionDistance(previous).toDouble() / averageFontHeight
        return gapRatio >= SYMBOL_GAP_WORD_SPLIT_FONT_HEIGHT_RATIO
    }

    private fun shouldSplitSymbolGapWithHiddenTokenBoundary(
        currentGroup: List<Char>,
        previous: Char,
        next: Char,
    ): Boolean {
        val averageFontHeight = previous.getAverageFontHeight(next)
        if (averageFontHeight <= 0.0) return false
        val gapRatio = next.getWriteDirectionDistance(previous).toDouble() / averageFontHeight
        if (gapRatio >= SYMBOL_GAP_WORD_SPLIT_FONT_HEIGHT_RATIO) return true
        if (gapRatio < HIDDEN_TOKEN_WORD_SPLIT_FONT_HEIGHT_RATIO) return false
        val nextChar = next.representation.singleOrNull() ?: return false
        if (!nextChar.isLowerCase()) return false
        val prefix = currentGroup.joinToString(separator = "") { it.representation }
        return prefix.isLikelyTokenPrefixBeforeWord()
    }

    private fun String.isLikelyTokenPrefixBeforeWord(): Boolean {
        if (length !in 2..12 || any { it.isWhitespace() }) return false
        val lowercaseLetters = filter { it.isLowerCase() }
        if (lowercaseLetters.isNotEmpty() && !(lowercaseLetters.all { it == 'l' } && any { it.isDigit() })) {
            return false
        }
        if (any { it.isDigit() || it in "#@$&+/<>{}[]\\_|" }) return true
        return length <= 4 && all { it.isUpperCase() }
    }

    /**
     */
    private fun groupWordsIntoLines(
        bitmap: Bitmap,
        sourceLineWords: List<RecognizedSourceLineWords>,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Line> {
        val lines = mutableListOf<Line>()

        sourceLineWords.forEach { recognizedSourceLine ->
            if (recognizedSourceLine.words.size > 1) {
                val sourceOrderedWords = when (writingDirection) {
                    WritingDirection.LTR -> recognizedSourceLine.words.sortedBy { it.boundingBox.left }
                    WritingDirection.RTL -> recognizedSourceLine.words.sortedByDescending { it.boundingBox.right }
                    WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> recognizedSourceLine.words.sortedBy { it.boundingBox.top }
                }
                val existingLine = lines.firstOrNull {
                    it.acceptsSplitSourceLineFragment(recognizedSourceLine.boundingBox, writingDirection)
                }
                if (existingLine != null) {
                    sourceOrderedWords.forEach(existingLine::addWord)
                } else {
                    lines.add(Line(sourceOrderedWords.toMutableList(), writingDirection))
                }
                return@forEach
            }

            val splitSourceLine = lines.firstOrNull {
                it.acceptsSplitSourceLineFragment(recognizedSourceLine.boundingBox, writingDirection)
            }
            var sourceLine: Line? = splitSourceLine

            recognizedSourceLine.words
                .forEach { word ->
                    sourceLine?.let { line ->
                        if (line === splitSourceLine || line.acceptsRecognizedLineWord(word)) {
                            line.addWord(word)
                            return@forEach
                        }
                    }

                    var addedToLine = false

                    for (line in lines) {
                        val closestWord = line.words.minByOrNull { it.getWriteDirectionDistance(word) }!!

                        val averageFontHeight: Double = word.getAverageFontHeight(closestWord)

                        val axisDistance = word.getAxisDistance(closestWord)

                        if (axisDistance > averageFontHeight) break

                        val writeDirectionDistance: Double = word.getWriteDirectionDistance(closestWord).toDouble()

                        val writeDirectionDistanceFontHeightRatio: Double = writeDirectionDistance / averageFontHeight

                        // [condition 0]
                        if (writeDirectionDistanceFontHeightRatio <= WORD_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT) { // 0.63
                            val axisSimilarityRatio = word.getAxisSimilarityRatio(closestWord)

                            val fontHeightSimilarityRatio = word.getFontHeightSimilarityRatio(closestWord)

                            val axisFontHeightSimilarityRatio = axisSimilarityRatio * fontHeightSimilarityRatio

                            // [condition 0-0]
                            if (axisFontHeightSimilarityRatio >= WORD_AXIS_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO) { // 0.85
                                if (TRACE_VISION_LOGS) {
                                    Timber.tag(TAG).d(
                                        "groupWordsIntoLines add 0-0 : "
                                                + "${writeDirectionDistance._cutDecimal()}, "
                                                + "${averageFontHeight._cutDecimal()}, "
                                                + "${writeDirectionDistanceFontHeightRatio._cutDecimal()}, "
                                                + "${axisSimilarityRatio._cutDecimal()}, "
                                                + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${axisFontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${line.representation}(${line.boundingBox}) + ${word.representation}(${word.boundingBox})"
                                    )
                                }
                                line.addWord(word)
                                if (sourceLine == null) {
                                    sourceLine = line
                                }
                                addedToLine = true
                                break
                            }
                            // [condition 0-1]
                            else {
                                if (TRACE_VISION_LOGS) {
                                    Timber.tag(TAG).v(
                                        "groupWordsIntoLines drop 0-1 : "
                                                + "${writeDirectionDistance._cutDecimal()}, "
                                                + "${averageFontHeight._cutDecimal()}, "
                                                + "${writeDirectionDistanceFontHeightRatio._cutDecimal()}, "
                                                + "${axisSimilarityRatio._cutDecimal()}, "
                                                + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${axisFontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${line.representation}(${line.boundingBox}) + ${word.representation}(${word.boundingBox})"
                                    )
                                }
                            }
                        }
                        // [condition 1]
                        else {
                            if (TRACE_VISION_LOGS) {
                                Timber.tag(TAG).v(
                                    "groupWordsIntoLines drop 1 : "
                                            + "${writeDirectionDistance._cutDecimal()}, "
                                            + "${averageFontHeight._cutDecimal()}, "
                                            + "${writeDirectionDistanceFontHeightRatio._cutDecimal()}, "
                                            + "${line.representation}(${line.boundingBox}) + ${word.representation}(${word.boundingBox})"
                                )
                            }
                        }
                    }

                    if (!addedToLine) {
                        val splitLine = lines.firstOrNull { it.acceptsSplitSourceLineFragment(word) }
                        if (splitLine != null) {
                            splitLine.addWord(word)
                            sourceLine = splitLine
                        } else {
                            val newLine = Line(mutableListOf(word), writingDirection)
                            lines.add(0, newLine)
                            if (sourceLine == null) {
                                sourceLine = newLine
                            }
                        }
                    }
                }
        }

        lines.forEach {
            it.setFontAndBackgroundColors(bitmap, coordinateOffsetX, coordinateOffsetY)
        }

        return lines
    }

    private fun Line.acceptsRecognizedLineWord(word: Word): Boolean {
        val closestWord = words.minByOrNull { it.getWriteDirectionDistance(word) } ?: return false
        return RecognizedLineWordAffinityPolicy.accepts(
            axisSimilarityRatio = word.getAxisSimilarityRatio(closestWord),
            fontHeightSimilarityRatio = word.getFontHeightSimilarityRatio(closestWord),
        )
    }

    private fun Line.acceptsSplitSourceLineFragment(word: Word): Boolean {
        val closestWord = words.minByOrNull { it.getWriteDirectionDistance(word) } ?: return false
        val averageFontHeight = word.getAverageFontHeight(closestWord)
        if (averageFontHeight <= 0.0) return false
        val writeDirectionDistanceFontHeightRatio = word.getWriteDirectionDistance(closestWord).toDouble() / averageFontHeight
        return RecognizedLineWordAffinityPolicy.acceptsSplitSourceLineFragment(
            axisSimilarityRatio = word.getAxisSimilarityRatio(closestWord),
            fontHeightSimilarityRatio = word.getFontHeightSimilarityRatio(closestWord),
            writeDirectionDistanceFontHeightRatio = writeDirectionDistanceFontHeightRatio,
        )
    }

    private fun Line.acceptsSplitSourceLineFragment(
        sourceLineBoundingBox: Rect,
        writingDirection: WritingDirection,
    ): Boolean {
        val sourceFontHeight = sourceLineBoundingBox.fontHeightFor(writingDirection)
        val averageFontHeight = (fontHeight + sourceFontHeight) / 2.0
        if (averageFontHeight <= 0.0) return false
        val axisDistance = boundingBox.axisDistance(sourceLineBoundingBox, writingDirection)
        val axisSimilarityRatio = averageFontHeight / (averageFontHeight + axisDistance)
        val fontHeightSimilarityRatio = sourceFontHeight.divideByLarger(fontHeight)
        val writeDirectionDistanceFontHeightRatio =
            boundingBox.writeDirectionDistance(sourceLineBoundingBox, writingDirection).toDouble() / averageFontHeight
        return RecognizedLineWordAffinityPolicy.acceptsSplitSourceLineFragment(
            axisSimilarityRatio = axisSimilarityRatio,
            fontHeightSimilarityRatio = fontHeightSimilarityRatio,
            writeDirectionDistanceFontHeightRatio = writeDirectionDistanceFontHeightRatio,
        )
    }

    private fun Rect.fontHeightFor(writingDirection: WritingDirection): Double {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> height().toDouble()
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> width().toDouble()
        }
    }

    private fun Rect.axisDistance(other: Rect, writingDirection: WritingDirection): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> abs(centerY() - other.centerY())
            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> abs(centerX() - other.centerX())
        }
    }

    private fun Rect.writeDirectionDistance(other: Rect, writingDirection: WritingDirection): Int {
        return when (writingDirection) {
            WritingDirection.LTR, WritingDirection.RTL -> when {
                other.left > right -> other.left - right
                left > other.right -> left - other.right
                else -> 0
            }

            WritingDirection.TTB_LTR, WritingDirection.TTB_RTL -> when {
                other.top > bottom -> other.top - bottom
                top > other.bottom -> top - other.bottom
                else -> 0
            }
        }
    }

    private fun Double.divideByLarger(other: Double): Double {
        val larger = maxOf(this, other)
        return if (larger == 0.0) 0.0 else minOf(this, other) / larger
    }

    /**
     */
    private fun groupLinesIntoParagraphs(lines: List<Line>, writingDirection: WritingDirection): List<Paragraph> {
        val paragraphs = mutableListOf<Paragraph>()
        val assignedLines = Collections.newSetFromMap(IdentityHashMap<Line, Boolean>())

        lines
            .sortedWith(VisionSingleLineText.getComparator(writingDirection))
            .forEach { line ->
                if (line in assignedLines) return@forEach

                var addedToParagraph = false

                for (paragraph in paragraphs) {
                    val isWriteDirectionOverlaps = line.isWriteDirectionOverlaps(paragraph)

                    val isLineReturnDirectionOverlaps = line.isLineReturnDirectionOverlaps(paragraph)

                    if (isWriteDirectionOverlaps && isLineReturnDirectionOverlaps) {
                        if (TRACE_VISION_LOGS) {
                            Timber.tag(TAG).d(
                                "groupLinesIntoParagraphs add 0 : "
                                        + "${paragraph.boundingBox}, "
                                        + "${paragraph.representation}(${paragraph.height}), "
                                        + "${line.boundingBox}, "
                                        + "${line.representation}(${line.height})"
                            )
                        }

                        paragraph.addLine(line)
                        assignedLines.add(line)
                        addedToParagraph = true
                        break
                    }

                    val closestLine = paragraph.lines.lastOrNull() ?: continue

                    val lineSpacing = closestLine.getLineReturnDirectionDistance(line)

                    val averageFontHeight: Double = line.getAverageFontHeight(closestLine)

                    val fontHeightSimilarityRatio = line.getFontHeightSimilarityRatio(closestLine)

                    if (
                        acceptsWrappedSentenceContinuation(
                            previousLine = closestLine,
                            nextLine = line,
                            averageFontHeight = averageFontHeight,
                            fontHeightSimilarityRatio = fontHeightSimilarityRatio,
                        )
                    ) {
                        paragraph.addLine(line)
                        assignedLines.add(line)
                        addedToParagraph = true
                        break
                    }

                    if (lineSpacing > closestLine.fontHeight * 1.6) continue

                    if (fontHeightSimilarityRatio >= LINE_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO) {
                        if (isWriteDirectionOverlaps) {
                            val writeDirectionOverlapRatio: Double = line.getWriteDirectionOverlapRatio(paragraph)

                            if (writeDirectionOverlapRatio >= LINE_WRITE_DIRECTION_OVERLAP_MINIMUM_RATIO) {
                                val colorSimilarity = closestLine.getColorSimilarity(line)

                                if (TRACE_VISION_LOGS) {
                                    Timber.tag(TAG).d(
                                        "${String.format("#%08X", closestLine.fontColor)} "
                                                + "${String.format("#%08X", closestLine.backgroundColor)} "
                                                + "${String.format("#%08X", line.fontColor)} "
                                                + "${String.format("#%08X", line.backgroundColor)} "
                                    )
                                }

                                val lineSpacingAffinity = min(1.0, 1.0 / (lineSpacing.toDouble() / averageFontHeight))

                                val fontHeightColorLineSpacingAffinity = fontHeightSimilarityRatio * colorSimilarity * lineSpacingAffinity

                                // [condition 0-0-0-0] 
                                if (fontHeightColorLineSpacingAffinity >= LINE_FONT_HEIGHT_COLOR_SPACING_AFFINITY_LIMIT) {
                                    if (TRACE_VISION_LOGS) {
                                        Timber.tag(TAG).d(
                                            "groupLinesIntoParagraphs add 0-0-0-0 : "
                                                    + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                    + "${colorSimilarity._cutDecimal()}, "
                                                    + "${lineSpacingAffinity._cutDecimal()}, "
                                                    + "*${fontHeightColorLineSpacingAffinity._cutDecimal()}, "
                                                    + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height})"
                                        )
                                    }
                                    paragraph.addLine(line)
                                    assignedLines.add(line)
                                    addedToParagraph = true
                                    break
                                }
                                // [condition 0-0-0-1] 
                                else {
                                    if (TRACE_VISION_LOGS) {
                                        Timber.tag(TAG).v(
                                            "groupLinesIntoParagraphs drop 0-0-0-1 : "
                                                    + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                    + "${colorSimilarity._cutDecimal()}, "
                                                    + "${lineSpacingAffinity._cutDecimal()}, "
                                                    + "*${fontHeightColorLineSpacingAffinity._cutDecimal()}, "
                                                    + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height})"
                                        )
                                    }
                                }
                            }
                            // [condition 0-0-1] 
                            else {
                                if (TRACE_VISION_LOGS) {
                                    Timber.tag(TAG).v(
                                        "groupLinesIntoParagraphs drop 0-0-1 : "
                                                + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${writeDirectionOverlapRatio._cutDecimal()}, "
                                                + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height})"
                                    )
                                }
                            }
                        }

                        else if (isLineReturnDirectionOverlaps) {
                            val axisSimilarityRatio = line.getAxisSimilarityRatio(closestLine)

                            val axisHeightSimilarityRatio = axisSimilarityRatio * fontHeightSimilarityRatio

                            // [condition 0-1-0]
                            if (axisHeightSimilarityRatio >= LINE_AXIS_HEIGHT_SIMILARITY_MINIMUM_RATIO) {
                                val writeDirectionDistance: Double = line.getWriteDirectionDistance(closestLine).toDouble()

                                val writeDirectionDistanceFontHeightRatio: Double = writeDirectionDistance / averageFontHeight

                                // [condition 0-1-0-0]
                                if (writeDirectionDistanceFontHeightRatio <= LINE_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT) {
                                    if (TRACE_VISION_LOGS) {
                                        Timber.tag(TAG).d(
                                            "groupLinesIntoParagraphs add 0-1-0-0 : "
                                                    + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                    + "${axisSimilarityRatio._cutDecimal()}, "
                                                    + "${axisHeightSimilarityRatio._cutDecimal()}, "
                                                    + "${writeDirectionDistance._cutDecimal()}, "
                                                    + "${writeDirectionDistanceFontHeightRatio._cutDecimal()}, "
                                                    + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                                        )
                                    }

                                    if (writingDirection == WritingDirection.LTR) {
                                        if (closestLine.boundingBox.right < line.boundingBox.right) {
                                            paragraph.addLine(line)
                                        } else {
                                            paragraph.addLine(paragraph.lines.size - 1, line)
                                        }
                                    } else {
                                        if (line.boundingBox.left < closestLine.boundingBox.left) {
                                            paragraph.addLine(line)
                                        } else {
                                            paragraph.addLine(paragraph.lines.size - 1, line)
                                        }
                                    }
                                    assignedLines.add(line)
                                    paragraph.hasParallelLines = true
                                    addedToParagraph = true
                                    break
                                }
                                // [condition 0-1-0-1]
                                else {
                                    if (TRACE_VISION_LOGS) {
                                        Timber.tag(TAG).v(
                                            "groupLinesIntoParagraphs drop 0-1-0-1 : "
                                                    + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                    + "${axisSimilarityRatio._cutDecimal()}, "
                                                    + "${axisHeightSimilarityRatio._cutDecimal()}, "
                                                    + "${writeDirectionDistance._cutDecimal()}, "
                                                    + "${writeDirectionDistanceFontHeightRatio}, "
                                                    + "${LINE_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT}, "
                                                    + "${(writeDirectionDistanceFontHeightRatio <= LINE_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT)}, "
                                                    + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                                        )
                                    }
                                }
                            }
                            // [condition 0-1-1]
                            else {
                                if (TRACE_VISION_LOGS) {
                                    Timber.tag(TAG).v(
                                        "groupLinesIntoParagraphs drop 0-1-1 : "
                                                + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${axisSimilarityRatio._cutDecimal()}, "
                                                + "${axisHeightSimilarityRatio._cutDecimal()}, "
                                                + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                                    )
                                }
                            }
                        }
                        // [condition 0-2]
                        else {
                            if (TRACE_VISION_LOGS) {
                                Timber.tag(TAG).v(
                                    "groupLinesIntoParagraphs drop 0-2 : "
                                            + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                            + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                                )
                            }
                        }
                    }
                    // [condition 1]
                    else {
                        if (TRACE_VISION_LOGS) {
                            Timber.tag(TAG).v(
                                "groupLinesIntoParagraphs drop 1 : "
                                        + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                        + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                            )
                        }
                    }
                }

                if (!addedToParagraph) {
                    paragraphs.add(0, Paragraph(mutableListOf(line), writingDirection))
                    assignedLines.add(line)
                }
            }

        return paragraphs
    }

    private fun acceptsWrappedSentenceContinuation(
        previousLine: Line,
        nextLine: Line,
        averageFontHeight: Double,
        fontHeightSimilarityRatio: Double,
    ): Boolean {
        if (averageFontHeight <= 0.0) return false
        if (fontHeightSimilarityRatio < LINE_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO) return false
        if (previousLine.endsWithSentenceTerminal()) return false
        val lineReturnDistance = previousLine.getLineReturnDirectionDistance(nextLine)
        if (lineReturnDistance > averageFontHeight * 2.4) return false
        val startPositionDistance = abs(previousLine.startPosition - nextLine.startPosition)
        return startPositionDistance <= averageFontHeight * 2.5
    }

    private fun Line.endsWithSentenceTerminal(): Boolean {
        val terminals = setOf('.', '?', '!', '。', '።', '।', '།', '؟', ';')
        val text = representation.trim()
        if (text.isEmpty()) return false
        return if (writingDirection == WritingDirection.RTL) {
            text.first() in terminals
        } else {
            text.last() in terminals
        }
    }

    /**
     *
     *        △ △ △ △ △ △ △ △  ○ ○ ○ ○ ○ ○ ○ ○
     *        △ △ △ △ △ △ △ △  ○ ○ ○ ○ ○ ○ ○ ○
     *        △ △ △ △ △ △ △ △  ○ ○ ○ ○ ○ ○ ○ ○
     *        △ △ △ △ △ △ △ △  ○ ○ ○ ○ ○ ○ ○ ○
     *
     *
     */
    private fun detectAndSplitParagraphs(paragraph: Paragraph, writingDirection: WritingDirection): List<Paragraph> {
        if (!paragraph.hasParallelLines || paragraph.lines.size == 1) {
            return listOf(paragraph)
        }

        if (paragraph.areAllInLine()) {
            return listOf(paragraph)
        }

        if (TRACE_VISION_LOGS) Timber.tag(TAG).d("detectAndSplitParagraphs ${paragraph.representation}")

        val lines = paragraph.lines
        val clustersVisited = mutableSetOf<Line>()
        val clusters = mutableListOf<MutableList<Line>>()
        val distanceLimit: Double = paragraph.averageLineHeight()

        fun expandCluster(line: Line, cluster: MutableList<Line>) {
            val neighbors =
                lines.filter {
                    if (it != line) {
                        if (TRACE_VISION_LOGS) {
                            Timber.tag(TAG).i(
                                "Split cluster "
                                        + "$distanceLimit, ${abs(line.startPosition - it.startPosition)}, ${line.boundingBox}, ${line.representation}, ${it.boundingBox}, ${it.representation}"
                            )
                        }
                    }
                    it != line && abs(line.startPosition - it.startPosition) <= distanceLimit
                }

            cluster.add(line)
            clustersVisited.add(line)
            neighbors.forEach {
                if (!clustersVisited.contains(it)) {
                    expandCluster(it, cluster)
                }
            }
        }

        lines.forEach { line ->
            if (!clustersVisited.contains(line)) {
                val cluster = mutableListOf<Line>()
                expandCluster(line, cluster)
                clusters.add(cluster)
            }
        }

        return clusters.map { cluster -> Paragraph(cluster.toMutableList(), writingDirection) }
    }

    /**
     *
     *        ○ ○ ○ ○ ○ ○ ○ ○  ○ ○ ○ ○ ○ ○ ○ ○
     *            ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○
     *                  ○ ○ ○ ○ ○ ○ ○ ○ ○ ○
     *
     *
     *        ○ ○ ○ ○ ○ ○ ○ ○  △ △ △ △ △ △ △ △
     *            ▲ ▲ ▲ ▲ ▲ ▲ ▲ ▲ ▲ ▲ ▲
     *                  ◇ ◇ ◇ ◇ ◇ ◇ ◇ ◇ ◇ ◇
     *
     */
    private fun correctDetectAndSplitParagraphs(paragraphs: List<Paragraph>, writingDirection: WritingDirection): List<Paragraph> {
        val clustersVisited = mutableSetOf<Paragraph>()
        val clusters = mutableListOf<MutableList<Paragraph>>()

        fun expandCluster(paragraph: Paragraph, cluster: MutableList<Paragraph>) {
            val neighbors = paragraphs.filter {
                if (it != paragraph) {
                    if (TRACE_VISION_LOGS) {
                        Timber.tag(TAG)
                            .i("Correct cluster ${paragraph.isWriteDirectionOverlaps(it)}, ${paragraph.boundingBox}, ${paragraph.representation}, ${it.boundingBox}, ${it.representation}")
                    }
                }
                it != paragraph && paragraph.isWriteDirectionOverlaps(it)
            }
            cluster.add(paragraph)
            clustersVisited.add(paragraph)
            neighbors.forEach {
                if (!clustersVisited.contains(it)) {
                    expandCluster(it, cluster)
                }
            }
        }

        paragraphs.forEach { paragraph ->
            if (!clustersVisited.contains(paragraph)) {
                val cluster = mutableListOf<Paragraph>()
                expandCluster(paragraph, cluster)
                clusters.add(cluster)
            }
        }

        fun mergeParagraphs(paragraphs: List<Paragraph>): Paragraph {
            val allLines = paragraphs.flatMap { it.lines }
                .sortedWith(VisionSingleLineText.getComparator(writingDirection))
                .toMutableList()
            return Paragraph(allLines, writingDirection)
        }

        return clusters.map { cluster -> mergeParagraphs(cluster) }
    }

    /**
     */
    private fun detectVerticalWriting(text: Text): Boolean {
        var countGreaterThanOne = 0
        var countLessThanOne = 0

        for (textBlock in text.textBlocks) {
            for (line in textBlock.lines) {
                line.boundingBox?.let {
                    if (it.width() > it.height() && it.height() > 0) {
                        countGreaterThanOne++
                    } else {
                        countLessThanOne++
                    }
                }
            }
        }
        if (TRACE_VISION_LOGS) Timber.tag(TAG).i("isVerticalWriting  $countGreaterThanOne $countLessThanOne")
        return countGreaterThanOne < countLessThanOne
    }

    /**
     */
    private fun setReferenceConstantValue(isVerticalWriting: Boolean, sourceLanguageCode: String) {
        val isNonSpacingLanguage = Language.isNonSpacingLanguage(sourceLanguageCode)
        if (TRACE_VISION_LOGS) {
            Timber.tag(TAG)
                .d("setReferenceConstantValue - sourceLanguageCode: $sourceLanguageCode, isNonSpacingLanguage: $isNonSpacingLanguage, isVerticalWriting: $isVerticalWriting")
        }

        WORD_AXIS_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO = 0.85
        WORD_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT = 0.63
        LINE_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO = 0.68
        LINE_WRITE_DIRECTION_OVERLAP_MINIMUM_RATIO = 0.84
        LINE_FONT_HEIGHT_COLOR_SPACING_AFFINITY_LIMIT = 0.62
        LINE_AXIS_HEIGHT_SIMILARITY_MINIMUM_RATIO = 0.87
        LINE_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO_LIMIT = 0.91

        if (isVerticalWriting) {
            if (isNonSpacingLanguage) {
                if (sourceLanguageCode == "ja") {
                }
            } else {

            }
        } else {
            if (isNonSpacingLanguage) {
                if (sourceLanguageCode == "ja") {
                }
            } else {

            }
        }
    }
}

private fun rectOf(left: Int, top: Int, right: Int, bottom: Int): Rect {
    return Rect().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }
}

private fun String.scriptCoverageScore(): Double {
    var score = 0.0
    forEach { char ->
        score += when (Character.UnicodeBlock.of(char)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES,
            Character.UnicodeBlock.HANGUL_JAMO,
            Character.UnicodeBlock.DEVANAGARI -> 6.0
            else -> 0.0
        }
    }
    return score
}

private fun String.isUrlFragmentBefore(next: String): Boolean {
    if (startsWith("http://") || startsWith("https://") || startsWith("http:/") || startsWith("https:/")) return true
    if (startsWith("htps:/") || startsWith("ttp://")) return true
    if (contains("://") && next.isDomainOrPathTail()) return true
    return false
}

private fun String.isEmailFragmentBefore(next: String): Boolean {
    val hasLocalPart = any { it.isLetterOrDigit() } &&
            (any { it == '.' || it == '_' || it == '-' || it.isDigit() } || length <= 2)
    if (next.startsWith("@")) return hasLocalPart
    if (next.contains('@')) return hasLocalPart
    if (!contains('@')) return false
    if (startsWith("@")) return false
    val domain = substringAfter('@', missingDelimiterValue = "")
    if (domain.any { !(it.isLetterOrDigit() || it == '-' || it == '.') }) return false
    return domain.isNotEmpty() && !domain.contains('.') && next.isDomainOrPathTail()
}

private fun String.isHandleMarkerBefore(next: String): Boolean {
    return (this == "@" || this == "(@") && next.isHandleTail()
}

private fun String.isHandleNameBeforeNumericSuffix(next: String): Boolean {
    if (!startsWith("@") || contains('_')) return false
    if (length < 3 || drop(1).any { !it.isLetterOrDigit() }) return false
    return next.isNumericSuffixFragment()
}

private fun String.isHandleTail(): Boolean {
    if (isEmpty()) return false
    if (first() == '@' || first() == '#') return false
    return first().isLetter() && all { it.isLetterOrDigit() || it == '_' }
}

private fun String.isNumericSuffixFragment(): Boolean {
    if (length !in 2..6) return false
    return all { it.isDigit() || it in "Il|l" } && any { it.isDigit() }
}

private fun String.isDomainOrPathTail(): Boolean {
    if (isEmpty()) return false
    val first = first()
    return first.isLetterOrDigit() || first == '/' || first == '?' || first == '&' || first == '-' || first == '_'
}

private fun String.isCompactAmbiguousTokenFragment(): Boolean {
    if (length !in 1..12 || any { it.isWhitespace() }) return false
    if (!all { it.isLetterOrDigit() || it in "_-:/?.=&" }) return false
    val compact = filter { it.isLetterOrDigit() || it == '|' }
    if (compact.isNotEmpty() && compact.all { it in "0OoOQ1Il|" }) return true
    if (compact.any { it.isLowerCase() && it !in "ol" }) return false
    return any { it.isDigit() || it in "_-:/?.=&" }
}

fun Text.Element._toWord(
    writingDirection: WritingDirection,
    coordinateOffsetX: Int = 0,
    coordinateOffsetY: Int = 0,
): Word {
    val chars = this.symbols.map { symbol ->
        Char(VisionCoordinateMapper.toScreenRect(symbol.boundingBox!!, coordinateOffsetX, coordinateOffsetY), symbol.text, writingDirection)
    }
    return Word(VisionCoordinateMapper.toScreenRect(this.boundingBox!!, coordinateOffsetX, coordinateOffsetY), this.text, writingDirection, chars)
}

fun Text.Line._toLine(
    writingDirection: WritingDirection,
    coordinateOffsetX: Int = 0,
    coordinateOffsetY: Int = 0,
): Line {
    val words = this.elements.map { element ->
        element._toWord(writingDirection, coordinateOffsetX, coordinateOffsetY)
    }.toMutableList()
    return Line(words, writingDirection)
}

package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.Lifecycle
import com.yiqun.translator.data.local.vision.model.Char
import com.yiqun.translator.extensions._cutDecimal
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min
import kotlin.properties.Delegates


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
    ): VisionResponse = coroutineScope {
        Timber.tag(TAG).i("#### request() ####  $sourceLanguageCode")
        val inputImage: InputImage = InputImage.fromBitmap(bitmap, 0)

        try {
            val deferredResults: List<Deferred<Text?>> = getRecognizers(sourceLanguageCode).map { recognizer ->
                async {
                    try {
                        Timber.tag(TAG).d("recognizer.detectorType : ${recognizer.type}")
                        val text: Text = recognizer.process(inputImage)
                        Timber.tag(TAG).d("_processSuspend text : ${text.text}")
                        text
                    } catch (e: Exception) {
                        Timber.tag(TAG).e("Error processing text recognition: ${e.message}")
                        null
                    }
                }
            }

            val processResults: List<Text?> = deferredResults.awaitAll()

            val text = processResults.maxByOrNull { text ->
                text?.textBlocks?.sumOf { block ->
                    block.lines.sumOf { line ->
                        line.confidence.toDouble()
                    }
                } ?: 0.0
            } ?: throw Exception("No text recognized")

            val (detectedLanguageCode, analyzedParagraphs) = withContext(Dispatchers.Default) {
                var _sourceLanguageCode = sourceLanguageCode
                if (sourceLanguageCode == "auto") {
                    _sourceLanguageCode = identifyLanguage(text.text)
                    Timber.tag(TAG).i("_sourceLanguageCode : $_sourceLanguageCode")
                }

                val isVerticalWriting = detectVerticalWriting(text)
                val writingDirection = Language.writingDirection(_sourceLanguageCode, isVerticalWriting)
                if (writingDirection == WritingDirection.LTR || writingDirection == WritingDirection.RTL) {
                    _sourceLanguageCode to textToParagraphs(bitmap, text, _sourceLanguageCode, writingDirection, coordinateOffsetX, coordinateOffsetY)
                } else {
                    _sourceLanguageCode to textToVerticalParagraphs(bitmap, text, _sourceLanguageCode, writingDirection, coordinateOffsetX, coordinateOffsetY)
                }
            }

            VisionResponse.Success(Transaction(bitmap, text, detectedLanguageCode, analyzedParagraphs))
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
        Timber.tag(TAG).i("#### textToParagraphs() ####  ${"\n" + text.text}")
        setReferenceConstantValue(false, sourceLanguageCode)

        val elements: List<Text.Element> = convertTextLinesToTextElements(text.textBlocks.flatMap { textBlock -> textBlock.lines }, writingDirection)

        elements.forEach {
            Timber.tag(TAG).i("element : ${it.boundingBox} ${it.text} ${(it.boundingBox!!.width().toDouble() / it.boundingBox!!.height())._cutDecimal()}")
        }

        val words: List<Word> = convertTextElementsToWords(bitmap, elements, writingDirection, coordinateOffsetX, coordinateOffsetY)

        val lines: List<Line> = groupWordsIntoLines(bitmap, words, writingDirection, coordinateOffsetX, coordinateOffsetY)

        lines.forEach { Timber.tag(TAG).i("groupWordsIntoLines result : ${it.boundingBox}, ${it.representation}, ${it.words}") }

        var paragraphs: List<Paragraph> = groupLinesIntoParagraphs(lines, writingDirection)

        paragraphs.forEach { Timber.tag(TAG).i("groupLinesIntoParagraphs result : ${it.hasParallelLines} ${it.boundingBox} ${it.representation}") }

        /**
         */
        paragraphs = paragraphs.flatMap { paragraph ->
            val splitParagraphs = detectAndSplitParagraphs(paragraph, writingDirection)
            splitParagraphs.forEach {
                Timber.tag(TAG).d("detectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
            }
            val clusterParagraphs = correctDetectAndSplitParagraphs(splitParagraphs, writingDirection)
            clusterParagraphs.forEach {
                Timber.tag(TAG).d("correctDetectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
            }
            clusterParagraphs
        }

        paragraphs.forEach {
            Timber.tag(TAG).i("paragraphs ${it.boundingBox} ${it.representation} ")
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
        Timber.tag(TAG).i("#### textToVerticalParagraphs() ####  ${"\n" + text.text}")

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

        textLines.forEach { Timber.tag(TAG).i("textLine : ${it.boundingBox}, ${it.text}") }

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
        verticalParagraphs.forEach { Timber.tag(TAG).i("groupLinesIntoParagraphs result : ${it.boundingBox} ${it.representation}") }

        /**
         */
        verticalParagraphs = verticalParagraphs.flatMap { paragraph ->
            val splitParagraphs = detectAndSplitParagraphs(paragraph, writingDirection)
            splitParagraphs.forEach {
                Timber.tag(TAG).d("detectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
            }
            splitParagraphs
        }.toMutableList()

        /** ####################################### horizontalParagraphs ###################################### */
        setReferenceConstantValue(false, sourceLanguageCode)
        val horizontalWritingDirection = Language.writingDirection(sourceLanguageCode, false)
        val elements: List<Text.Element> = convertTextLinesToTextElements(horizontalTextLines, horizontalWritingDirection)
        val words: List<Word> = convertTextElementsToWords(bitmap, elements, horizontalWritingDirection, coordinateOffsetX, coordinateOffsetY)
        val lines: List<Line> = groupWordsIntoLines(bitmap, words, horizontalWritingDirection, coordinateOffsetX, coordinateOffsetY)
        var horizontalParagraphs: List<Paragraph> = groupLinesIntoParagraphs(lines, horizontalWritingDirection)
        horizontalParagraphs = horizontalParagraphs.flatMap { paragraph ->
            val splitParagraphs = detectAndSplitParagraphs(paragraph, horizontalWritingDirection)
            splitParagraphs.forEach {
                Timber.tag(TAG).d("detectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
            }
            val clusterParagraphs = correctDetectAndSplitParagraphs(splitParagraphs, horizontalWritingDirection)
            clusterParagraphs.forEach {
                Timber.tag(TAG).d("correctDetectAndSplitParagraphs ${it.boundingBox} ${it.representation} ")
            }
            clusterParagraphs
        }

        verticalParagraphs.forEach { Timber.tag(TAG).i("verticalParagraphs : ${it.boundingBox} ${it.representation}") }
        horizontalParagraphs.forEach { Timber.tag(TAG).i("horizontalParagraphs : ${it.boundingBox} ${it.representation}") }

        verticalParagraphs.addAll(horizontalParagraphs)

        return verticalParagraphs
    }

    /**
     */
    private fun convertTextLinesToTextElements(textLines: List<Text.Line>, writingDirection: WritingDirection): List<Text.Element> {
        return when (writingDirection) {
            WritingDirection.LTR -> {
                textLines
                    .filter { it.boundingBox.isValid() }
                    .sortedWith(
                        Comparator { line1, line2 ->
                            val topComparison = line1.boundingBox!!.top.compareTo(line2.boundingBox!!.top)
                            if (topComparison != 0) topComparison else line1.boundingBox!!.left.compareTo(line2.boundingBox!!.left)
                        }
                    )
                    .flatMap { line -> line.elements }
            }

            WritingDirection.RTL -> {
                textLines
                    .filter { it.boundingBox.isValid() }
                    .sortedWith(
                        Comparator { line1, line2 ->
                            val topComparison = line1.boundingBox!!.top.compareTo(line2.boundingBox!!.top)
                            if (topComparison != 0) topComparison else line2.boundingBox!!.right.compareTo(line1.boundingBox!!.right)
                        }
                    )
                    .flatMap { line -> line.elements }
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
                    .flatMap { line -> line.elements }
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
                    .flatMap { line -> line.elements }
            }
        }
    }

    /**
     */
    private fun convertTextElementsToWords(
        bitmap: Bitmap,
        elements: List<Text.Element>,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Word> {
        val words = mutableListOf<Word>()
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        for (element in elements) {
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
                    val chars = element.symbols
                        .filter { it.boundingBox.isValid() }
                        .map {
                            Char(
                                VisionCoordinateMapper.toScreenRect(it.boundingBox!!, coordinateOffsetX, coordinateOffsetY),
                                it.text,
                                writingDirection
                            )
                        }

                    if (chars.isNotEmpty()) {
                        words.add(Word(correctedBoundingBox, element.text, writingDirection, chars))
                    }
                }
            }
        }
        return words
    }

    /**
     */
    private fun groupWordsIntoLines(
        bitmap: Bitmap,
        words: List<Word>,
        writingDirection: WritingDirection,
        coordinateOffsetX: Int,
        coordinateOffsetY: Int,
    ): List<Line> {
        val lines = mutableListOf<Line>()

        words
            .sortedWith(VisionSingleLineText.getComparator(writingDirection))
            .forEach { word ->
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
                            line.addWord(word)
                            addedToLine = true
                            break
                        }
                        // [condition 0-1]
                        else {
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
                    // [condition 1]
                    else {
                        Timber.tag(TAG).v(
                            "groupWordsIntoLines drop 1 : "
                                    + "${writeDirectionDistance._cutDecimal()}, "
                                    + "${averageFontHeight._cutDecimal()}, "
                                    + "${writeDirectionDistanceFontHeightRatio._cutDecimal()}, "
                                    + "${line.representation}(${line.boundingBox}) + ${word.representation}(${word.boundingBox})"
                        )
                    }
                }

                if (!addedToLine) {
                    lines.add(0, Line(mutableListOf(word), writingDirection))
                }
            }

        lines.forEach {
            it.setFontAndBackgroundColors(bitmap, coordinateOffsetX, coordinateOffsetY)
        }

        return lines
    }

    /**
     */
    private fun groupLinesIntoParagraphs(lines: List<Line>, writingDirection: WritingDirection): List<Paragraph> {
        val paragraphs = mutableListOf<Paragraph>()

        lines
            .sortedWith(VisionSingleLineText.getComparator(writingDirection))
            .forEach { line ->
                if (line in paragraphs.flatMap { it.lines }) return@forEach

                var addedToParagraph = false

                for (paragraph in paragraphs) {
                    val isWriteDirectionOverlaps = line.isWriteDirectionOverlaps(paragraph)

                    val isLineReturnDirectionOverlaps = line.isLineReturnDirectionOverlaps(paragraph)

                    if (isWriteDirectionOverlaps && isLineReturnDirectionOverlaps) {
                        Timber.tag(TAG).d(
                            "groupLinesIntoParagraphs add 0 : "
                                    + "${paragraph.boundingBox}, "
                                    + "${paragraph.representation}(${paragraph.height}), "
                                    + "${line.boundingBox}, "
                                    + "${line.representation}(${line.height})"
                        )

                        paragraph.lines.add(line)
                        addedToParagraph = true
                        break
                    }

                    val closestLine = paragraph.lines.lastOrNull() ?: continue

                    val lineSpacing = closestLine.getLineReturnDirectionDistance(line)

                    if (lineSpacing > closestLine.fontHeight * 1.6) continue

                    val averageFontHeight: Double = line.getAverageFontHeight(closestLine)

                    val fontHeightSimilarityRatio = line.getFontHeightSimilarityRatio(closestLine)

                    if (fontHeightSimilarityRatio >= LINE_FONT_HEIGHT_SIMILARITY_MINIMUM_RATIO) {
                        if (isWriteDirectionOverlaps) {
                            val writeDirectionOverlapRatio: Double = line.getWriteDirectionOverlapRatio(paragraph)

                            if (writeDirectionOverlapRatio >= LINE_WRITE_DIRECTION_OVERLAP_MINIMUM_RATIO) {
                                val colorSimilarity = closestLine.getColorSimilarity(line)

                                Timber.tag(TAG).d(
                                    "${String.format("#%08X", closestLine.fontColor)} "
                                            + "${String.format("#%08X", closestLine.backgroundColor)} "
                                            + "${String.format("#%08X", line.fontColor)} "
                                            + "${String.format("#%08X", line.backgroundColor)} "
                                )

                                val lineSpacingAffinity = min(1.0, 1.0 / (lineSpacing.toDouble() / averageFontHeight))

                                val fontHeightColorLineSpacingAffinity = fontHeightSimilarityRatio * colorSimilarity * lineSpacingAffinity

                                // [condition 0-0-0-0] 
                                if (fontHeightColorLineSpacingAffinity >= LINE_FONT_HEIGHT_COLOR_SPACING_AFFINITY_LIMIT) {
                                    Timber.tag(TAG).d(
                                        "groupLinesIntoParagraphs add 0-0-0-0 : "
                                                + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${colorSimilarity._cutDecimal()}, "
                                                + "${lineSpacingAffinity._cutDecimal()}, "
                                                + "*${fontHeightColorLineSpacingAffinity._cutDecimal()}, "
                                                + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height})"
                                    )
                                    paragraph.lines.add(line)
                                    addedToParagraph = true
                                    break
                                }
                                // [condition 0-0-0-1] 
                                else {
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
                            // [condition 0-0-1] 
                            else {
                                Timber.tag(TAG).v(
                                    "groupLinesIntoParagraphs drop 0-0-1 : "
                                            + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                            + "${writeDirectionOverlapRatio._cutDecimal()}, "
                                            + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height})"
                                )
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
                                    Timber.tag(TAG).d(
                                        "groupLinesIntoParagraphs add 0-1-0-0 : "
                                                + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                                + "${axisSimilarityRatio._cutDecimal()}, "
                                                + "${axisHeightSimilarityRatio._cutDecimal()}, "
                                                + "${writeDirectionDistance._cutDecimal()}, "
                                                + "${writeDirectionDistanceFontHeightRatio._cutDecimal()}, "
                                                + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                                    )

                                    if (writingDirection == WritingDirection.LTR) {
                                        if (closestLine.boundingBox.right < line.boundingBox.right) {
                                            paragraph.lines.add(line)
                                        } else {
                                            paragraph.lines.add(paragraph.lines.size - 1, line)
                                        }
                                    } else {
                                        if (line.boundingBox.left < closestLine.boundingBox.left) {
                                            paragraph.lines.add(line)
                                        } else {
                                            paragraph.lines.add(paragraph.lines.size - 1, line)
                                        }
                                    }
                                    paragraph.hasParallelLines = true
                                    addedToParagraph = true
                                    break
                                }
                                // [condition 0-1-0-1]
                                else {
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
                            // [condition 0-1-1]
                            else {
                                Timber.tag(TAG).v(
                                    "groupLinesIntoParagraphs drop 0-1-1 : "
                                            + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                            + "${axisSimilarityRatio._cutDecimal()}, "
                                            + "${axisHeightSimilarityRatio._cutDecimal()}, "
                                            + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                                )
                            }
                        }
                        // [condition 0-2]
                        else {
                            Timber.tag(TAG).v(
                                "groupLinesIntoParagraphs drop 0-2 : "
                                        + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                        + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                            )
                        }
                    }
                    // [condition 1]
                    else {
                        Timber.tag(TAG).v(
                            "groupLinesIntoParagraphs drop 1 : "
                                    + "${fontHeightSimilarityRatio._cutDecimal()}, "
                                    + "${closestLine.representation}(${closestLine.height}) + ${line.representation}(${line.height}))"
                        )
                    }
                }

                if (!addedToParagraph) {
                    paragraphs.add(0, Paragraph(mutableListOf(line), writingDirection))
                }
            }

        return paragraphs
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

        Timber.tag(TAG).d("detectAndSplitParagraphs ${paragraph.representation}")

        val lines = paragraph.lines
        val clustersVisited = mutableSetOf<Line>()
        val clusters = mutableListOf<MutableList<Line>>()
        val distanceLimit: Double = paragraph.averageLineHeight()

        fun expandCluster(line: Line, cluster: MutableList<Line>) {
            val neighbors =
                lines.filter {
                    if (it != line) {
                        Timber.tag(TAG).i(
                            "Split cluster "
                                    + "$distanceLimit, ${abs(line.startPosition - it.startPosition)}, ${line.boundingBox}, ${line.representation}, ${it.boundingBox}, ${it.representation}"
                        )
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
                    Timber.tag(TAG)
                        .i("Correct cluster ${paragraph.isWriteDirectionOverlaps(it)}, ${paragraph.boundingBox}, ${paragraph.representation}, ${it.boundingBox}, ${it.representation}")
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
        Timber.tag(TAG).i("isVerticalWriting  $countGreaterThanOne $countLessThanOne")
        return countGreaterThanOne < countLessThanOne
    }

    /**
     */
    private fun setReferenceConstantValue(isVerticalWriting: Boolean, sourceLanguageCode: String) {
        val isNonSpacingLanguage = Language.isNonSpacingLanguage(sourceLanguageCode)
        Timber.tag(TAG)
            .d("setReferenceConstantValue - sourceLanguageCode: $sourceLanguageCode, isNonSpacingLanguage: $isNonSpacingLanguage, isVerticalWriting: $isVerticalWriting")

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



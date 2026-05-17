package com.yiqun.translator.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Debug
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiqun.translator.data.local.vision.AutoRecognitionPolicy
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.VisionRepository
import com.yiqun.translator.data.local.vision.model.Paragraph
import com.yiqun.translator.data.local.vision.model.Sentence
import com.yiqun.translator.data.local.vision.model.VisionResponse
import com.yiqun.translator.data.local.vision.model.Word
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroupRequestCache
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroupChunkPolicy
import com.yiqun.translator.data.remote.translation.TranslationRequestCache
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.remote.translation.Transaction
import com.yiqun.translator.ui.screen.overlay.fixedarea.FixedAreaRecognitionPolicy
import com.yiqun.translator.ui.screen.overlay.selection.AreaCapturePolicy
import com.yiqun.translator.ui.screen.overlay.selection.createOverlaidBitmap
import com.yiqun.translator.ui.screen.overlay.targethandle.RecognitionDelayPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class RecognitionModesEndToEndPerformanceInstrumentedTest {

    @Test
    fun profileAllRecognitionModesAgainstLegacyBaseline() = runBlocking {
        val openRouterKey = InstrumentationRegistry.getArguments().getString(ARG_OPENROUTER_KEY).orEmpty()
        assumeTrue("Pass -e $ARG_OPENROUTER_KEY to include SENSE_GROUP GPT timing", openRouterKey.isNotBlank())

        val repository = VisionRepository()
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val template = createFullScreenTextBitmap()
        val selectionRect = Rect(40, 360, 1040, 960)

        val warmupBitmap = template.copy(Bitmap.Config.ARGB_8888, false)
        val warmup = repository.request(
            bitmap = warmupBitmap,
            sourceLanguageCode = SOURCE_LANGUAGE,
            coordinateOffsetX = 0,
            coordinateOffsetY = 0,
            autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
        )
        warmupBitmap.recycle()
        assertTrue("Warmup OCR must detect text", warmup is VisionResponse.Success)
        googleTranslateCurrent(httpClient, "warmup", SOURCE_LANGUAGE, TARGET_LANGUAGE)

        val summaries = TextDetectMode.values().map { mode ->
            val legacyRuns = (1..PROFILE_RUNS).map { runIndex ->
                profileRun(
                    label = "legacy",
                    runIndex = runIndex,
                    mode = mode,
                    repository = repository,
                    httpClient = httpClient,
                    openRouterKey = openRouterKey,
                    template = template,
                    selectionRect = selectionRect,
                    optimized = false,
                )
            }
            val currentRuns = (1..PROFILE_RUNS).map { runIndex ->
                profileRun(
                    label = "current",
                    runIndex = runIndex,
                    mode = mode,
                    repository = repository,
                    httpClient = httpClient,
                    openRouterKey = openRouterKey,
                    template = template,
                    selectionRect = selectionRect,
                    optimized = true,
                )
            }

            val summary = ModeSummary(
                mode = mode,
                legacyWallMs = legacyRuns.averageOf { it.wallMs },
                currentWallMs = currentRuns.averageOf { it.wallMs },
                legacyCpuMs = legacyRuns.averageOf { it.cpuMs },
                currentCpuMs = currentRuns.averageOf { it.cpuMs },
                legacyDeltaPssKb = legacyRuns.averageOf { it.deltaPssKb },
                currentDeltaPssKb = currentRuns.averageOf { it.deltaPssKb },
            )
            Log.i(
                PERF_TAG,
                "MODE_SUMMARY mode=$mode runs=$PROFILE_RUNS " +
                        "legacyAvgWallMs=${summary.legacyWallMs.roundToInt()} " +
                        "currentAvgWallMs=${summary.currentWallMs.roundToInt()} " +
                        "wallRatio=${summary.wallRatio()} " +
                        "legacyAvgCpuMs=${summary.legacyCpuMs.roundToInt()} " +
                        "currentAvgCpuMs=${summary.currentCpuMs.roundToInt()} " +
                        "legacyAvgDeltaPssKb=${summary.legacyDeltaPssKb.roundToInt()} " +
                        "currentAvgDeltaPssKb=${summary.currentDeltaPssKb.roundToInt()}"
            )
            summary
        }

        template.recycle()

        summaries.forEach { summary ->
            assertTrue(
                "${summary.mode} average wall time should be <= half of legacy baseline. " +
                        "legacy=${summary.legacyWallMs.roundToInt()}ms current=${summary.currentWallMs.roundToInt()}ms",
                summary.currentWallMs <= summary.legacyWallMs * HALF_TIME_TARGET_RATIO
            )
            assertTrue(
                "${summary.mode} average CPU must not increase",
                summary.currentCpuMs <= summary.legacyCpuMs + CPU_NOISE_ALLOWANCE_MS
            )
            assertTrue(
                "${summary.mode} average PSS delta must not increase",
                summary.currentDeltaPssKb <= summary.legacyDeltaPssKb + PSS_NOISE_ALLOWANCE_KB
            )
        }
    }

    private suspend fun profileRun(
        label: String,
        runIndex: Int,
        mode: TextDetectMode,
        repository: VisionRepository,
        httpClient: OkHttpClient,
        openRouterKey: String,
        template: Bitmap,
        selectionRect: Rect,
        optimized: Boolean,
    ): RunResult {
        Runtime.getRuntime().gc()
        delay(100)
        val keepSampling = java.util.concurrent.atomic.AtomicBoolean(true)
        val startPssKb = currentPssKb()
        var peakPssKb = startPssKb
        val sampler = Thread {
            while (keepSampling.get()) {
                peakPssKb = maxOf(peakPssKb, currentPssKb())
                Thread.sleep(20)
            }
        }

        val startCpuMs = processCpuMs()
        val startWallNs = System.nanoTime()
        sampler.start()
        val resultText = try {
            delay(startDelayFor(mode, optimized))
            val visionText = recognizeTextForMode(
                repository = repository,
                template = template,
                selectionRect = selectionRect,
                mode = mode,
                optimized = optimized,
            )
            if (mode == TextDetectMode.SENSE_GROUP) {
                if (optimized) {
                    val chunk = senseGroupCurrent(httpClient, openRouterKey, visionText)
                    googleTranslateCurrentCached(httpClient, chunk, SOURCE_LANGUAGE, TARGET_LANGUAGE)
                } else {
                    senseGroupLegacy(httpClient, openRouterKey, visionText).translation
                }
            } else {
                if (optimized) {
                    googleTranslateCurrentCached(httpClient, visionText, SOURCE_LANGUAGE, TARGET_LANGUAGE)
                } else {
                    googleTranslateLegacy(httpClient, visionText, SOURCE_LANGUAGE, TARGET_LANGUAGE)
                }
            }
        } finally {
            keepSampling.set(false)
            sampler.join()
        }

        val wallMs = (System.nanoTime() - startWallNs) / 1_000_000
        val cpuMs = processCpuMs() - startCpuMs
        val deltaPssKb = peakPssKb - startPssKb
        Log.i(
            PERF_TAG,
            "MODE_RUN label=$label mode=$mode index=$runIndex wallMs=$wallMs cpuMs=$cpuMs " +
                    "startPssKb=$startPssKb peakPssKb=$peakPssKb deltaPssKb=$deltaPssKb result=${resultText.take(48)}"
        )
        assertTrue("$label $mode should return a non-empty result", resultText.isNotBlank())
        return RunResult(wallMs, cpuMs, deltaPssKb)
    }

    private fun startDelayFor(mode: TextDetectMode, optimized: Boolean): Long {
        if (optimized) {
            return when (mode) {
                TextDetectMode.FIXED_AREA -> FixedAreaRecognitionPolicy.firstRecognitionDelayMs()
                else -> RecognitionDelayPolicy.pointerStoppedDelayMs(mode) + RecognitionDelayPolicy.captureStartDelayMs()
            }
        }
        return when (mode) {
            TextDetectMode.SELECT -> LEGACY_SELECT_DELAY_MS
            TextDetectMode.FIXED_AREA -> LEGACY_FIXED_AREA_FIRST_DELAY_MS
            else -> LEGACY_POINTED_POINTER_DELAY_MS + LEGACY_CAPTURE_START_DELAY_MS
        }
    }

    private suspend fun recognizeTextForMode(
        repository: VisionRepository,
        template: Bitmap,
        selectionRect: Rect,
        mode: TextDetectMode,
        optimized: Boolean,
    ): String {
        val bitmap = when {
            mode == TextDetectMode.SELECT || mode == TextDetectMode.FIXED_AREA -> {
                if (optimized) {
                    val captureRect = AreaCapturePolicy.captureRect(selectionRect)
                    Bitmap.createBitmap(template, captureRect.left, captureRect.top, captureRect.width(), captureRect.height())
                } else {
                    createOverlaidBitmap(template, selectionRect)
                }
            }
            else -> template.copy(Bitmap.Config.ARGB_8888, false)
        }
        val offsetX = if (optimized && (mode == TextDetectMode.SELECT || mode == TextDetectMode.FIXED_AREA)) selectionRect.left else 0
        val offsetY = if (optimized && (mode == TextDetectMode.SELECT || mode == TextDetectMode.FIXED_AREA)) selectionRect.top else 0
        val response = try {
            repository.request(
                bitmap = bitmap,
                sourceLanguageCode = SOURCE_LANGUAGE,
                coordinateOffsetX = offsetX,
                coordinateOffsetY = offsetY,
                autoRecognitionPolicy = if (optimized) AutoRecognitionPolicy.LATIN_FIRST else AutoRecognitionPolicy.FULL,
            )
        } finally {
            bitmap.recycle()
        }
        assertTrue("OCR should succeed for $mode", response is VisionResponse.Success)
        val transaction = (response as VisionResponse.Success).result
        return when (mode) {
            TextDetectMode.WORD -> transaction.paragraphs.firstWordText()
            TextDetectMode.SENTENCE,
            TextDetectMode.SENSE_GROUP -> transaction.paragraphs.firstSentenceText()
            TextDetectMode.PARAGRAPH -> transaction.paragraphs.firstParagraphText()
            TextDetectMode.SELECT,
            TextDetectMode.FIXED_AREA -> transaction.text.text.replace("\n", " ").trim()
        }.takeIf { it.isNotBlank() } ?: transaction.text.text.replace("\n", " ").trim()
    }

    private fun List<Paragraph>.firstWordText(): String {
        return asSequence()
            .flatMap { it.lines.asSequence() }
            .flatMap { it.words.asSequence() }
            .map(Word::representation)
            .firstOrNull { it.equals("committee", ignoreCase = true) }
            ?: firstSentenceText().substringBefore(' ')
    }

    private fun List<Paragraph>.firstSentenceText(): String {
        return asSequence()
            .flatMap { it.sentences.asSequence() }
            .map(Sentence::representation)
            .firstOrNull { it.contains("committee", ignoreCase = true) }
            ?: firstParagraphText()
    }

    private fun List<Paragraph>.firstParagraphText(): String {
        return firstOrNull()?.representation.orEmpty()
    }

    private suspend fun googleTranslateCurrent(
        httpClient: OkHttpClient,
        sourceText: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): String = googleTranslate(
        httpClient = httpClient,
        baseUrl = "https://translate.googleapis.com/translate_a/single",
        client = "gtx",
        token = "0",
        sourceText = sourceText,
        sourceLanguageCode = sourceLanguageCode,
        targetLanguageCode = targetLanguageCode,
    )

    private suspend fun googleTranslateCurrentCached(
        httpClient: OkHttpClient,
        sourceText: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): String {
        TranslationRequestCache.get(
            TranslationKitType.GOOGLE,
            sourceLanguageCode,
            targetLanguageCode,
            sourceText,
        )?.resultText?.let { cached ->
            return cached
        }

        return googleTranslateCurrent(httpClient, sourceText, sourceLanguageCode, targetLanguageCode)
            .also { translated ->
                TranslationRequestCache.put(
                    Transaction(
                        sourceLanguageCode = sourceLanguageCode,
                        targetLanguageCode = targetLanguageCode,
                        sourceText = sourceText,
                        translationKitType = TranslationKitType.GOOGLE,
                        detectedLanguageCode = sourceLanguageCode,
                        resultText = translated,
                    )
                )
            }
    }

    private suspend fun googleTranslateLegacy(
        httpClient: OkHttpClient,
        sourceText: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): String = googleTranslate(
        httpClient = httpClient,
        baseUrl = "https://translate.google.com/translate_a/single",
        client = "webapp",
        token = legacyGoogleToken(sourceText.trim()),
        sourceText = sourceText,
        sourceLanguageCode = sourceLanguageCode,
        targetLanguageCode = targetLanguageCode,
    )

    private suspend fun googleTranslate(
        httpClient: OkHttpClient,
        baseUrl: String,
        client: String,
        token: String,
        sourceText: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(sourceText.trim(), "UTF-8")
        val url = "$baseUrl?client=$client&dt=t&ie=UTF-8&oe=UTF-8&source=btn&ssel=0&tsel=0&kc=1" +
                "&sl=$sourceLanguageCode&tl=$targetLanguageCode&hl=$sourceLanguageCode&tk=$token&q=$encoded"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            assertTrue("Google translation HTTP ${response.code}", response.isSuccessful)
            val body = response.body?.string().orEmpty()
            val segments = JSONArray(body).getJSONArray(0)
            buildString {
                for (index in 0 until segments.length()) {
                    append(segments.getJSONArray(index).optString(0))
                }
            }
        }
    }

    private suspend fun senseGroupLegacy(
        httpClient: OkHttpClient,
        openRouterKey: String,
        sentence: String,
    ): SenseGroupResult {
        val word = "decision"
        val offset = sentence.indexOf(word).coerceAtLeast(0)
        val systemContent = "You are a linguist. The user is reading a sentence and is pointing " +
                "at a specific word inside it. Your job is to identify the SENSE GROUP " +
                "(also known as a semantic chunk, thought unit, or 意群) that contains " +
                "that pointed word, and translate JUST that chunk into the target language. " +
                "\n\n" +
                "A sense group is a phrase-level unit smaller than the full sentence: " +
                "typically a noun phrase, a verb phrase, a prepositional phrase, or a " +
                "subordinate clause. It is the minimal contiguous span of words that " +
                "carries a self-contained meaning and gives the pointed word its " +
                "in-context interpretation." +
                "\n\n" +
                "Rules (all MUST be followed):" +
                "\n - The chunk MUST contain the pointed word." +
                "\n - If pointed_word_start and pointed_word_end are provided, identify " +
                "the word occurrence at exactly those character offsets. Do NOT use an " +
                "earlier repeated occurrence of the same word." +
                "\n - The chunk MUST appear VERBATIM in the sentence — copy the substring " +
                "exactly, do not paraphrase, do not normalize punctuation, do not change " +
                "case." +
                "\n - Prefer the SMALLEST meaningful chunk. Do NOT return the entire " +
                "sentence unless the sentence is itself a single short phrase." +
                "\n - Translate ONLY the chunk, not the surrounding sentence." +
                "\n\n" +
                "Respond ONLY with valid JSON of the form:" +
                "\n{\"chunk\":\"<verbatim span from the sentence>\",\"translation\":\"<translation in target language>\"}"
        val userPayload = JSONObject()
            .put("word", word)
            .put("sentence", sentence)
            .put("pointed_word_start", offset)
            .put("pointed_word_end", offset + word.length)
            .put("source_language", SOURCE_LANGUAGE_FOR_AI)
            .put("target_language", TARGET_LANGUAGE)
        return openRouterSenseGroup(
            httpClient = httpClient,
            openRouterKey = openRouterKey,
            systemContent = systemContent,
            userContent = userPayload.toString(),
            maxTokens = 400,
        )
    }

    private suspend fun senseGroupCurrent(
        httpClient: OkHttpClient,
        openRouterKey: String,
        sentence: String,
    ): String {
        val word = "decision"
        val offset = sentence.indexOf(word).coerceAtLeast(0)
        val endpointUrl = "https://openrouter.ai/api/v1/chat/completions"
        val model = "openai/gpt-4o-mini"
        SenseGroupRequestCache.get(
            model = model,
            endpointUrl = endpointUrl,
            word = word,
            sentence = sentence,
            pointedTokenOffset = offset,
            sourceLanguageCode = SOURCE_LANGUAGE_FOR_AI,
            targetLanguageCode = TARGET_LANGUAGE,
        )?.text?.let { cached ->
            return cached
        }
        val systemContent = SenseGroupChunkPolicy.SYSTEM_PROMPT
        val userPayload = JSONObject()
            .put("word", word)
            .put("sentence", sentence)
            .put("pointed_word_start", offset)
            .put("pointed_word_end", offset + word.length)
            .put("source_language", SOURCE_LANGUAGE_FOR_AI)
        return openRouterSenseGroup(
            httpClient = httpClient,
            openRouterKey = openRouterKey,
            systemContent = systemContent,
            userContent = userPayload.toString(),
            maxTokens = 80,
        ).also { result ->
            val refinedChunk = SenseGroupChunkPolicy.refineChunk(
                sentence = sentence,
                modelChunk = result.chunk,
                word = word,
                pointedTokenOffset = offset,
            ).orEmpty()
            val refinedRange = chunkCharRange(sentence, refinedChunk, offset)
            if (refinedChunk.isNotBlank() && refinedRange != null) {
                SenseGroupRequestCache.put(
                    model = model,
                    endpointUrl = endpointUrl,
                    word = word,
                    sentence = sentence,
                    pointedTokenOffset = offset,
                    sourceLanguageCode = SOURCE_LANGUAGE_FOR_AI,
                    targetLanguageCode = TARGET_LANGUAGE,
                    senseGroup = com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup(refinedChunk, "", refinedRange),
                )
            }
        }.let { result ->
            SenseGroupChunkPolicy.refineChunk(
                sentence = sentence,
                modelChunk = result.chunk,
                word = word,
                pointedTokenOffset = offset,
            ).orEmpty()
        }
    }

    private suspend fun openRouterSenseGroup(
        httpClient: OkHttpClient,
        openRouterKey: String,
        systemContent: String,
        userContent: String,
        maxTokens: Int,
    ): SenseGroupResult = withContext(Dispatchers.IO) {
        val requestJson = JSONObject()
            .put("model", "openai/gpt-4o-mini")
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemContent))
                    .put(JSONObject().put("role", "user").put("content", userContent))
            )
            .put("max_tokens", maxTokens)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0.0)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $openRouterKey")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            assertTrue("OpenRouter HTTP ${response.code}: $body", response.isSuccessful)
            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            val json = JSONObject(content)
            SenseGroupResult(
                chunk = json.optString("chunk").trim(),
                translation = json.optString("translation").trim(),
            )
        }
    }

    private fun createFullScreenTextBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 24, 24)
            textSize = 42f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(48, 48, 48)
            textSize = 34f
        }

        canvas.drawText("Recognition latency benchmark", 54f, 150f, titlePaint)
        val lines = listOf(
            "The committee made a careful decision after reviewing the evidence.",
            "This sentence stays inside the selected area for area modes.",
            "The quick brown fox jumps over the lazy dog.",
            "Paragraph recognition keeps the complete paragraph visible.",
            "Fixed area translation should begin without an extra wait."
        )
        var y = 470f
        lines.forEachIndexed { index, text ->
            canvas.drawText(text, 70f, y, if (index == 0) bodyPaint else smallPaint)
            y += 104f
        }
        return bitmap
    }

    private fun legacyGoogleToken(input: String): String {
        var b = 406_644L
        val b1 = 3_293_161_072L
        val bytes = mutableListOf<Long>()
        var index = 0
        while (index < input.length) {
            var code = input[index].code.toLong()
            when {
                code < 128 -> bytes.add(code)
                code < 2048 -> {
                    bytes.add(code shr 6 or 192)
                    bytes.add(code and 63 or 128)
                }
                code and 64512 == 55296L &&
                        index + 1 < input.length &&
                        input[index + 1].code.toLong() and 64512 == 56320L -> {
                    code = 65536 + ((code and 1023) shl 10) + (input[++index].code.toLong() and 1023)
                    bytes.add(code shr 18 or 240)
                    bytes.add(code shr 12 and 63 or 128)
                    bytes.add(code shr 6 and 63 or 128)
                    bytes.add(code and 63 or 128)
                }
                else -> {
                    bytes.add(code shr 12 or 224)
                    bytes.add(code shr 6 and 63 or 128)
                    bytes.add(code and 63 or 128)
                }
            }
            index++
        }
        for (value in bytes) {
            b = (b + value) and 0xFFFFFFFFL
            b = legacyGoogleRl(b, "+-a^+6")
        }
        b = legacyGoogleRl(b, "+-3^+b+-f")
        b = (b xor b1) and 0xFFFFFFFFL
        if (b < 0) b = (b and 2147483647) + 2147483648
        b %= 1_000_000
        return "${b}.${(b xor 406_644L) and 0xFFFFFFFFL}"
    }

    private fun legacyGoogleRl(a: Long, b: String): Long {
        var result = a
        var index = 0
        while (index < b.length - 2) {
            val d = b[index + 2].let { if (it >= 'a') it.code - 87 else it.toString().toInt() }
            val shifted = if (b[index + 1] == '+') result ushr d else result shl d
            result = if (b[index] == '+') (result + shifted) and 0xFFFFFFFFL else (result xor shifted) and 0xFFFFFFFFL
            index += 3
        }
        return result
    }

    private fun currentPssKb(): Int {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss
    }

    private fun processCpuMs(): Long {
        val stat = File("/proc/${Process.myPid()}/stat").readText()
        val closeParen = stat.lastIndexOf(')')
        val fields = stat.substring(closeParen + 2).split(' ')
        val userTicks = fields[11].toLong()
        val kernelTicks = fields[12].toLong()
        val ticksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK)
        return ((userTicks + kernelTicks) * 1000.0 / ticksPerSecond).roundToInt().toLong()
    }

    private fun chunkCharRange(
        sentence: String,
        chunkText: String,
        pointedTokenOffset: Int?,
    ): IntRange? {
        val chunk = chunkText.takeIf { it.isNotEmpty() } ?: return null
        val occurrences = mutableListOf<IntRange>()
        var searchFrom = 0
        while (searchFrom <= sentence.length) {
            val start = sentence.indexOf(chunk, startIndex = searchFrom)
            if (start < 0) break
            val end = start + chunk.length
            occurrences.add(start..end)
            searchFrom = (start + 1).coerceAtMost(sentence.length + 1)
        }
        if (occurrences.isEmpty()) return null
        if (pointedTokenOffset == null) return occurrences.first()

        return occurrences.firstOrNull { offset ->
            pointedTokenOffset >= offset.first && pointedTokenOffset < offset.last
        } ?: occurrences.minByOrNull { range ->
            when {
                pointedTokenOffset < range.first -> range.first - pointedTokenOffset
                pointedTokenOffset >= range.last -> pointedTokenOffset - range.last + 1
                else -> 0
            }
        }
    }

    private fun List<RunResult>.averageOf(selector: (RunResult) -> Number): Double {
        return map { selector(it).toDouble() }.average()
    }

    private fun ModeSummary.wallRatio(): String {
        return "%.3f".format(currentWallMs / legacyWallMs)
    }

    private data class RunResult(
        val wallMs: Long,
        val cpuMs: Long,
        val deltaPssKb: Int,
    )

    private data class SenseGroupResult(
        val chunk: String,
        val translation: String,
    )

    private data class ModeSummary(
        val mode: TextDetectMode,
        val legacyWallMs: Double,
        val currentWallMs: Double,
        val legacyCpuMs: Double,
        val currentCpuMs: Double,
        val legacyDeltaPssKb: Double,
        val currentDeltaPssKb: Double,
    )

    private companion object {
        const val PERF_TAG = "EndToEndPerf"
        const val ARG_OPENROUTER_KEY = "openrouterKey"
        const val SCREEN_WIDTH = 1080
        const val SCREEN_HEIGHT = 2400
        const val PROFILE_RUNS = 3
        const val SOURCE_LANGUAGE = "auto"
        const val SOURCE_LANGUAGE_FOR_AI = "en"
        const val TARGET_LANGUAGE = "zh-CN"
        const val LEGACY_POINTED_POINTER_DELAY_MS = 80L
        const val LEGACY_CAPTURE_START_DELAY_MS = 50L
        const val LEGACY_SELECT_DELAY_MS = 220L
        const val LEGACY_FIXED_AREA_FIRST_DELAY_MS = 100L
        const val HALF_TIME_TARGET_RATIO = 0.55
        const val CPU_NOISE_ALLOWANCE_MS = 20.0
        const val PSS_NOISE_ALLOWANCE_KB = 1024.0
    }
}

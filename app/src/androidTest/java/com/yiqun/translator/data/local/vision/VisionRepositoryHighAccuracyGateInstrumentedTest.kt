package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.text.Text
import com.yiqun.translator.data.local.vision.model.Sentence
import com.yiqun.translator.data.local.vision.model.VisionResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionRepositoryHighAccuracyGateInstrumentedTest {

    @Test
    fun reachesNinetyFivePercentCriticalTokenAccuracyAcrossKnownFontFailures() = runBlocking {
        val repository = VisionRepository()
        val rows = buildList {
            cases.forEach { case ->
                variants.forEach { variant ->
                    repeat(REPETITIONS) { repetition ->
                        val bitmap = render(case.text, variant)
                        val response = try {
                            repository.request(
                                bitmap = bitmap,
                                sourceLanguageCode = case.sourceLanguageCode,
                                autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
                            )
                        } finally {
                            bitmap.recycle()
                        }
                        val (recognized, rawText, elements) = when (response) {
                            is VisionResponse.Success -> Triple(
                                response.result.paragraphs
                                    .flatMap { it.sentences }
                                    .joinToString(separator = " ") { Sentence::representation.invoke(it) }
                                    .ifBlank { response.result.text.text },
                                response.result.text.text,
                                response.result.text.elementSnapshot(),
                            )
                            is VisionResponse.Error -> Triple("", response.t.message.orEmpty(), "")
                        }
                        add(ResultRow(case, variant, repetition, normalize(recognized), normalize(rawText), elements))
                    }
                }
            }
        }

        val criticalAccuracy = rows.count { it.criticalMatched }.toDouble() / rows.size.toDouble()
        Log.i(
            TAG,
            "SUMMARY rows=${rows.size} matched=${rows.count { it.criticalMatched }} " +
                    "criticalAccuracy=${"%.4f".format(criticalAccuracy)} cases=${cases.size} variants=${variants.size} repetitions=$REPETITIONS"
        )
        val failures = rows
            .filterNot { it.criticalMatched }
            .groupBy { "${it.case.name}/${it.variant.name}" }
            .map { (key, group) ->
                val first = group.first()
                "$key -> recognized='${first.recognized}', raw='${first.rawText}', elements='${first.elements}'"
            }
            .take(40)

        assertTrue(
            "Expected critical OCR accuracy >= 95%, actual=${"%.4f".format(criticalAccuracy)}\n" +
                    failures.joinToString(separator = "\n"),
            criticalAccuracy >= 0.95,
        )
    }

    private data class TestCase(
        val name: String,
        val text: String,
        val criticalToken: String,
        val sourceLanguageCode: String = "en",
    )

    private data class FontVariant(
        val name: String,
        val typefaceName: String,
        val textSize: Float,
        val background: Int = Color.WHITE,
        val foreground: Int = Color.rgb(24, 24, 24),
        val fakeBold: Boolean = false,
        val textScaleX: Float = 1.0f,
    )

    private data class ResultRow(
        val case: TestCase,
        val variant: FontVariant,
        val repetition: Int,
        val recognized: String,
        val rawText: String,
        val elements: String,
    ) {
        val criticalMatched: Boolean
            get() = normalize(recognized).contains(normalize(case.criticalToken))
    }

    private companion object {
        const val SCREEN_WIDTH = 1320
        const val SCREEN_HEIGHT = 260
        const val REPETITIONS = 3
        const val TAG = "HighAccuracyGate"

        val cases = listOf(
            TestCase("i_l_1", "Token I1l stays exact in UI.", "I1l"),
            TestCase("o_zero_o", "Token O0o stays exact in UI.", "O0o"),
            TestCase("s_five", "Code S5S must remain exact.", "S5S"),
            TestCase("b_eight", "Code B8B must remain exact.", "B8B"),
            TestCase("z_two", "Code Z2Z must remain exact.", "Z2Z"),
            TestCase("g_six", "Code G6G must remain exact.", "G6G"),
            TestCase("rn_m", "Compare rn with m in normal text.", "rn"),
            TestCase("cl_d", "Compare cl with d in normal text.", "cl"),
            TestCase("r_and_b", "Play R&B before lunch.", "R&B"),
            TestCase("code", "Use val id=\"O0I1\" in Kotlin.", "val id=\"O0I1\""),
            TestCase("url", "Open https://a-b.example.com/v1?q=O0.", "https://a-b.example.com/v1?q=O0"),
            TestCase("symbol_cluster", "Keep & @ # % $ all visible.", "& @ # % $"),
            TestCase("brackets", "Read (OK) [ID] {json} <tag>.", "(OK) [ID] {json} <tag>"),
            TestCase("email", "Email a.b-01@test-site.com now.", "a.b-01@test-site.com"),
            TestCase("quotes_colon_semicolon", "Say \"OK\": then 'go'; now.", "\"OK\": then 'go';"),
            TestCase("slashes", "Path A/B and A\\B must differ.", "A/B and A\\B"),
            TestCase("version", "Upgrade app v3.2.2-beta.1 today.", "v3.2.2-beta.1"),
            TestCase("currency_percent", "Pay S$5.00, save 50% today.", "S$5.00, save 50%"),
            TestCase("username", "Mention @user_01 and #topic now.", "@user_01 and #topic"),
            TestCase("mixed_zh_en", "混排 OCR: user_01 支付 S$5.00。", "user_01 支付 S$5.00", sourceLanguageCode = "auto"),
        )

        val variants = listOf(
            FontVariant("roboto_normal_32_high", "sans-serif", 32f),
            FontVariant("roboto_bold_32_high", "sans-serif", 32f, fakeBold = true),
            FontVariant("roboto_small_24_low", "sans-serif", 24f, foreground = Color.rgb(108, 108, 108)),
            FontVariant("roboto_dark_28", "sans-serif", 28f, background = Color.rgb(20, 22, 26), foreground = Color.WHITE),
            FontVariant("ui_condensed_28_compressed", "sans-serif-condensed", 28f, textScaleX = 0.86f),
            FontVariant("ui_wide_30", "sans-serif", 30f, textScaleX = 1.08f),
            FontVariant("mono_30", "monospace", 30f),
            FontVariant("serif_30", "serif", 30f),
        )

        fun render(text: String, variant: FontVariant): Bitmap {
            val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(variant.background)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = variant.foreground
                textSize = variant.textSize
                typeface = Typeface.create(variant.typefaceName, Typeface.NORMAL)
                isFakeBoldText = variant.fakeBold
                isSubpixelText = true
                textScaleX = variant.textScaleX
            }
            val lines = wrap(text, paint, SCREEN_WIDTH - 96f)
            var y = if (lines.size == 1) 118f else 88f
            lines.forEach { line ->
                canvas.drawText(line, 48f, y, paint)
                y += variant.textSize * 1.65f
            }
            return bitmap
        }

        fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            val words = text.split(' ')
            val lines = mutableListOf<String>()
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                    current = candidate
                } else {
                    lines.add(current)
                    current = word
                }
            }
            if (current.isNotEmpty()) lines.add(current)
            return lines
        }

        fun normalize(text: String): String {
            return text.replace(Regex("\\s+"), " ")
                .replace(" 。", "。")
                .replace(" .", ".")
                .trim()
        }

        fun Text.elementSnapshot(): String {
            return textBlocks.joinToString(separator = " | ") { block ->
                block.lines.joinToString(separator = " / ") { line ->
                    line.elements.joinToString(separator = " ") { element ->
                        val symbols = element.symbols.joinToString(separator = "") { symbol ->
                            val box = symbol.boundingBox
                            if (box == null) {
                                symbol.text
                            } else {
                                "${symbol.text}{${box.left},${box.top},${box.right},${box.bottom}}"
                            }
                        }
                        "${element.text}[$symbols]"
                    }
                }
            }
        }
    }
}

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
class VisionRepositoryGlyphAccuracyInstrumentedTest {

    @Test
    fun recognizesBroadAmbiguousLatinGlyphAndSymbolScenesTenTimesEach() = runBlocking {
        val repository = VisionRepository()
        val failures = mutableListOf<String>()

        scenes.forEachIndexed { sceneIndex, scene ->
            repeat(REPETITIONS) { repetition ->
                val bitmap = scene.toBitmap()
                val response = try {
                    repository.request(
                        bitmap = bitmap,
                        sourceLanguageCode = "en",
                        autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
                    )
                } finally {
                    bitmap.recycle()
                }

                val result = when (response) {
                    is VisionResponse.Success -> response.result
                    is VisionResponse.Error -> {
                        failures.add("${scene.name}#$repetition OCR failed: ${response.t.message}")
                        return@repeat
                    }
                }

                val recognizedSentences = result.paragraphs
                    .flatMap { it.sentences }
                    .map(Sentence::representation)
                    .map(::normalize)
                val expected = normalize(scene.expected)
                val matched = recognizedSentences.contains(expected)

                if (!matched) {
                    failures.add(
                        buildString {
                            append("${scene.name}#$repetition expected='$expected' ")
                            append("sentences=$recognizedSentences ")
                            append("raw='${normalize(result.text.text)}' ")
                            append("elements='${result.text.elementSnapshot()}'")
                        }
                    )
                }

                Log.i(
                    TAG,
                    "SCENE index=$sceneIndex name=${scene.name} repetition=$repetition " +
                            "matched=$matched expected='$expected' sentences=$recognizedSentences " +
                            "raw='${normalize(result.text.text)}' elements='${result.text.elementSnapshot()}'"
                )
            }
        }

        assertTrue(
            "Ambiguous Latin glyph OCR failures:\n${failures.joinToString(separator = "\n")}",
            failures.isEmpty(),
        )
    }

    private data class Scene(
        val name: String,
        val visualLines: List<String>,
        val expected: String,
        val textSize: Float = 36f,
        val typefaceName: String = "sans-serif",
        val textScaleX: Float = 1.0f,
        val color: Int = Color.rgb(24, 24, 24),
        val fakeBold: Boolean = false,
    ) {
        fun toBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = this@Scene.color
                textSize = this@Scene.textSize
                typeface = Typeface.create(typefaceName, Typeface.NORMAL)
                textScaleX = this@Scene.textScaleX
                isFakeBoldText = fakeBold
                isSubpixelText = true
            }
            var y = 118f
            visualLines.forEach { line ->
                canvas.drawText(line, 48f, y, paint)
                y += textSize * 2.35f
            }
            return bitmap
        }
    }

    private companion object {
        const val TAG = "GlyphAccuracy"
        const val SCREEN_WIDTH = 1280
        const val SCREEN_HEIGHT = 360
        const val REPETITIONS = 10

        val scenes = listOf(
            Scene("apostrophe_i_ll", listOf("I'll review exact glyphs."), "I'll review exact glyphs.", textSize = 34f),
            Scene("pipe_digit_one_l", listOf("Token I1l remains exact."), "Token I1l remains exact.", textSize = 34f),
            Scene("o_zero_pair", listOf("Token O0 remains exact."), "Token O0 remains exact.", textSize = 34f),
            Scene("o_zero_i_one_serial", listOf("Token O0O1I1l remains exact."), "Token O0O1I1l remains exact.", textSize = 34f),
            Scene("ampersand_r_b", listOf("Read R&B before lunch."), "Read R&B before lunch.", textSize = 34f),
            Scene("ampersand_a_b", listOf("Read A&B before lunch."), "Read A&B before lunch.", textSize = 34f),
            Scene("leading_ampersand", listOf("Read &B marker today."), "Read &B marker today.", textSize = 34f),
            Scene("legit_eight_token", listOf("Read B8B marker today."), "Read B8B marker today.", textSize = 34f),
            Scene("cpp_plus_plus", listOf("Use C++ before lunch."), "Use C++ before lunch.", textSize = 34f),
            Scene("c_sharp_hash", listOf("Use C# before lunch."), "Use C# before lunch.", textSize = 34f),
            Scene("at_handle", listOf("Read @user before lunch."), "Read @user before lunch.", textSize = 34f),
            Scene("at_email", listOf("Mail a@b.com before lunch."), "Mail a@b.com before lunch.", textSize = 34f),
            Scene("price_decimal_zero", listOf("Read $5.00 before lunch."), "Read $5.00 before lunch.", textSize = 34f),
            Scene("currency_prefix", listOf("Read S$5 before lunch."), "Read S$5 before lunch.", textSize = 34f),
            Scene("slash_separator", listOf("Read A/B before lunch."), "Read A/B before lunch.", textSize = 34f),
            Scene("backslash_separator", listOf("Read A\\B before lunch."), "Read A\\B before lunch.", textSize = 34f),
            Scene("underscore_separator", listOf("Read X_Y before lunch."), "Read X_Y before lunch.", textSize = 34f),
            Scene("brace_token", listOf("Read {id} before lunch."), "Read {id} before lunch.", textSize = 34f),
            Scene("angle_token", listOf("Read <tag> before lunch."), "Read <tag> before lunch.", textSize = 34f),
            Scene("single_quote_token", listOf("Read 'OK' before lunch."), "Read 'OK' before lunch.", textSize = 34f),
            Scene("double_quote_token", listOf("Read \"OK\" before lunch."), "Read \"OK\" before lunch.", textSize = 34f),
            Scene("double_v_word", listOf("Read vv versus w before lunch."), "Read vv versus w before lunch.", textSize = 34f),
            Scene("s_five_s_token", listOf("Read S5S before lunch."), "Read S5S before lunch.", textSize = 34f),
            Scene("q_nine_q_token", listOf("Read Q9Q before lunch."), "Read Q9Q before lunch.", textSize = 34f),
        )

        fun normalize(text: String): String {
            return text.replace(Regex("\\s+"), " ").trim()
        }

        fun Text.elementSnapshot(): String {
            return textBlocks.joinToString(separator = " | ") { block ->
                block.lines.joinToString(separator = " / ") { line ->
                    line.elements.joinToString(separator = " ") { element ->
                        val symbols = element.symbols.joinToString(separator = "") { it.text }
                        "${element.text}[$symbols]"
                    }
                }
            }
        }
    }
}

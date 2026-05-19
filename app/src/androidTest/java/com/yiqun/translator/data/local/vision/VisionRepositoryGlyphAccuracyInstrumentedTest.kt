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
    fun recognizesTwentyAmbiguousLatinGlyphScenesTenTimesEach() = runBlocking {
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
            Scene("apostrophe_i_ll_leave", listOf("I'll leave it in Lisbon."), "I'll leave it in Lisbon.", textSize = 34f),
            Scene("apostrophe_i_ll_lift", listOf("I'll lift little lines."), "I'll lift little lines.", textSize = 34f),
            Scene("apostrophe_i_ll_call", listOf("I'll call Bill later."), "I'll call Bill later.", textSize = 34f),
            Scene("apostrophe_i_ll_listen", listOf("I'll listen in class."), "I'll listen in class.", textSize = 34f),
            Scene("digit_i_one_l_code", listOf("The code I1l stays readable."), "The code I1l stays readable.", textSize = 34f),
            Scene("digit_i_one_l_id", listOf("The ID I1l is visible."), "The ID I1l is visible.", textSize = 34f),
            Scene("digit_i_one_l_label", listOf("Label I1l ends today."), "Label I1l ends today.", textSize = 34f),
            Scene("digit_i_one_l_token", listOf("Token I1l keeps its tail."), "Token I1l keeps its tail.", textSize = 34f),
            Scene("zero_letter_o_code", listOf("The O0 code opens output."), "The O0 code opens output.", textSize = 34f),
            Scene("zero_letter_o_output", listOf("The O0 output opens today."), "The O0 output opens today.", textSize = 34f),
            Scene("zero_letter_o_label", listOf("Label O0 remains mixed."), "Label O0 remains mixed.", textSize = 34f),
            Scene("zero_letter_o_token", listOf("Token O0 keeps one letter."), "Token O0 keeps one letter.", textSize = 34f),
            Scene("serial_o_zero_i_one", listOf("Serial O0O1I1l keeps identity."), "Serial O0O1I1l keeps identity.", textSize = 34f),
            Scene("serial_o_zero_i_one_label", listOf("Label O0O1I1l stays intact."), "Label O0O1I1l stays intact.", textSize = 34f),
            Scene("serial_o_zero_i_one_token", listOf("Token O0O1I1l is stable."), "Token O0O1I1l is stable.", textSize = 34f),
            Scene("serial_o_zero_i_one_code", listOf("Code O0O1I1l remains exact."), "Code O0O1I1l remains exact.", textSize = 34f),
            Scene("double_l_word_null", listOf("The word null includes ll."), "The word null includes ll.", textSize = 34f),
            Scene("digit_i_one_l_serial", listOf("Serial I1l remains exact."), "Serial I1l remains exact.", textSize = 34f),
            Scene("zero_letter_o_serial", listOf("Serial O0 remains exact."), "Serial O0 remains exact.", textSize = 34f),
            Scene("apostrophe_i_ll_remain", listOf("I'll remain exact."), "I'll remain exact.", textSize = 34f),
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

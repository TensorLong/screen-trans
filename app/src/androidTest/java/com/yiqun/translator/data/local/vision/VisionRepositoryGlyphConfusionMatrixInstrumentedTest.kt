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
class VisionRepositoryGlyphConfusionMatrixInstrumentedTest {

    @Test
    fun recognizesAmbiguousLatinGlyphAndSymbolMatrix() = runBlocking {
        val repository = VisionRepository()
        val failures = mutableListOf<String>()

        cases.forEachIndexed { caseIndex, case ->
            repeat(REPETITIONS) { repetition ->
                val bitmap = case.toBitmap()
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
                        failures.add("${case.name}#$repetition OCR failed: ${response.t.message}")
                        return@repeat
                    }
                }

                val expected = normalize(case.expectedSentence)
                val recognizedSentences = result.paragraphs
                    .flatMap { it.sentences }
                    .map(Sentence::representation)
                    .map(::normalize)
                val recognizedText = normalize(result.paragraphs.joinToString(separator = " ") { it.representation })
                val expectedNeedle = normalize(case.expectedNeedle)
                val matched = recognizedSentences.contains(expected) || recognizedText.contains(expectedNeedle)

                if (!matched) {
                    failures.add(
                        buildString {
                            append("${case.name}#$repetition expected='$expected' ")
                            append("needle='$expectedNeedle' ")
                            append("sentences=$recognizedSentences ")
                            append("text='$recognizedText' ")
                            append("raw='${normalize(result.text.text)}' ")
                            append("elements='${result.text.elementSnapshot()}'")
                        }
                    )
                }

                Log.i(
                    TAG,
                    "CASE index=$caseIndex name=${case.name} repetition=$repetition matched=$matched " +
                            "expected='$expected' needle='$expectedNeedle' sentences=$recognizedSentences text='$recognizedText' raw='${normalize(result.text.text)}' " +
                            "elements='${result.text.elementSnapshot()}'"
                )
            }
        }

        assertTrue(
            "Ambiguous glyph/symbol OCR failures:\n${failures.joinToString(separator = "\n")}",
            failures.isEmpty(),
        )
    }

    private data class MatrixCase(
        val name: String,
        val sentence: String,
        val expectedSentence: String = sentence,
        val expectedNeedle: String = expectedSentence,
        val textSize: Float = 34f,
        val typefaceName: String = "sans-serif",
        val textScaleX: Float = 1.0f,
        val fakeBold: Boolean = false,
    ) {
        fun toBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 24, 24)
                textSize = this@MatrixCase.textSize
                typeface = Typeface.create(typefaceName, Typeface.NORMAL)
                textScaleX = this@MatrixCase.textScaleX
                isFakeBoldText = fakeBold
                isSubpixelText = true
            }
            canvas.drawText(sentence, 48f, 118f, paint)
            return bitmap
        }
    }

    private companion object {
        const val TAG = "GlyphMatrix"
        const val SCREEN_WIDTH = 1320
        const val SCREEN_HEIGHT = 260
        const val REPETITIONS = 3

        val cases = buildList {
            val coreSentences = listOf(
                "Read R&B before lunch." to "R&B",
                "Read R&D before lunch." to "R&D",
                "Read A&B before lunch." to "A&B",
                "Read B&B before lunch." to "B&B",
                "Read B8B marker today." to "B8B",
                "Read R8B marker today." to "R8B",
                "Read &B marker today." to "&B",
                "Use C++ before lunch." to "C++",
                "Use C# before lunch." to "C#",
                "Use F# before lunch." to "F#",
                "Read @user before lunch." to "@user",
                "Mail a@b.com before lunch." to "a@b.com",
                "Read #1 before lunch." to "#1",
                "Read $5.00 before lunch." to "$5.00",
                "Read S$5 before lunch." to "S$5",
                "Read 50% before lunch." to "50%",
                "Read A/B before lunch." to "A/B",
                "Read A\\B before lunch." to "A\\B",
                "Read X_Y before lunch." to "X_Y",
                "Read {id} before lunch." to "{id}",
                "Read <tag> before lunch." to "<tag>",
                "Read 'OK' before lunch." to "'OK'",
                "Read \"OK\" before lunch." to "\"OK\"",
                "Read rn versus m before lunch." to "rn",
                "Read vv versus w before lunch." to "vv",
                "Read cl versus d before lunch." to "cl",
                "Read OQ0D before lunch." to "OQ0D",
                "Read S5S before lunch." to "S5S",
                "Read Z2Z before lunch." to "Z2Z",
                "Read G6G before lunch." to "G6G",
                "Read Q9Q before lunch." to "Q9Q",
            )
            val variants = listOf(
                Variant("sans_34", textSize = 34f, typefaceName = "sans-serif"),
                Variant("sans_28", textSize = 28f, typefaceName = "sans-serif"),
                Variant("sans_24", textSize = 24f, typefaceName = "sans-serif"),
                Variant("sans_condensed", textSize = 34f, typefaceName = "sans-serif-condensed"),
                Variant("sans_narrow", textSize = 34f, typefaceName = "sans-serif", textScaleX = 0.82f),
                Variant("sans_bold", textSize = 34f, typefaceName = "sans-serif", fakeBold = true),
                Variant("serif_34", textSize = 34f, typefaceName = "serif"),
                Variant("mono_34", textSize = 34f, typefaceName = "monospace"),
            )

            coreSentences.forEachIndexed { index, (sentence, expectedNeedle) ->
                variants.forEach { variant ->
                    add(
                        MatrixCase(
                            name = "variant_${index}_${variant.name}",
                            sentence = sentence,
                            expectedNeedle = expectedNeedle,
                            textSize = variant.textSize,
                            typefaceName = variant.typefaceName,
                            textScaleX = variant.textScaleX,
                            fakeBold = variant.fakeBold,
                        )
                    )
                }
            }

            addAll(
                listOf(
            MatrixCase("apostrophe_i_ll", "I'll review exact glyphs."),
            MatrixCase("pipe_l_letter", "Token I1l remains exact."),
            MatrixCase("o_zero_pair", "Token O0 remains exact."),
            MatrixCase("o_zero_serial", "Token O0O1I1l remains exact."),
            MatrixCase("ampersand_r_and_b", "Read R&B before lunch."),
            MatrixCase("ampersand_r_and_d", "Read R&D before lunch."),
            MatrixCase("ampersand_a_and_b", "Read A&B before lunch."),
            MatrixCase("ampersand_b_and_b", "Read B&B before lunch."),
            MatrixCase("ampersand_at_start", "Read &B marker today."),
            MatrixCase("legit_b8b", "Read B8B marker today."),
            MatrixCase("legit_r8b", "Read R8B marker today."),
            MatrixCase("plus_c_plus_plus", "Use C++ before lunch."),
            MatrixCase("hash_c_sharp", "Use C# before lunch."),
            MatrixCase("hash_f_sharp", "Use F# before lunch."),
            MatrixCase("slash_a_b", "Read A/B before lunch."),
            MatrixCase("backslash_a_b", "Read A\\B before lunch."),
            MatrixCase("hyphen_x_y", "Read X-Y before lunch."),
            MatrixCase("underscore_x_y", "Read X_Y before lunch."),
            MatrixCase("at_email", "Mail a@b.com before lunch."),
            MatrixCase("at_handle", "Read @user before lunch."),
            MatrixCase("hash_rank", "Read #1 before lunch."),
            MatrixCase("percent_50", "Read 50% before lunch."),
            MatrixCase("dollar_price", "Read $5.00 before lunch."),
            MatrixCase("dollar_serial", "Read S$5 before lunch."),
            MatrixCase("s_five_pair", "Read S5S before lunch."),
            MatrixCase("z_two_pair", "Read Z2Z before lunch."),
            MatrixCase("b_eight_pair", "Read B8B before lunch."),
            MatrixCase("g_six_pair", "Read G6G before lunch."),
            MatrixCase("q_nine_pair", "Read Q9Q before lunch."),
            MatrixCase("paren_ok", "Read (OK) before lunch."),
            MatrixCase("bracket_ok", "Read [OK] before lunch."),
            MatrixCase("brace_id", "Read {id} before lunch."),
            MatrixCase("angle_tag", "Read <tag> before lunch."),
            MatrixCase("colon_key_value", "Read key:value before lunch."),
            MatrixCase("semicolon_a_b", "Read A;B before lunch."),
            MatrixCase("quote_word", "Read \"OK\" before lunch."),
            MatrixCase("single_quote_word", "Read 'OK' before lunch."),
            MatrixCase("dot_version", "Read v1.2.3 before lunch."),
            MatrixCase("tilde_home", "Read ~/file before lunch."),
            MatrixCase("star_glob", "Read *.kt before lunch."),
                )
            )
        }

        data class Variant(
            val name: String,
            val textSize: Float,
            val typefaceName: String,
            val textScaleX: Float = 1.0f,
            val fakeBold: Boolean = false,
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

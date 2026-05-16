package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yiqun.translator.data.local.vision.model.Sentence
import com.yiqun.translator.data.local.vision.model.VisionResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionRepositoryAccuracyInstrumentedTest {

    @Test
    fun recognizesTwentyVariableSpacingSentencesConsecutively() = runBlocking {
        val repository = VisionRepository()

        scenes.forEachIndexed { index, scene ->
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

            assertTrue("scene $index OCR should succeed", response is VisionResponse.Success)
            val result = (response as VisionResponse.Success).result
            val recognizedSentences = result.paragraphs
                .flatMap { it.sentences }
                .map(Sentence::representation)
                .map { normalize(it) }

            assertTrue(
                "scene $index expected '${normalize(scene.expected)}' in $recognizedSentences raw='${result.text.text}'",
                recognizedSentences.contains(normalize(scene.expected)),
            )
        }
    }

    private data class Scene(
        val visualLines: List<String>,
        val expected: String,
    ) {
        fun toBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 20, 20)
                textSize = 38f
                isSubpixelText = true
            }
            var y = 150f
            visualLines.forEach { line ->
                canvas.drawText(line, 64f, y, paint)
                y += 92f
            }
            return bitmap
        }
    }

    private companion object {
        const val SCREEN_WIDTH = 1600
        const val SCREEN_HEIGHT = 520

        val scenes = listOf(
            Scene(listOf("The   quick brown      fox jumps over the lazy dog."), "The quick brown fox jumps over the lazy dog."),
            Scene(listOf("Careful readers notice     small changes in sentence spacing."), "Careful readers notice small changes in sentence spacing."),
            Scene(listOf("A bright screen shows clear      words under the pointer."), "A bright screen shows clear words under the pointer."),
            Scene(listOf("The team made      a careful decision after review."), "The team made a careful decision after review."),
            Scene(listOf("Simple tools can still solve      difficult translation tasks."), "Simple tools can still solve difficult translation tasks."),
            Scene(listOf("Every mode should keep the complete      sentence available."), "Every mode should keep the complete sentence available."),
            Scene(listOf("Variable spacing must not split      one sentence into pieces."), "Variable spacing must not split one sentence into pieces."),
            Scene(listOf("The target icon points at text      without changing the words."), "The target icon points at text without changing the words."),
            Scene(listOf("Longer passages remain readable      when the gaps are uneven."), "Longer passages remain readable when the gaps are uneven."),
            Scene(listOf("Sentence detection joins words      before translation begins."), "Sentence detection joins words before translation begins."),
            Scene(listOf("The first line carries a clause", "and the second line finishes it."), "The first line carries a clause and the second line finishes it."),
            Scene(listOf("The app reads short text", "then returns the same sentence."), "The app reads short text then returns the same sentence."),
            Scene(listOf("A narrow layout wraps the sentence", "without losing any meaning."), "A narrow layout wraps the sentence without losing any meaning."),
            Scene(listOf("The pointer rests over one word", "but the whole sentence is selected."), "The pointer rests over one word but the whole sentence is selected."),
            Scene(listOf("Uneven gaps appear in the first     half", "and normal spacing appears later."), "Uneven gaps appear in the first half and normal spacing appears later."),
            Scene(listOf("The left segment is separated       from the right segment."), "The left segment is separated from the right segment."),
            Scene(listOf("Reliable text keeps nouns verbs      and objects together."), "Reliable text keeps nouns verbs and objects together."),
            Scene(listOf("This example checks sentence mode      with one long visual line."), "This example checks sentence mode with one long visual line."),
            Scene(listOf("Small words near the      gap must remain in place."), "Small words near the gap must remain in place."),
            Scene(listOf("Many varied scenes pass      one after another."), "Many varied scenes pass one after another."),
        )

        fun normalize(text: String): String {
            return text.replace(Regex("\\s+"), " ").trim()
        }
    }
}

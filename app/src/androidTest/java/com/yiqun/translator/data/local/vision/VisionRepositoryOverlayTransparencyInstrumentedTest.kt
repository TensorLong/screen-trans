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
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class VisionRepositoryOverlayTransparencyInstrumentedTest {

    @Test
    fun transparentTargetOverlayDoesNotCorruptTextRecognition() = runBlocking {
        val repository = VisionRepository()
        val bitmap = createTextWithTransparentTargetOverlay()
        val response = try {
            repository.request(
                bitmap = bitmap,
                sourceLanguageCode = "en",
                autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
            )
        } finally {
            bitmap.recycle()
        }

        assertTrue("OCR should succeed with a transparent target overlay", response is VisionResponse.Success)
        val result = (response as VisionResponse.Success).result
        val recognized = result.paragraphs
            .flatMap { it.sentences }
            .joinToString(separator = " ") { Sentence::representation.invoke(it) }
            .ifBlank { result.text.text }
            .normalize()

        assertTrue(
            "Expected '$EXPECTED_TEXT' with transparent target overlay, got '$recognized' raw='${result.text.text}'",
            recognized.contains(EXPECTED_TEXT),
        )
    }

    private fun createTextWithTransparentTargetOverlay(): Bitmap {
        val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 16, 16)
            textSize = 54f
            isSubpixelText = true
        }
        canvas.drawText(EXPECTED_TEXT, 48f, 150f, textPaint)

        val overlayAlpha = (255f * TARGET_RENDER_ALPHA * PASS_THROUGH_WINDOW_ALPHA).roundToInt()
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(overlayAlpha, 40, 120, 255)
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        val centerX = 430f
        val centerY = 130f
        canvas.drawCircle(centerX, centerY, 50f, overlayPaint)
        canvas.drawLine(centerX - 66f, centerY, centerX + 66f, centerY, overlayPaint)
        canvas.drawLine(centerX, centerY - 66f, centerX, centerY + 66f, overlayPaint)

        return bitmap
    }

    private fun String.normalize(): String = replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val SCREEN_WIDTH = 1200
        const val SCREEN_HEIGHT = 260
        const val EXPECTED_TEXT = "The target icon points at clear words."
        const val TARGET_RENDER_ALPHA = 0.01f
        const val PASS_THROUGH_WINDOW_ALPHA = 0.8f
    }
}

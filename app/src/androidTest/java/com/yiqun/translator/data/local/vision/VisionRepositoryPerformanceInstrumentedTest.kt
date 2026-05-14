package com.yiqun.translator.data.local.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Debug
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class VisionRepositoryPerformanceInstrumentedTest {

    @Test
    fun profileFullScreenPointedOcrLatin() = runBlocking {
        profileScenario(
            label = "source-en",
            sourceLanguageCode = "en",
            autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
            bitmapConfig = Bitmap.Config.RGB_565,
        )
    }

    @Test
    fun profileFullScreenPointedOcrAutoLatinFirst() = runBlocking {
        profileScenario(
            label = "auto-latin-first",
            sourceLanguageCode = "auto",
            autoRecognitionPolicy = AutoRecognitionPolicy.LATIN_FIRST,
            bitmapConfig = Bitmap.Config.RGB_565,
        )
    }

    @Test
    fun profileFullScreenPointedOcrAutoLatinFirstArgb() = runBlocking {
        profileScenario(
            label = "auto-latin-first-argb",
            sourceLanguageCode = "auto",
            autoRecognitionPolicy = AutoRecognitionPolicy.LATIN_FIRST,
            bitmapConfig = Bitmap.Config.ARGB_8888,
        )
    }

    private suspend fun profileScenario(
        label: String,
        sourceLanguageCode: String,
        autoRecognitionPolicy: AutoRecognitionPolicy,
        bitmapConfig: Bitmap.Config,
    ) {
        val repository = VisionRepository()
        val template = createFullScreenTextBitmap()

        val warmupBitmap = template.copy(bitmapConfig, false)
        val warmup = repository.request(
            bitmap = warmupBitmap,
            sourceLanguageCode = sourceLanguageCode,
            autoRecognitionPolicy = autoRecognitionPolicy,
        )
        warmupBitmap.recycle()
        assertTrue(warmup is com.yiqun.translator.data.local.vision.model.VisionResponse.Success)

        Runtime.getRuntime().gc()
        Thread.sleep(250)

        val results = (1..PROFILE_RUNS).map { runIndex ->
            val bitmap = template.copy(bitmapConfig, false)
            try {
                profileRun(
                    runIndex = runIndex,
                    repository = repository,
                    bitmap = bitmap,
                    sourceLanguageCode = sourceLanguageCode,
                    autoRecognitionPolicy = autoRecognitionPolicy,
                )
            } finally {
                bitmap.recycle()
            }
        }

        val medianWallMs = results.map { it.wallMs }.sorted()[results.size / 2]
        val medianCpuMs = results.map { it.cpuMs }.sorted()[results.size / 2]
        val medianPeakPssKb = results.map { it.peakPssKb }.sorted()[results.size / 2]
        val medianDeltaPssKb = results.map { it.deltaPssKb }.sorted()[results.size / 2]
        val maxBlocks = results.maxOf { it.blocks }

        Log.i(
            PERF_TAG,
            "SUMMARY label=$label source=$sourceLanguageCode policy=$autoRecognitionPolicy bitmapConfig=$bitmapConfig runs=$PROFILE_RUNS medianWallMs=$medianWallMs " +
                    "medianCpuMs=$medianCpuMs medianPeakPssKb=$medianPeakPssKb medianDeltaPssKb=$medianDeltaPssKb maxBlocks=$maxBlocks"
        )

        assertTrue("Expected OCR to detect at least one block", maxBlocks > 0)
        template.recycle()
    }

    private suspend fun profileRun(
        runIndex: Int,
        repository: VisionRepository,
        bitmap: Bitmap,
        sourceLanguageCode: String,
        autoRecognitionPolicy: AutoRecognitionPolicy,
    ): ProfileResult {
        val keepSampling = AtomicBoolean(true)
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
        val response = try {
            repository.request(
                bitmap = bitmap,
                sourceLanguageCode = sourceLanguageCode,
                autoRecognitionPolicy = autoRecognitionPolicy,
            )
        } finally {
            keepSampling.set(false)
            sampler.join()
        }
        val wallMs = (System.nanoTime() - startWallNs) / 1_000_000
        val cpuMs = processCpuMs() - startCpuMs
        val blocks = when (response) {
            is com.yiqun.translator.data.local.vision.model.VisionResponse.Success -> response.result.text.textBlocks.size
            is com.yiqun.translator.data.local.vision.model.VisionResponse.Error -> 0
        }

        Log.i(
            PERF_TAG,
            "RUN index=$runIndex source=$sourceLanguageCode policy=$autoRecognitionPolicy wallMs=$wallMs " +
                    "cpuMs=$cpuMs startPssKb=$startPssKb peakPssKb=$peakPssKb deltaPssKb=${peakPssKb - startPssKb} blocks=$blocks"
        )

        return ProfileResult(wallMs, cpuMs, peakPssKb, peakPssKb - startPssKb, blocks)
    }

    private fun createFullScreenTextBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 32, 32)
            textSize = 38f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(64, 64, 64)
            textSize = 30f
        }

        canvas.drawText("Sense Group Translator Settings", 54f, 120f, titlePaint)
        val lines = listOf(
            "Pointer Handle",
            "Pointer Distance  Single",
            "Pointer Docking Delay  Infinity",
            "Haptic Feedback on Detection  Off",
            "Menu Bar",
            "Menu Bar Transparency  10%",
            "Menu Functions",
            "AI API Settings  Not set",
            "Translation",
            "Translation Transparency  25%",
            "Translation Close Delay  1.6 sec",
            "Drag the pointer handle to translate the sentence under the target icon.",
            "This full screen benchmark keeps the OCR recognition area unchanged.",
            "The quick brown fox jumps over the lazy dog.",
            "A long sentence remains visible so sentence and paragraph detection stay intact.",
        )

        var y = 260f
        lines.forEachIndexed { index, text ->
            val paint = if (index % 3 == 0) bodyPaint else smallPaint
            canvas.drawText(text, 70f, y, paint)
            y += if (index % 3 == 0) 132f else 118f
        }

        return bitmap
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

    private data class ProfileResult(
        val wallMs: Long,
        val cpuMs: Long,
        val peakPssKb: Int,
        val deltaPssKb: Int,
        val blocks: Int,
    )

    private companion object {
        const val PERF_TAG = "VisionPerf"
        const val SCREEN_WIDTH = 1080
        const val SCREEN_HEIGHT = 2400
        const val PROFILE_RUNS = 3
    }
}

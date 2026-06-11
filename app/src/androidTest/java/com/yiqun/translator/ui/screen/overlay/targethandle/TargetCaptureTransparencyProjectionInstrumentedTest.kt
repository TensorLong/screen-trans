package com.yiqun.translator.ui.screen.overlay.targethandle

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.capture.CaptureResponse
import com.yiqun.translator.data.local.preference.preferenceDataStore
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.vision.AutoRecognitionPolicy
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.VisionRepository
import com.yiqun.translator.data.local.vision.model.VisionResponse
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import com.yiqun.translator.ui.screen.test.OcrProbeActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TargetCaptureTransparencyProjectionInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var targetView: TargetIconNativeView? = null

    @After
    fun tearDown() {
        targetView?.let { view ->
            runCatching {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
            }
        }
        targetView = null
    }

    @Test
    fun senseGroupProjectionCaptureWaitsForTransparentTargetFrame() = runBlocking {
        prepareAppPermissions()
        configureSenseGroupMode()
        // Token must be granted BEFORE the foreground service starts: on API 34+
        // startForeground(FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) without a
        // projection grant throws SecurityException, and the service's recovery
        // path launches SplashActivity over the probe screen. The probe activity
        // is launched last so it is the captured foreground content.
        ensureMediaProjectionToken()
        startOverlayForegroundService()
        launchProbeActivity()

        val captureRepository = CaptureRepository(context).also { it.acquire() }
        val visionRepository = VisionRepository()
        try {
            val cropRect = probeCropRect()
            val baseline = captureBitmap(captureRepository, cropRect, "baseline")
            val baselineText = recognize(visionRepository, baseline)
            assertProbeTextRecognized("Baseline", baselineText)

            addVisibleTarget()
            val visible = captureBitmap(captureRepository, cropRect, "visible-target")
            val visibleDifference = differenceAroundTarget(baseline, visible)
            assertTargetVisible("Visible target must be present in MediaProjection capture", visibleDifference)

            withContext(Dispatchers.Main.immediate) {
                targetView?.render(TargetIconRenderState(pointerVisible = true, captureRequested = true))
            }
            val immediateTransparent = captureBitmap(captureRepository, cropRect, "immediate-transparent")
            val immediateDifference = differenceAroundTarget(baseline, immediateTransparent)

            withContext(Dispatchers.Main.immediate) {
                targetView?.render(TargetIconRenderState(pointerVisible = true))
            }
            delay(300)

            val synchronizedTransparent = TargetCaptureTransparency.withTransparentTargets(listOfNotNull(targetView)) {
                captureBitmap(captureRepository, cropRect, "synchronized-transparent")
            }
            val synchronizedText = recognize(visionRepository, synchronizedTransparent)
            val synchronizedDifference = differenceAroundTarget(baseline, synchronizedTransparent)

            assertTrue(
                "Synchronized transparent target should be much closer to baseline than the visible target. " +
                        "visible=$visibleDifference immediate=$immediateDifference synchronized=$synchronizedDifference",
                synchronizedDifference.mean < visibleDifference.mean * TRANSPARENT_DIFF_RATIO_LIMIT,
            )
            assertProbeTextRecognized("Synchronized transparent target", synchronizedText)

            baseline.recycle()
            visible.recycle()
            immediateTransparent.recycle()
            synchronizedTransparent.recycle()
        } finally {
            captureRepository.release()
        }
        Unit
    }

    @Test
    fun cancelledTransparentCaptureRestoresVisibleTargetFrame() = runBlocking {
        prepareAppPermissions()
        configureSenseGroupMode()
        // Token must be granted BEFORE the foreground service starts: on API 34+
        // startForeground(FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) without a
        // projection grant throws SecurityException, and the service's recovery
        // path launches SplashActivity over the probe screen. The probe activity
        // is launched last so it is the captured foreground content.
        ensureMediaProjectionToken()
        startOverlayForegroundService()
        launchProbeActivity()

        val captureRepository = CaptureRepository(context).also { it.acquire() }
        try {
            val cropRect = probeCropRect()
            val baseline = captureBitmap(captureRepository, cropRect, "cancel-baseline")

            addVisibleTarget()
            val visible = captureBitmap(captureRepository, cropRect, "cancel-visible-target")
            val visibleDifference = differenceAroundTarget(baseline, visible)
            assertTargetVisible("Visible target must be present before cancellation", visibleDifference)

            val transparentBlockStarted = CompletableDeferred<Unit>()
            val transparentJob = launch {
                TargetCaptureTransparency.withTransparentTargets(listOfNotNull(targetView)) {
                    transparentBlockStarted.complete(Unit)
                    awaitCancellation()
                }
            }
            withTimeout(CAPTURE_TIMEOUT_MS) {
                transparentBlockStarted.await()
            }
            transparentJob.cancelAndJoin()
            delay(200)

            val afterCancel = captureBitmap(captureRepository, cropRect, "cancel-after-restore")
            val afterCancelDifference = differenceAroundTarget(baseline, afterCancel)

            assertTrue(
                "Cancelled transparent capture must restore the visible target. " +
                        "visible=$visibleDifference afterCancel=$afterCancelDifference",
                afterCancelDifference.mean > visibleDifference.mean * RESTORED_TARGET_DIFF_RATIO_LIMIT,
            )

            baseline.recycle()
            visible.recycle()
            afterCancel.recycle()
        } finally {
            captureRepository.release()
        }
        Unit
    }

    @Test
    fun transparentProjectionCaptureResourceProfile() = runBlocking {
        prepareAppPermissions()
        configureSenseGroupMode()
        // Token must be granted BEFORE the foreground service starts: on API 34+
        // startForeground(FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) without a
        // projection grant throws SecurityException, and the service's recovery
        // path launches SplashActivity over the probe screen. The probe activity
        // is launched last so it is the captured foreground content.
        ensureMediaProjectionToken()
        startOverlayForegroundService()
        launchProbeActivity()

        val captureRepository = CaptureRepository(context).also { it.acquire() }
        try {
            val cropRect = probeCropRect()
            addVisibleTarget()

            repeat(2) {
                captureTransparentOnce(captureRepository, cropRect).recycle()
            }
            Runtime.getRuntime().gc()
            delay(250)

            val results = (1..PROFILE_RUNS).map { runIndex ->
                profileTransparentCaptureRun(runIndex, captureRepository, cropRect)
            }
            val medianWallMs = results.map { it.wallMs }.sorted()[results.size / 2]
            val medianCpuMs = results.map { it.cpuMs }.sorted()[results.size / 2]
            val medianPeakPssKb = results.map { it.peakPssKb }.sorted()[results.size / 2]
            val medianDeltaPssKb = results.map { it.deltaPssKb }.sorted()[results.size / 2]

            Log.i(
                PERF_TAG,
                "SUMMARY label=target-transparent-capture runs=$PROFILE_RUNS " +
                        "medianWallMs=$medianWallMs medianCpuMs=$medianCpuMs " +
                        "medianPeakPssKb=$medianPeakPssKb medianDeltaPssKb=$medianDeltaPssKb",
            )
        } finally {
            captureRepository.release()
        }
        Unit
    }

    private suspend fun configureSenseGroupMode() {
        context.preferenceDataStore.edit { preferences ->
            preferences[WAS_TRAILER_SHOWN] = true
            preferences[TEXT_DETECT_MODE] = TextDetectMode.SENSE_GROUP.name
            preferences[SOURCE_LANGUAGE_CODE] = "en"
            preferences[TARGET_LANGUAGE_CODE] = "zh-CN"
        }
    }

    private suspend fun launchProbeActivity() {
        context.startActivity(
            Intent(context, OcrProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        instrumentation.waitForIdleSync()
        // The immersive system-bar transition shifts the window content by a few
        // pixels for ~1s after launch; captures taken across that transition differ
        // at every glyph edge and drown out the target-icon diff.
        delay(2_000)
        ScreenInfoHolder.updateScreenInfoInService(context)
    }

    private fun prepareAppPermissions() {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("cmd appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("cmd appops set ${context.packageName} PROJECT_MEDIA allow")
        // The probe activity uses immersive fullscreen; without this the system's
        // "Viewing full screen" education bubble covers the capture region.
        shell("settings put secure immersive_mode_confirmations confirmed")
    }

    private suspend fun startOverlayForegroundService() {
        val intent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        delay(500)
    }

    private suspend fun ensureMediaProjectionToken() {
        if (CaptureRepository.mediaProjectionToken != null) return

        // The consent-dialog tap coordinates need real screen dimensions, and this
        // now runs before launchProbeActivity (which used to populate the holder).
        ScreenInfoHolder.updateScreenInfoInService(context)
        context.startActivity(
            Intent(context, ScreenCapturePermissionRequesterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val screenInfo = ScreenInfoHolder.get()
        repeat(10) {
            delay(500)
            if (CaptureRepository.mediaProjectionToken != null) return
            val tapX = (screenInfo.width * 0.78f).toInt().coerceAtLeast(1)
            val tapY = (screenInfo.height * 0.88f).toInt().coerceAtLeast(1)
            shell("input tap $tapX $tapY")
        }

        assertTrue(
            "MediaProjection token was not granted for the real projection test",
            CaptureRepository.mediaProjectionToken != null,
        )
    }

    private suspend fun addVisibleTarget() {
        val targetDimen = context.resources.getDimensionPixelSize(R.dimen.target_pointer_dimen)
        val params = WindowManager.LayoutParams(
            targetDimen,
            targetDimen,
            OcrProbeActivity.TARGET_CENTER_X - targetDimen / 2,
            OcrProbeActivity.TARGET_CENTER_Y - targetDimen / 2,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = OverlayWindowAlpha.passThroughVisibleAlpha
        }

        withContext(Dispatchers.Main.immediate) {
            targetView = TargetIconNativeView(context).apply {
                render(TargetIconRenderState(pointerVisible = true))
            }
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .addView(targetView, params)
        }
        delay(500)
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun probeCropRect(): Rect {
        val screenInfo = ScreenInfoHolder.get()
        return Rect(
            0,
            OcrProbeActivity.CROP_TOP,
            screenInfo.width,
            OcrProbeActivity.CROP_BOTTOM.coerceAtMost(screenInfo.height),
        )
    }

    private suspend fun captureBitmap(
        captureRepository: CaptureRepository,
        cropRect: Rect,
        label: String,
    ): Bitmap {
        val response = withTimeout(CAPTURE_TIMEOUT_MS) {
            captureRepository.request(cropRect)
        }
        assertTrue("Capture '$label' should succeed, got $response", response is CaptureResponse.Success)
        val bitmap = (response as CaptureResponse.Success).bitmap
        writeBitmap(bitmap, label)
        return bitmap
    }

    private suspend fun captureTransparentOnce(
        captureRepository: CaptureRepository,
        cropRect: Rect,
    ): Bitmap {
        val response = TargetCaptureTransparency.withTransparentTargets(listOfNotNull(targetView)) {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                captureRepository.request(cropRect)
            }
        }
        assertTrue("Transparent capture should succeed, got $response", response is CaptureResponse.Success)
        return (response as CaptureResponse.Success).bitmap
    }

    private suspend fun profileTransparentCaptureRun(
        runIndex: Int,
        captureRepository: CaptureRepository,
        cropRect: Rect,
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
        val bitmap = try {
            captureTransparentOnce(captureRepository, cropRect)
        } finally {
            keepSampling.set(false)
            sampler.join()
        }
        bitmap.recycle()

        val result = ProfileResult(
            wallMs = (System.nanoTime() - startWallNs) / 1_000_000,
            cpuMs = processCpuMs() - startCpuMs,
            peakPssKb = peakPssKb,
            deltaPssKb = peakPssKb - startPssKb,
        )

        Log.i(
            PERF_TAG,
            "RUN index=$runIndex label=target-transparent-capture wallMs=${result.wallMs} " +
                    "cpuMs=${result.cpuMs} startPssKb=$startPssKb peakPssKb=${result.peakPssKb} " +
                    "deltaPssKb=${result.deltaPssKb}",
        )
        return result
    }

    private suspend fun recognize(
        visionRepository: VisionRepository,
        bitmap: Bitmap,
    ): String {
        val recognitionBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val response = try {
            visionRepository.request(
                bitmap = recognitionBitmap,
                sourceLanguageCode = "en",
                autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
            )
        } finally {
            recognitionBitmap.recycle()
        }
        assertTrue("OCR should succeed", response is VisionResponse.Success)
        return (response as VisionResponse.Success).result.text.text
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun assertProbeTextRecognized(label: String, recognizedText: String) {
        assertTrue(
            "$label OCR should read the probe sentence, got '$recognizedText'",
            normalizedOcrText(recognizedText).contains(normalizedOcrText(OcrProbeActivity.EXPECTED_TEXT)),
        )
    }

    private fun normalizedOcrText(text: String): String {
        return text.lowercase().filter { it.isLetterOrDigit() }
    }

    private fun assertTargetVisible(label: String, difference: TargetDifference) {
        assertTrue(
            "$label; diff=$difference",
            difference.mean > VISIBLE_TARGET_MEAN_DIFF_THRESHOLD ||
                    difference.maxChannelSum > VISIBLE_TARGET_MAX_CHANNEL_SUM_THRESHOLD,
        )
    }

    private fun differenceAroundTarget(first: Bitmap, second: Bitmap): TargetDifference {
        val cropTop = OcrProbeActivity.CROP_TOP
        val centerX = OcrProbeActivity.TARGET_CENTER_X.coerceIn(0, first.width - 1)
        val centerY = (OcrProbeActivity.TARGET_CENTER_Y - cropTop).coerceIn(0, first.height - 1)
        val radius = context.resources.getDimensionPixelSize(R.dimen.target_pointer_dimen)
        val left = (centerX - radius).coerceAtLeast(0)
        val top = (centerY - radius).coerceAtLeast(0)
        val right = (centerX + radius).coerceAtMost(minOf(first.width, second.width))
        val bottom = (centerY + radius).coerceAtMost(minOf(first.height, second.height))

        var sum = 0L
        var count = 0
        var maxChannelSum = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                val redDiff = kotlin.math.abs(Color.red(a) - Color.red(b))
                val greenDiff = kotlin.math.abs(Color.green(a) - Color.green(b))
                val blueDiff = kotlin.math.abs(Color.blue(a) - Color.blue(b))
                sum += redDiff
                sum += greenDiff
                sum += blueDiff
                maxChannelSum = maxOf(maxChannelSum, redDiff + greenDiff + blueDiff)
                count += 3
            }
        }
        return TargetDifference(
            mean = if (count == 0) 0.0 else sum.toDouble() / count.toDouble(),
            maxChannelSum = maxChannelSum,
        )
    }

    private fun writeBitmap(bitmap: Bitmap, label: String) {
        val outputDir = File(context.getExternalFilesDir(null), "projection-probe").apply { mkdirs() }
        FileOutputStream(File(outputDir, "$label.png")).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
            reader.readText()
        }.also {
            descriptor.close()
        }
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

    private companion object {
        const val PERF_TAG = "TargetCapturePerf"
        val WAS_TRAILER_SHOWN = booleanPreferencesKey("was_trailer_shown")
        val TEXT_DETECT_MODE = stringPreferencesKey("text_detect_mode")
        val SOURCE_LANGUAGE_CODE = stringPreferencesKey("source_language_code")
        val TARGET_LANGUAGE_CODE = stringPreferencesKey("target_language_code")
        const val VISIBLE_TARGET_MEAN_DIFF_THRESHOLD = 1.0
        const val VISIBLE_TARGET_MAX_CHANNEL_SUM_THRESHOLD = 60
        const val TRANSPARENT_DIFF_RATIO_LIMIT = 0.35
        const val RESTORED_TARGET_DIFF_RATIO_LIMIT = 0.70
        const val CAPTURE_TIMEOUT_MS = 10_000L
        const val PROFILE_RUNS = 5
    }

    private data class TargetDifference(
        val mean: Double,
        val maxChannelSum: Int,
    )

    private data class ProfileResult(
        val wallMs: Long,
        val cpuMs: Long,
        val peakPssKb: Int,
        val deltaPssKb: Int,
    )
}

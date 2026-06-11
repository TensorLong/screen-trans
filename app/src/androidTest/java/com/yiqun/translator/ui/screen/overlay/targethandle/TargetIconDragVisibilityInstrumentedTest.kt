package com.yiqun.translator.ui.screen.overlay.targethandle

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.preference.preferenceDataStore
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import com.yiqun.translator.ui.screen.test.OcrProbeActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * End-to-end regression test for the "recognition box disappears while dragging"
 * bug on sub-60fps / janky devices.
 *
 * A real drag is injected over real text (OcrProbeActivity) as raw MotionEvents
 * whose delivery gaps exceed the dwell delay, reproducing the sparse touch-event
 * stream of low-end devices. With status-driven hiding (the old behavior) a false
 * dwell mid-drag left the target icon invisible for whole abort-restart cycles;
 * with frame-synchronized hiding the icon may only blink during an actual capture
 * window.
 */
@RunWith(AndroidJUnit4::class)
class TargetIconDragVisibilityInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun iconStaysVisibleDuringJankyDragOverText() = runBlocking {
        prepareAppPermissions()
        configurePointedMode()
        ensureMediaProjectionToken()
        startOverlayForegroundService()
        launchProbeActivity()
        // The production flow casts the handle from the settings/menu UI; the
        // service alone does not show it.
        TargetHandleView.INSTANCE.cast(context)
        waitForTargetIconViews()

        val samples = mutableListOf<Pair<Long, Boolean>>()
        var sawPointerInteraction = false
        var attempts = 0
        var lastStart = 0 to 0
        // The handle can still be repositioned by async collectors right after
        // cast() on a fresh install, so wait for its position to settle, then
        // drag; if the drag never engages (position moved between the read and
        // the injection), re-read and retry.
        while (!sawPointerInteraction && attempts < MAX_DRAG_ATTEMPTS) {
            attempts++
            val (handleStartX, handleStartY) = awaitStableHandleCenter()
            lastStart = handleStartX to handleStartY

            // Slow drag from the handle's real position up toward the probe text,
            // injected as raw MotionEvents with deliberate delivery gaps longer
            // than the dwell delay — the touch stream of a sub-60fps device under
            // jank. Gaps mid-motion produce false dwells (and capture attempts)
            // mid-drag.
            val injector = Thread {
                val endX = OcrProbeActivity.TARGET_CENTER_X
                val endY = OcrProbeActivity.TARGET_CENTER_Y + 600
                val downTime = SystemClock.uptimeMillis()
                injectMotion(MotionEvent.ACTION_DOWN, downTime, handleStartX.toFloat(), handleStartY.toFloat())
                val steps = 24
                for (step in 1..steps) {
                    val x = handleStartX + (endX - handleStartX) * step / steps.toFloat()
                    val y = handleStartY + (endY - handleStartY) * step / steps.toFloat()
                    injectMotion(MotionEvent.ACTION_MOVE, downTime, x, y)
                    // Every 6th move pauses well past the 150ms dwell delay mid-motion.
                    SystemClock.sleep(if (step % 6 == 0) 320L else 40L)
                }
                injectMotion(MotionEvent.ACTION_UP, downTime, endX.toFloat(), endY.toFloat())
            }.apply { start() }

            samples.clear()
            try {
                val samplingEndUptime = SystemClock.uptimeMillis() + SWIPE_DURATION_MS + POST_SWIPE_SAMPLE_MS
                while (SystemClock.uptimeMillis() < samplingEndUptime) {
                    var anyIconHidden = false
                    instrumentation.runOnMainSync {
                        val views = TargetHandleView.targetIconViews()
                        anyIconHidden = views.isNotEmpty() && views.any { view ->
                            view.isCaptureTransparentForTest ||
                                    TargetIconRenderPolicy.contentAlpha(view.renderStateForTest) == 0f
                        }
                        if (views.any { it.renderStateForTest.pointerVisible }) {
                            sawPointerInteraction = true
                        }
                    }
                    samples.add(SystemClock.uptimeMillis() to anyIconHidden)
                    SystemClock.sleep(SAMPLE_INTERVAL_MS)
                }
            } finally {
                injector.join()
            }
        }

        assertTrue("Sampling should have produced data", samples.size >= 10)
        // Guards against a swipe that misses the handle's touchable window, which
        // would make the visibility assertions below trivially true.
        assertTrue(
            "The injected swipe never engaged the drag handle " +
                    "(attempts=$attempts, lastStart=$lastStart)",
            sawPointerInteraction,
        )

        val hiddenCount = samples.count { it.second }
        val hiddenFraction = hiddenCount.toDouble() / samples.size

        var longestHiddenStreakMs = 0L
        var streakStart: Long? = null
        samples.forEach { (timestamp, hidden) ->
            if (hidden) {
                if (streakStart == null) streakStart = timestamp
                longestHiddenStreakMs = maxOf(longestHiddenStreakMs, timestamp - streakStart!!)
            } else {
                streakStart = null
            }
        }

        var finalHidden = true
        instrumentation.runOnMainSync {
            val views = TargetHandleView.targetIconViews()
            finalHidden = views.any { view ->
                view.isCaptureTransparentForTest ||
                        TargetIconRenderPolicy.contentAlpha(view.renderStateForTest) == 0f
            }
        }

        // The old status-driven hide left the icon invisible for entire
        // abort-restart cycles (multi-second streaks, stuck after the drag).
        // Frame-synchronized hiding only blinks for the actual capture window.
        assertTrue(
            "Icon hidden too long during janky drag: longest streak ${longestHiddenStreakMs}ms " +
                    "(samples=${samples.size}, hiddenFraction=$hiddenFraction)",
            longestHiddenStreakMs <= MAX_HIDDEN_STREAK_MS,
        )
        assertTrue(
            "Icon hidden for too large a fraction of the drag: $hiddenFraction",
            hiddenFraction <= MAX_HIDDEN_FRACTION,
        )
        assertTrue("Icon must be visible after the drag settles", !finalHidden)
    }

    private suspend fun configurePointedMode() {
        context.preferenceDataStore.edit { preferences ->
            preferences[WAS_TRAILER_SHOWN] = true
            preferences[TEXT_DETECT_MODE] = TextDetectMode.SENSE_GROUP.name
            preferences[SOURCE_LANGUAGE_CODE] = "en"
            preferences[TARGET_LANGUAGE_CODE] = "zh-CN"
        }
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
        delay(1_000)
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

    private suspend fun ensureMediaProjectionToken() {
        if (CaptureRepository.mediaProjectionToken != null) return

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
            "MediaProjection token was not granted",
            CaptureRepository.mediaProjectionToken != null,
        )
    }

    private suspend fun awaitStableHandleCenter(): Pair<Int, Int> {
        var previous: Pair<Int, Int>? = null
        repeat(20) {
            var current = 0 to 0
            instrumentation.runOnMainSync {
                current = TargetHandleView.INSTANCE.handleCenterOnScreenForTest
            }
            if (current == previous) return current
            previous = current
            delay(500)
        }
        return previous ?: (0 to 0)
    }

    private suspend fun waitForTargetIconViews() {
        repeat(20) {
            var present = false
            instrumentation.runOnMainSync {
                present = TargetHandleView.targetIconViews().isNotEmpty()
            }
            if (present) return
            delay(500)
        }
        assertTrue("Target icon views never appeared after starting OverlayService", false)
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
            reader.readText()
        }.also {
            descriptor.close()
        }
    }

    private fun injectMotion(action: Int, downTime: Long, x: Float, y: Float) {
        val eventTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            instrumentation.uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    private companion object {
        val WAS_TRAILER_SHOWN = booleanPreferencesKey("was_trailer_shown")
        val TEXT_DETECT_MODE = stringPreferencesKey("text_detect_mode")
        val SOURCE_LANGUAGE_CODE = stringPreferencesKey("source_language_code")
        val TARGET_LANGUAGE_CODE = stringPreferencesKey("target_language_code")

        const val SWIPE_DURATION_MS = 2_500L
        const val POST_SWIPE_SAMPLE_MS = 1_500L
        const val SAMPLE_INTERVAL_MS = 25L
        const val MAX_DRAG_ATTEMPTS = 3

        // Frame-synchronized hide budget at 60Hz is ~83+120ms plus the capture wait
        // (700ms watchdog worst case); anything beyond ~1.2s means a status-driven
        // hide leaked outside the capture window again.
        const val MAX_HIDDEN_STREAK_MS = 1_200L
        const val MAX_HIDDEN_FRACTION = 0.5
    }
}

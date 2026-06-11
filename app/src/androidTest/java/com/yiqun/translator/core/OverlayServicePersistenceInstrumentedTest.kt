package com.yiqun.translator.core

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiqun.translator.data.local.capture.CaptureRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * Regression tests for the "screen lock kills everything and the user redoes
 * the whole authorization flow" problem.
 *
 * Before the fix, OverlayService.onStartCommand on API 34+ always promoted
 * itself with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION; coming up without a
 * projection grant (the exact state after process death) threw
 * SecurityException, which launched SplashActivity and stopped the service.
 * Now the service stays in the foreground as specialUse and upgrades once the
 * user re-grants capture with a single system dialog.
 */
@RunWith(AndroidJUnit4::class)
class OverlayServicePersistenceInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun serviceStaysForegroundWithoutProjectionGrant() = runBlocking {
        prepareAppPermissions()
        CaptureRepository.mediaProjectionToken = null

        val intent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // The old behavior stopped the service (and bounced to SplashActivity)
        // within onStartCommand; give it ample time to do so before asserting.
        delay(3_000)

        assertTrue(
            "OverlayService must keep running without a projection grant",
            OverlayService.isRunning,
        )
        val dump = shell("dumpsys activity services ${context.packageName}/.core.OverlayService")
        assertTrue(
            "OverlayService must hold foreground status without a projection grant:\n$dump",
            dump.contains("isForeground=true"),
        )

        // Cleanup so later tests in the same instrumentation run start clean.
        context.stopService(intent)
        delay(500)
    }

    private fun prepareAppPermissions() {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("cmd appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
            reader.readText()
        }.also {
            descriptor.close()
        }
    }
}

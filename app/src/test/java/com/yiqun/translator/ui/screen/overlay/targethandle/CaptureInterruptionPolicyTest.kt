package com.yiqun.translator.ui.screen.overlay.targethandle

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureInterruptionPolicyTest {

    @Test
    fun dwellInterruptionReleasesRequestedCaptureHide() {
        assertEquals(
            "An aborted capture must release the render-level hide so the target icon reappears",
            CaptureStatus.Idle,
            CaptureInterruptionPolicy.statusAfterDwellInterruption(CaptureStatus.Requested),
        )
    }

    @Test
    fun dwellInterruptionKeepsIdleState() {
        assertEquals(
            CaptureStatus.Idle,
            CaptureInterruptionPolicy.statusAfterDwellInterruption(CaptureStatus.Idle),
        )
    }

    @Test
    fun dwellInterruptionPreservesPermissionRequest() {
        assertEquals(
            "Pending projection permission flow must survive pointer movement",
            CaptureStatus.PermissionRequested,
            CaptureInterruptionPolicy.statusAfterDwellInterruption(CaptureStatus.PermissionRequested),
        )
    }

    @Test
    fun dwellInterruptionPreservesCapturedState() {
        assertEquals(
            CaptureStatus.Captured,
            CaptureInterruptionPolicy.statusAfterDwellInterruption(CaptureStatus.Captured),
        )
    }
}

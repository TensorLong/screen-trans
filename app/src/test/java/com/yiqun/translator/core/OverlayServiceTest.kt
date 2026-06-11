package com.yiqun.translator.core

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayServiceTest {

    @Test
    fun withoutProjectionGrantServiceRunsAsSpecialUseOnly() {
        // API 34+ rejects the mediaProjection type before the user grants
        // capture; specialUse keeps the service alive across that gap.
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            OverlayService.foregroundServiceTypes(projectionGranted = false),
        )
    }

    @Test
    fun withProjectionGrantServiceCarriesMediaProjectionType() {
        val types = OverlayService.foregroundServiceTypes(projectionGranted = true)
        assertTrue(types and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION != 0)
        // specialUse stays set so a later projection stop never leaves the
        // service without a legal foreground type.
        assertTrue(types and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0)
    }
}

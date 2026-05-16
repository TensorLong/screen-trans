package com.yiqun.translator.ui.screen.overlay.menubar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuBarAttachmentPolicyTest {

    @Test
    fun attachesOnlyForSettingsHome() {
        assertTrue(
            MenuBarAttachmentPolicy.shouldAttach(
                activityLive = true,
                surface = SettingsSurface.HOME,
            )
        )
        assertFalse(
            MenuBarAttachmentPolicy.shouldAttach(
                activityLive = true,
                surface = SettingsSurface.POINTER_DISTANCE,
            )
        )
        assertFalse(
            MenuBarAttachmentPolicy.shouldAttach(
                activityLive = true,
                surface = SettingsSurface.AI_API,
            )
        )
        assertFalse(
            MenuBarAttachmentPolicy.shouldAttach(
                activityLive = false,
                surface = SettingsSurface.HOME,
            )
        )
    }
}

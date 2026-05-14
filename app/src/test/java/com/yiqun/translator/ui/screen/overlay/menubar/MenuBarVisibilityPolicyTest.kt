package com.yiqun.translator.ui.screen.overlay.menubar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuBarVisibilityPolicyTest {

    @Test
    fun settingsMenuBarIsVisibleOnlyOnSettingsHomeSurface() {
        assertTrue(
            MenuBarVisibilityPolicy.visibleInSettings(
                activityLive = true,
                surface = SettingsSurface.HOME,
            )
        )

        assertFalse(
            MenuBarVisibilityPolicy.visibleInSettings(
                activityLive = true,
                surface = SettingsSurface.POINTER_DISTANCE,
            )
        )

        assertFalse(
            MenuBarVisibilityPolicy.visibleInSettings(
                activityLive = true,
                surface = SettingsSurface.AI_API,
            )
        )
    }

    @Test
    fun settingsSurfaceIsIgnoredWhenSettingsActivityIsNotLive() {
        assertFalse(
            MenuBarVisibilityPolicy.visibleInSettings(
                activityLive = false,
                surface = SettingsSurface.HOME,
            )
        )
    }
}

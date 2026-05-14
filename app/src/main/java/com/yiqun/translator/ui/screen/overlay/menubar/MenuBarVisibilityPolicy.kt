package com.yiqun.translator.ui.screen.overlay.menubar

enum class SettingsSurface {
    HOME,
    POINTER_DISTANCE,
    AI_API,
}

object MenuBarVisibilityPolicy {
    fun visibleInSettings(
        activityLive: Boolean,
        surface: SettingsSurface,
    ): Boolean {
        return activityLive && surface == SettingsSurface.HOME
    }
}

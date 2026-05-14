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
        pointerInteractionActive: Boolean = false,
    ): Boolean {
        return activityLive && surface == SettingsSurface.HOME && !pointerInteractionActive
    }

    fun visibleOutsideSettings(
        captureRequested: Boolean,
        pointerInteractionActive: Boolean,
        dragHandleDocked: Boolean,
        menuHandling: Boolean,
        fixedAreaTranslating: Boolean,
    ): Boolean {
        return !captureRequested &&
                !pointerInteractionActive &&
                (!dragHandleDocked || menuHandling) &&
                !fixedAreaTranslating
    }
}

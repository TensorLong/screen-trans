package com.yiqun.translator.ui.screen.overlay

import com.yiqun.translator.ui.screen.overlay.detectmode.DetectModeMenuView
import com.yiqun.translator.ui.screen.overlay.fixedarea.FixedAreaTranslationView
import com.yiqun.translator.ui.screen.overlay.fixedarea.FixedAreaView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarView
import com.yiqun.translator.ui.screen.overlay.selection.AreaSelectionView
import com.yiqun.translator.ui.screen.overlay.targethandle.SayHereView
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleView
import com.yiqun.translator.ui.screen.overlay.translation.TranslationView
import com.yiqun.translator.ui.screen.overlay.visiontext.VisionTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object OverlayCaptureOcclusion {
    suspend fun <T> withHiddenOverlays(block: suspend () -> T): T {
        withContext(Dispatchers.Main.immediate) {
            setOccluded(true)
        }
        return try {
            block()
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                setOccluded(false)
            }
        }
    }

    private fun setOccluded(occluded: Boolean) {
        TargetHandleView.setCaptureOccludedAll(occluded)
        MenuBarView.INSTANCE.setCaptureOccluded(occluded)
        DetectModeMenuView.INSTANCE.setCaptureOccluded(occluded)
        SayHereView.INSTANCE.setCaptureOccluded(occluded)
        VisionTextView.INSTANCE.setCaptureOccluded(occluded)
        TranslationView.INSTANCE.setCaptureOccluded(occluded)
        AreaSelectionView.INSTANCE.setCaptureOccluded(occluded)
        FixedAreaView.INSTANCE.setCaptureOccluded(occluded)
        FixedAreaTranslationView.INSTANCE.setCaptureOccluded(occluded)
    }
}

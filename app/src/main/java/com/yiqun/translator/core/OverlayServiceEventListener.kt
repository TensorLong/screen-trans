package com.yiqun.translator.core

import com.yiqun.translator.ui.screen.overlay.Event

interface OverlayServiceEventListener {
    fun onOverlayServiceEvent(overlayService: OverlayService, event: Event)
}
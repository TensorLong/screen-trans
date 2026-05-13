package com.galaxy.airviewdictionary.core

import com.galaxy.airviewdictionary.ui.screen.overlay.Event

interface OverlayServiceEventListener {
    fun onOverlayServiceEvent(overlayService: OverlayService, event: Event)
}
package com.yiqun.translator.data.remote.translation.goolge

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleWebKitTest {

    @Test
    fun googleTranslationUsesLightweightPublicEndpoint() {
        assertEquals("https://translate.googleapis.com/", GoogleWebKit.BASE_URL)
    }
}

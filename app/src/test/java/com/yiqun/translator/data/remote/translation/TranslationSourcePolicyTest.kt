package com.yiqun.translator.data.remote.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationSourcePolicyTest {

    @Test
    fun `auto user source passes auto through to auto-capable engines`() {
        for (kit in listOf(
            TranslationKitType.GOOGLE,
            TranslationKitType.AZURE,
            TranslationKitType.DEEPL,
            TranslationKitType.PAPAGO,
        )) {
            assertEquals(
                "auto",
                TranslationSourcePolicy.requestSourceLanguageCode(
                    userSourceLanguageCode = "auto",
                    detectedLanguageCode = "en",
                    translationKitType = kit,
                ),
            )
        }
    }

    @Test
    fun `offline kit keeps the on-device guess because it has no auto mode`() {
        assertEquals(
            "es",
            TranslationSourcePolicy.requestSourceLanguageCode(
                userSourceLanguageCode = "auto",
                detectedLanguageCode = "es",
                translationKitType = TranslationKitType.GOOGLE_OFFLINE,
            ),
        )
    }

    @Test
    fun `explicit user source keeps the detected code path untouched`() {
        assertEquals(
            "ja",
            TranslationSourcePolicy.requestSourceLanguageCode(
                userSourceLanguageCode = "ja",
                detectedLanguageCode = "ja",
                translationKitType = TranslationKitType.GOOGLE,
            ),
        )
    }
}

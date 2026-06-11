package com.yiqun.translator.data.local.preference

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DefaultLanguagePolicyTest {

    @Test
    fun `source default is auto`() {
        assertEquals("auto", DefaultLanguagePolicy.DEFAULT_SOURCE_LANGUAGE_CODE)
    }

    @Test
    fun `plain languages pass through as ISO-639-1`() {
        assertEquals("en", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale.ENGLISH))
        assertEquals("ja", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale.JAPANESE))
        assertEquals("de", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale.GERMANY))
        assertEquals("ko", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale("ko", "KR")))
    }

    @Test
    fun `simplified chinese script maps to zh-CN`() {
        val locale = Locale.Builder().setLanguage("zh").setScript("Hans").build()
        assertEquals("zh-CN", DefaultLanguagePolicy.defaultTargetLanguageCode(locale))
    }

    @Test
    fun `traditional chinese script maps to zh-TW regardless of region`() {
        val locale = Locale.Builder().setLanguage("zh").setScript("Hant").setRegion("CN").build()
        assertEquals("zh-TW", DefaultLanguagePolicy.defaultTargetLanguageCode(locale))
    }

    @Test
    fun `chinese without script falls back to region`() {
        assertEquals("zh-CN", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale("zh", "CN")))
        assertEquals("zh-TW", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale("zh", "TW")))
        assertEquals("zh-TW", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale("zh", "HK")))
        assertEquals("zh-TW", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale("zh", "MO")))
    }

    @Test
    fun `bare chinese defaults to simplified`() {
        assertEquals("zh-CN", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale("zh")))
    }

    @Test
    fun `empty language falls back to english`() {
        assertEquals("en", DefaultLanguagePolicy.defaultTargetLanguageCode(Locale.ROOT))
    }
}

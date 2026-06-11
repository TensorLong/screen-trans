package com.yiqun.translator.data.local.preference

import java.util.Locale

/**
 * Single source of truth for the out-of-box translation language pair.
 *
 * Source defaults to "auto": on-screen content is in an unknown language, so
 * asking the user to declare it up front is wrong by design. Every bundled
 * kit (Google, Azure, Papago) accepts "auto" and the OCR layer has its own
 * auto-recognition policy.
 *
 * Target defaults to the device locale, mapped onto the codes the translation
 * kits actually ship: plain ISO-639-1 except Chinese, which the kits split
 * into zh-CN/zh-TW by script (preferred) or region.
 */
object DefaultLanguagePolicy {

    const val DEFAULT_SOURCE_LANGUAGE_CODE = "auto"

    private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

    fun defaultTargetLanguageCode(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isEmpty()) return "en"
        if (language != "zh") return language
        return when {
            locale.script.equals("Hant", ignoreCase = true) -> "zh-TW"
            locale.script.equals("Hans", ignoreCase = true) -> "zh-CN"
            locale.country.uppercase(Locale.ROOT) in TRADITIONAL_CHINESE_REGIONS -> "zh-TW"
            else -> "zh-CN"
        }
    }
}

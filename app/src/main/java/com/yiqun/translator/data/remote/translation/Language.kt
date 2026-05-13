package com.yiqun.translator.data.remote.translation

import com.yiqun.translator.data.local.vision.WritingDirection
import java.util.Locale

/**
 * Holds the language code (i.e. "en") and the corresponding localized full language name
 * (i.e. "English")
 */
class Language(val code: String) : Comparable<Language> {

    val displayName: String = displayNameMap[code.uppercase()] ?: Locale(code).displayName

    val displayShortName: String = code.uppercase().substring(0, 2)

    val localDisplayName: String = Locale(code).getDisplayLanguage(Locale(code))

    /**
     */
    val isNonSpacingLanguage: Boolean by lazy {
        isNonSpacingLanguage(code)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is Language) {
            return false
        }

        return other.code.equals(code, ignoreCase = true)
    }


    val supportKitTypes: MutableList<TranslationKitType> = mutableListOf()

    override fun toString(): String {
        return "$code $displayName $supportKitTypes"
    }

    override fun compareTo(other: Language): Int {
        return this.displayName.compareTo(other.displayName)
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }


    companion object {

        /**
         *
         */
        fun isVerticalWritingSupported(languageCode: String): Boolean {
            val verticalWritingSupportedLanguages = listOf(
                "zh",
                "zh-CN", // Chinese (simplified)
                "zh-TW", // Chinese (traditional)
                "ZH-HANS", // Chinese (simplified)
                "ZH-HANT", // Chinese (traditional)
                "zh-Hans", // Chinese (simplified)
                "zh-Hant", // Chinese (traditional)
                "ja",
                "ko",
                "mn",
                "bo",
                "vi",
                "kk",
                "mnc",
                "ug",
                "tk"
            )

            return verticalWritingSupportedLanguages.any { code ->
                languageCode.startsWith(code)
            }
        }

        /**
         * @param languageCode
         */
        fun writingDirection(languageCode: String, isVerticalWriting: Boolean): WritingDirection {
            val rtlLanguages = listOf("ar", "he", "fa", "ur", "ps", "sd", "ckb", "dv", "ug")

            return when {
                isVerticalWriting -> {
                    when (languageCode) {
                        "zh", "zh-CN", "zh-TW", "ZH-HANS", "ZH-HANT", "zh-Hans", "zh-Hant", "ja", "ko", "vi" -> WritingDirection.TTB_RTL
                        "mn", "bo", "mnc" -> WritingDirection.TTB_LTR
                        "kk", "ug", "tk" -> WritingDirection.TTB_RTL // Assuming vertical writing follows RTL when using Arabic script
                        else -> WritingDirection.TTB_RTL
                    }
                }

                rtlLanguages.any { code -> languageCode.startsWith(code) } -> WritingDirection.RTL
                else -> WritingDirection.LTR
            }
        }

        /**
         */
        fun isNonSpacingLanguage(languageCode: String): Boolean {
            val nonSpacingLanguages = listOf(
                "zh", // Chinese
                "zh-CN", // Chinese (simplified)
                "zh-TW", // Chinese (traditional)
                "ZH-HANS", // Chinese (simplified)
                "ZH-HANT", // Chinese (traditional)
                "zh-Hans", // Chinese (simplified)
                "zh-Hant", // Chinese (traditional)
                "ja", // Japanese
                "th", // Thai
                "lo", // Lao
                "my", // Burmese
                "km"  // Khmer
            )

            return nonSpacingLanguages.any { code ->
                languageCode.startsWith(code, ignoreCase = true)
            }
        }

        val displayNameMap = mapOf(
            "AUTO" to "Auto",
            "ZH-CN" to "${Locale("zh").displayName} (simplified)", // GOOGLE, PAPAGO
            "ZH-TW" to "${Locale("zh").displayName} (traditional)", // GOOGLE, PAPAGO
            "EN-GB" to "${Locale("en").displayName} (British)", // DeepLKit
            "EN-US" to "${Locale("en").displayName} (American)", // DeepLKit
            "PT-BR" to "${Locale("pt").displayName} (Brazilian)", // DeepLKit
            "PT-PT" to "${Locale("pt").displayName} (excluding Brazilian)", // DeepLKit
            "ZH-HANS" to "${Locale("zh").displayName} (simplified)", // DeepLKit, AzureKit
            "ZH-HANT" to "${Locale("zh").displayName} (traditional)", // DeepLKit
            "IKT" to "Western Canadian", // AzureKit
            "LZH" to "Literary Chinese", // AzureKit
            "MWW" to "Mont Dao (Latin)", // AzureKit
            "OTQ" to "Otomi", // AzureKit
            "SR-CYRL" to "${Locale("sr").displayName} (Cyrillic)", // AzureKit
            "SR-LATN" to "${Locale("sr").displayName} (Latin)", // AzureKit
            "TLH-LATN" to "Klingon (Latin)", // AzureKit
            "TLH-PIQD" to "Klingon", // AzureKit
            "YUA" to "Yucatec Maya" // AzureKit
        )

        val noDisplayNameList = listOf(
            "IKT", // Western Canadian
            "LZH", // Literary Chinese
            "MWW", // Mont Dao (Latin)
            "OTQ", // Otomi
            "TLH-LATN", // Klingon (Latin)
            "TLH-PIQD", // Klingon
            "YUA", // Yucatec Maya
        )
    }
}
package com.yiqun.translator.data.remote.translation

/**
 * Source code to put on the wire for one translation request.
 *
 * The on-device guess (detectedLanguageCode) is derived from the WHOLE
 * captured screen, so foreign UI chrome around the pointed text can win:
 * an English banner above Spanish content makes ML Kit language ID say
 * "en", the request becomes en→en, and the engine echoes the source back
 * untranslated. When the user asked for auto-detection and the engine
 * natively supports it, let the engine detect on exactly the selected
 * text instead. Offline ML Kit has no auto mode and keeps the guess.
 */
object TranslationSourcePolicy {

    private val autoCapableKitTypes = setOf(
        TranslationKitType.GOOGLE,
        TranslationKitType.AZURE,
        TranslationKitType.DEEPL,
        TranslationKitType.PAPAGO,
    )

    fun requestSourceLanguageCode(
        userSourceLanguageCode: String,
        detectedLanguageCode: String,
        translationKitType: TranslationKitType,
    ): String {
        return if (userSourceLanguageCode == "auto" && translationKitType in autoCapableKitTypes) {
            "auto"
        } else {
            detectedLanguageCode
        }
    }
}

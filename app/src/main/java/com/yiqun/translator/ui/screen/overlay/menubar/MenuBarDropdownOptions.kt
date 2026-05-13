package com.yiqun.translator.ui.screen.overlay.menubar

import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.data.remote.translation.TranslationKitType

object MenuBarDropdownOptions {
    val textDetectModes: List<TextDetectMode> = TextDetectMode.values().toList()

    fun translationKitTypes(
        sourceLanguage: Language?,
        targetLanguage: Language?,
    ): List<TranslationKitType> {
        if (sourceLanguage == null || targetLanguage == null) {
            return TranslationKitType.values().toList()
        }
        val commonKitTypes = sourceLanguage.supportKitTypes.toSet()
            .intersect(targetLanguage.supportKitTypes.toSet())
        return TranslationKitType.values().filter { it in commonKitTypes }
    }

    fun hasMultipleTranslationKits(
        sourceLanguage: Language?,
        targetLanguage: Language?,
    ): Boolean = translationKitTypes(sourceLanguage, targetLanguage).size > 1
}

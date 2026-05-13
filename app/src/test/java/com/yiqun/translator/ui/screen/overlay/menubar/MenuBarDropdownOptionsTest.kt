package com.yiqun.translator.ui.screen.overlay.menubar

import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.data.remote.translation.TranslationKitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MenuBarDropdownOptionsTest {

    @Test
    fun textDetectModeDropdownShowsEveryModeInStableOrder() {
        assertEquals(
            listOf(
                TextDetectMode.WORD,
                TextDetectMode.SENTENCE,
                TextDetectMode.SENSE_GROUP,
                TextDetectMode.PARAGRAPH,
                TextDetectMode.SELECT,
                TextDetectMode.FIXED_AREA,
            ),
            MenuBarDropdownOptions.textDetectModes
        )
    }

    @Test
    fun translationKitDropdownKeepsEnumOrderAndFiltersUnsupportedKits() {
        val source = Language("en").apply {
            supportKitTypes.addAll(
                listOf(
                    TranslationKitType.PAPAGO,
                    TranslationKitType.GOOGLE,
                    TranslationKitType.DEEPL,
                )
            )
        }
        val target = Language("ko").apply {
            supportKitTypes.addAll(
                listOf(
                    TranslationKitType.AZURE,
                    TranslationKitType.GOOGLE,
                    TranslationKitType.PAPAGO,
                )
            )
        }

        assertEquals(
            listOf(TranslationKitType.GOOGLE, TranslationKitType.PAPAGO),
            MenuBarDropdownOptions.translationKitTypes(source, target)
        )
    }

    @Test
    fun translationKitDropdownIsDisabledWhenOnlyOneKitIsAvailable() {
        val source = Language("en").apply { supportKitTypes.add(TranslationKitType.GOOGLE) }
        val target = Language("ja").apply { supportKitTypes.add(TranslationKitType.GOOGLE) }

        assertFalse(MenuBarDropdownOptions.hasMultipleTranslationKits(source, target))
    }
}

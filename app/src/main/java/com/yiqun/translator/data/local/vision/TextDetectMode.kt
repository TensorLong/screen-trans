package com.yiqun.translator.data.local.vision

import com.yiqun.translator.R

enum class TextDetectMode(
    val text: String,
    val iconResourceId: Int,
    val descriptionResourceId: Int,
) {
    WORD(
        text = "Word detection",
        iconResourceId = R.drawable.ic_detect_mode_word,
        descriptionResourceId = R.string.feature_view_description_word_detection,
    ),
    SENTENCE(
        text = "Sentence detection",
        iconResourceId = R.drawable.ic_detect_mode_sentence,
        descriptionResourceId = R.string.feature_view_description_sentence_detection,
    ),
    SENSE_GROUP(
        text = "Sense group detection",
        iconResourceId = R.drawable.ic_detect_mode_sense_group,
        descriptionResourceId = R.string.feature_view_description_sense_group_detection,
    ),
    PARAGRAPH(
        text = "Paragraph detection",
        iconResourceId = R.drawable.ic_detect_mode_paragraph,
        descriptionResourceId = R.string.feature_view_description_paragraph_detection,
    ),
    SELECT(
        text = "Area selection",
        iconResourceId = R.drawable.ic_detect_mode_select,
        descriptionResourceId = R.string.feature_view_description_select_detection,
    ),
    FIXED_AREA(
        text = "Fixed-Area translation",
        iconResourceId = R.drawable.ic_detect_mode_fixedarea,
        descriptionResourceId = R.string.feature_view_description_fixed_area_detection,
    ),
}

package com.yiqun.translator.data.local.vision.model

import android.graphics.Bitmap
import com.yiqun.translator.data.local.vision.WritingDirection
import com.google.mlkit.vision.text.Text

data class Transaction(
    val bitmap: Bitmap,
    val text: Text,
    val detectedLanguageCode: String,
    val paragraphs: List<Paragraph>,
) {

    fun mostFrequentWritingDirection(): WritingDirection? {
        return paragraphs.groupingBy { it.writingDirection }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    override fun toString(): String {
        return "Vision(" +
                "bitmap=$bitmap, " +
                "text=$text, " +
                "detectedLanguageCode=$detectedLanguageCode, " +
                "result=$paragraphs, " +
                ")"
    }
}
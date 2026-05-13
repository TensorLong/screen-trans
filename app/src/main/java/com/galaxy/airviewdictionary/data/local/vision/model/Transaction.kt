package com.galaxy.airviewdictionary.data.local.vision.model

import android.graphics.Bitmap
import com.galaxy.airviewdictionary.data.local.vision.WritingDirection
import com.google.mlkit.vision.text.Text

data class Transaction(
    val bitmap: Bitmap,
    val text: Text,
    val detectedLanguageCode: String,
    val paragraphs: List<Paragraph>,
) {

    fun mostFrequentWritingDirection(): WritingDirection? {
        return paragraphs.groupingBy { it.writingDirection } // 각 writingDirection별 그룹화
            .eachCount() // 각 그룹의 개수 계산
            .maxByOrNull { it.value } // 개수가 가장 많은 항목 선택
            ?.key // 해당 writingDirection 반환
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
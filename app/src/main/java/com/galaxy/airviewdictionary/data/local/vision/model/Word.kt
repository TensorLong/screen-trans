package com.galaxy.airviewdictionary.data.local.vision.model

import android.graphics.Rect
import com.galaxy.airviewdictionary.data.local.vision.WritingDirection


/**
 * [Line] 을 구성하는 단어
 */
data class Word(
    override val boundingBox: Rect,
    override val representation: String,
    override val writingDirection: WritingDirection,
    val chars: List<Char>,
    private val presetFontHeight: Double? = null // 선택적 매개변수로 fontHeight 추가
) : VisionSingleLineText {
    override val fontHeight: Double
        get() = presetFontHeight ?: super.fontHeight // customFontHeight가 null이면 VisionSingleLineText의 fontHeight 사용
}

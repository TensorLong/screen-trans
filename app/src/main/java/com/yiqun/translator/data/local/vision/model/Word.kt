package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection


/**
 */
data class Word(
    override val boundingBox: Rect,
    override val representation: String,
    override val writingDirection: WritingDirection,
    val chars: List<Char>,
    private val presetFontHeight: Double? = null
) : VisionSingleLineText {
    override val fontHeight: Double
        get() = presetFontHeight ?: super.fontHeight
}

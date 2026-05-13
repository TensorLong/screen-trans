package com.galaxy.airviewdictionary.data.local.vision.model

import android.graphics.Rect
import com.galaxy.airviewdictionary.data.local.vision.WritingDirection


/**
 * [Word] 를 구성하는 문자.
 */
data class Char(
    override val boundingBox: Rect,
    override val representation: String,
    override val writingDirection: WritingDirection
) : VisionSingleLineText

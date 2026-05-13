package com.yiqun.translator.data.local.vision.model

import android.graphics.Rect
import com.yiqun.translator.data.local.vision.WritingDirection


/**
 */
data class Char(
    override val boundingBox: Rect,
    override val representation: String,
    override val writingDirection: WritingDirection
) : VisionSingleLineText

package com.galaxy.airviewdictionary.data.local.vision

import com.galaxy.airviewdictionary.data.local.vision.model.Line
import com.galaxy.airviewdictionary.data.local.vision.model.Word


/**
 * 텍스트 읽기 방향
 * 이것에 따라 [Word] 와 [Line] 의 정렬방법이 결정된다.
 */
enum class WritingDirection {
    LTR,
    RTL,
    TTB_LTR,
    TTB_RTL
}

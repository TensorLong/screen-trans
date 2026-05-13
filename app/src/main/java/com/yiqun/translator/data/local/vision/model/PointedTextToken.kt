package com.yiqun.translator.data.local.vision.model

import kotlin.math.abs

data class PointedTextToken(
    val text: String,
    val start: Int,
    val end: Int,
) {
    companion object {
        fun tokenAt(text: String, charOffset: Int): PointedTextToken {
            if (text.isEmpty()) return PointedTextToken("", 0, 0)

            val ranges = tokenRanges(text)
            if (ranges.isEmpty()) return PointedTextToken(text, 0, text.length)

            val offset = charOffset.coerceIn(0, text.lastIndex)
            val range = ranges.firstOrNull { offset >= it.first && offset < it.second }
                ?: ranges.minWith(compareBy<Pair<Int, Int>> { distanceToRange(offset, it) }
                    .thenBy { if (it.first <= offset) 0 else 1 }
                    .thenBy { abs(it.first - offset) })

            return PointedTextToken(
                text = text.substring(range.first, range.second),
                start = range.first,
                end = range.second,
            )
        }

        private fun tokenRanges(text: String): List<Pair<Int, Int>> {
            val ranges = mutableListOf<Pair<Int, Int>>()
            var start: Int? = null

            for (index in text.indices) {
                if (isTokenChar(text, index)) {
                    if (start == null) start = index
                } else if (start != null) {
                    ranges.add(start to index)
                    start = null
                }
            }
            if (start != null) ranges.add(start to text.length)

            return ranges
        }

        private fun isTokenChar(text: String, index: Int): Boolean {
            val char = text[index]
            if (char.isLetterOrDigit()) return true
            if (char != '\'' && char != '’') return false

            val previous = text.getOrNull(index - 1)
            val next = text.getOrNull(index + 1)
            return previous?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true
        }

        private fun distanceToRange(offset: Int, range: Pair<Int, Int>): Int {
            return when {
                offset < range.first -> range.first - offset
                offset >= range.second -> offset - range.second + 1
                else -> 0
            }
        }
    }
}

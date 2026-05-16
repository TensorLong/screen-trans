package com.yiqun.translator.data.local.vision

object RecognizedLineWordAffinityPolicy {
    private const val MIN_AXIS_SIMILARITY_RATIO = 0.5
    private const val MIN_FONT_HEIGHT_SIMILARITY_RATIO = 0.5
    private const val MIN_SPLIT_AXIS_SIMILARITY_RATIO = 0.75
    private const val MIN_SPLIT_FONT_HEIGHT_SIMILARITY_RATIO = 0.65
    private const val MAX_SPLIT_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO = 8.0

    fun accepts(
        axisSimilarityRatio: Double,
        fontHeightSimilarityRatio: Double,
    ): Boolean {
        return axisSimilarityRatio >= MIN_AXIS_SIMILARITY_RATIO &&
                fontHeightSimilarityRatio >= MIN_FONT_HEIGHT_SIMILARITY_RATIO
    }

    fun acceptsSplitSourceLineFragment(
        axisSimilarityRatio: Double,
        fontHeightSimilarityRatio: Double,
        writeDirectionDistanceFontHeightRatio: Double,
    ): Boolean {
        return axisSimilarityRatio >= MIN_SPLIT_AXIS_SIMILARITY_RATIO &&
                fontHeightSimilarityRatio >= MIN_SPLIT_FONT_HEIGHT_SIMILARITY_RATIO &&
                writeDirectionDistanceFontHeightRatio <= MAX_SPLIT_WRITE_DIRECTION_DISTANCE_FONT_HEIGHT_RATIO
    }
}

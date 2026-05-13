import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * 모든 boundingBox를 순회하며 합집합 Rect 계산
 */
fun Text.getBoundingBoxUnion(): Rect? {
    return textBlocks
        .mapNotNull { it.boundingBox } // boundingBox가 null인 경우 제외
        .fold(null as Rect?) { unionRect, boundingBox ->
            unionRect?.apply { union(boundingBox) } ?: Rect(boundingBox) // 합집합 계산
        }
}

/**
 * textBlocks 높이 평균
 */
fun Text.getAverageTextBlockHeight(): Double {
    val textBlocks = this.textBlocks
    if (textBlocks.isEmpty()) return 0.0

    val totalHeight = textBlocks.sumOf { textBlock ->
        textBlock.boundingBox?.height()?.toDouble() ?: 0.0
    }

    return totalHeight / textBlocks.size
}
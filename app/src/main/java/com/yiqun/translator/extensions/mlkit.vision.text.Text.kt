import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 */
fun Text.getBoundingBoxUnion(): Rect? {
    return textBlocks
        .mapNotNull { it.boundingBox }
        .fold(null as Rect?) { unionRect, boundingBox ->
            unionRect?.apply { union(boundingBox) } ?: Rect(boundingBox)
        }
}

/**
 */
fun Text.getAverageTextBlockHeight(): Double {
    val textBlocks = this.textBlocks
    if (textBlocks.isEmpty()) return 0.0

    val totalHeight = textBlocks.sumOf { textBlock ->
        textBlock.boundingBox?.height()?.toDouble() ?: 0.0
    }

    return totalHeight / textBlocks.size
}
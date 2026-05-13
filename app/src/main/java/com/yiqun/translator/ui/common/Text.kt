package com.yiqun.translator.ui.common

import androidx.annotation.DimenRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import timber.log.Timber

/**
 *
 */
@Composable
fun AutoResizeText(
    text: AnnotatedString,
    maxFontSize: TextUnit = 60.sp,
    minFontSize: TextUnit = 8.sp,
    enableAutoResize: Boolean = true,
    modifier: Modifier = Modifier,
    onReadyToDisplay: () -> Unit
) {
    var fontSize by remember { mutableStateOf(maxFontSize) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val constraintsMaxWidth = with(LocalDensity.current) { maxWidth.toPx() }
        val constraintsMaxHeight = with(LocalDensity.current) { maxHeight.toPx() }

        Text(
            text = text,
            fontSize = fontSize,
            maxLines = Int.MAX_VALUE,
            textAlign = TextAlign.Start,
            onTextLayout = { textLayoutResult ->
                if (enableAutoResize) {
                    if ((textLayoutResult.didOverflowHeight || textLayoutResult.didOverflowWidth) && fontSize > minFontSize) {
                        fontSize = (fontSize.value * .9).coerceAtLeast(minFontSize.value.toDouble()).sp
                        Timber.tag("AutoResizeText").d("AutoResizeText [${fontSize}] ")
                    } else {
                        onReadyToDisplay()
                    }
                }
            }
        )
    }
}

@Composable
@ReadOnlyComposable
fun fontDimensionResource(@DimenRes id: Int) = dimensionResource(id = id).value.sp
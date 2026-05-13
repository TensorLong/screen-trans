package com.galaxy.airviewdictionary.extensions

import android.content.Context
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.util.TypedValueCompat


/**
 *  todo -------------- 정리
 */





@Composable
fun Dp.toSp(): TextUnit {
    val density = LocalDensity.current
    return with(density) {
        this@toSp.toSp()
    }
}

@Composable
fun Dp.toPx(): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.value, LocalContext.current.resources.displayMetrics).toInt()
}

fun Dp.toPx(context: Context): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.value, context.resources.displayMetrics).toInt()
}

@Composable
fun Int.toDp(): Dp {
    val density = LocalDensity.current
    return (this / density.density).dp
}

@Composable
fun Float.toDp(): Dp {
    val density = LocalDensity.current
    return (this / density.density).dp
}

//@Composable
//fun TextUnit.toDp(): Dp {
//    val density = LocalDensity.current
//    return with(density) {
//        this@toDp.toPx().dp / density.density
//    }
//}

@Composable
fun TextUnit.toDp(): Dp {
    return this@toDp.toPx().dp
}

@Composable
fun TextUnit.toPx(): Float {
    val density = LocalDensity.current
    return with(density) {
        this@toPx.toPx()
    }
}

fun Float.toSpValue(context: Context): Float {
    return  TypedValueCompat.pxToSp(this@toSpValue, context.resources.displayMetrics)
}

fun Float.toDpValue(context: Context): Float {
    return  TypedValueCompat.pxToDp(this@toDpValue, context.resources.displayMetrics)
}

@Composable
fun getScreenWidthDp(): Dp {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return with(density) {
        configuration.screenWidthDp.dp
    }
}





package com.galaxy.airviewdictionary.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val typography = Typography(
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

//    titleLarge = TextStyle(
//        fontFamily = FontFamily.Default,
//        fontWeight = FontWeight.Normal,
//        fontSize = 22.sp,
//        lineHeight = 28.sp,
//        letterSpacing = 0.sp
//    ),
//    labelSmall = TextStyle(
//        fontFamily = FontFamily.Default,
//        fontWeight = FontWeight.Medium,
//        fontSize = 11.sp,
//        lineHeight = 16.sp,
//        letterSpacing = 0.5.sp
//    ),
)

val xxtextStyleSettingItem = fun(themeTextStyle: TextStyle): TextStyle {
    return themeTextStyle.copy(color = grayDark
        //fontSize = 20.sp, fontWeight = FontWeight.Normal, //color = grayDark
    )
}

val xtextStyleSettingItemSub = fun(themeTextStyle: TextStyle): TextStyle {
    return themeTextStyle.copy(
        fontSize = 14.sp, fontWeight = FontWeight.Normal, color = gray
    )
}

val xtextStyleSettingDialogItem = fun(themeTextStyle: TextStyle): TextStyle {
    return themeTextStyle.copy(
        fontWeight = FontWeight.Normal
    )
}


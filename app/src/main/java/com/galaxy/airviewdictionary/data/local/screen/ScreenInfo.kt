package com.galaxy.airviewdictionary.data.local.screen

data class ScreenInfo(
    val width: Int,
    val height: Int,
    val statusBarHeight: Int,
    val navBarHeight: Int,
    val orientation: Int,
    val safePaddingLeft: Int,
    val safePaddingTop: Int,
    val safePaddingRight: Int,
    val safePaddingBottom: Int
) {
    // safeArea: 실제 안전하게 사용할 수 있는 화면 영역의 면적
    val safeArea: Int
        get() = safeWidth * safeHeight

    val safeWidth: Int
        get() = width - safePaddingLeft - safePaddingRight

    val safeHeight: Int
        get() = height - safePaddingTop - safePaddingBottom

    override fun toString(): String {
        return "ScreenInfo(" +
                "width=$width, height=$height, " +
                "statusBarHeight=$statusBarHeight, navBarHeight=$navBarHeight, " +
                "orientation=$orientation, " +
                "safePadding=[L:$safePaddingLeft, T:$safePaddingTop, R:$safePaddingRight, B:$safePaddingBottom], " +
                "safeWidth=$safeWidth, safeHeight=$safeHeight, safeArea=$safeArea" +
                ")"
    }
}

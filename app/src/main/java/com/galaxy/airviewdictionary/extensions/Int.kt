package com.galaxy.airviewdictionary.extensions

import java.util.Locale

/**
 * 숫자 -> 날짜 포맷 (ex: 01, 02, 03.....09, 10, 11)
 */
fun Int._toBidigitFormat(): String = try {
    String.format(Locale.US, "%02d", this)
} catch (e: Throwable) {
    "" + this
}


package com.yiqun.translator.extensions

import java.util.Locale

/**
 */
fun Int._toBidigitFormat(): String = try {
    String.format(Locale.US, "%02d", this)
} catch (e: Throwable) {
    "" + this
}


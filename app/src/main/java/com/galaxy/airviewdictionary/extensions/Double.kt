package com.galaxy.airviewdictionary.extensions

import kotlin.math.floor

fun Double._cutDecimal(numberOfDigits: Int = 100): Double = floor(this * numberOfDigits) / numberOfDigits

fun Double._divideByLarger(other: Double): Double {
    return if (this < other) this / other else other / this
}

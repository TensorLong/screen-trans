package com.galaxy.airviewdictionary.extensions

import android.graphics.Point
import android.graphics.Rect

fun Rect?.isValid(): Boolean {
    return this?.let {
        it.left >= 0 && it.top >= 0 && it.right >= it.left && it.bottom >= it.top
    } ?: false
}

fun Rect._unionWith(other: Rect): Rect {
    return Rect(
        minOf(this.left, other.left),
        minOf(this.top, other.top),
        maxOf(this.right, other.right),
        maxOf(this.bottom, other.bottom)
    )
}

val Rect.topLeft: Point
    get() = Point(this.left, this.top)

val Rect.topRight: Point
    get() = Point(this.right, this.top)

val Rect.bottomLeft: Point
    get() = Point(this.left, this.bottom)

val Rect.bottomRight: Point
    get() = Point(this.right, this.bottom)

fun Rect.setFromPoints(point1: Point, point2: Point) {
    this.left = minOf(point1.x, point2.x)
    this.right = maxOf(point1.x, point2.x)
    this.top = minOf(point1.y, point2.y)
    this.bottom = maxOf(point1.y, point2.y)
}


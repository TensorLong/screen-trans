package com.galaxy.airviewdictionary.extensions

fun String.capitalizeFirstLetter(): String {
    return if (isNotEmpty()) this[0].uppercase() + substring(1).lowercase() else ""
}

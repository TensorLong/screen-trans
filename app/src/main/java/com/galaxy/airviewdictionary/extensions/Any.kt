package com.galaxy.airviewdictionary.extensions

import com.google.gson.GsonBuilder


///////////////////////////////////////////////////////////////////////////////
//                                                                           //
//                                   Gson                                    //
//                                                                           //
///////////////////////////////////////////////////////////////////////////////

fun Any._toPrettyJson(): String = try {
    GsonBuilder().setPrettyPrinting().create().toJson(this)
} catch (e: Throwable) {
    "" + this
}
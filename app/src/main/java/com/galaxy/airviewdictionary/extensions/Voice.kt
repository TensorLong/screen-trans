package com.galaxy.airviewdictionary.extensions

import android.speech.tts.Voice
import com.galaxy.airviewdictionary.data.remote.translation.Language

val Voice.languageCode: String
    get() = this.name.split("-")[0]

val Voice.language: Language
    get() = Language(this.languageCode)

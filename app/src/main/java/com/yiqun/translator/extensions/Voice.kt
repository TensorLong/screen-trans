package com.yiqun.translator.extensions

import android.speech.tts.Voice
import com.yiqun.translator.data.remote.translation.Language

val Voice.languageCode: String
    get() = this.name.split("-")[0]

val Voice.language: Language
    get() = Language(this.languageCode)

package com.galaxy.airviewdictionary.data.local.vision

import androidx.lifecycle.Lifecycle
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


enum class TextRecognizerType {
    TEXT,
    CHINESE,
    KOREAN,
    JAPANESE,
    DEVANAGARI,
}

class MyTextRecognizer(val type: TextRecognizerType) {

    private val TAG = javaClass.simpleName

    private  var recognizer: TextRecognizer

    init {
        recognizer = createTextRecognizer(type)
    }

    private fun createTextRecognizer(type: TextRecognizerType): TextRecognizer {
        return when (type) {
            TextRecognizerType.TEXT -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            TextRecognizerType.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            TextRecognizerType.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            TextRecognizerType.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            TextRecognizerType.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
    }

    fun addObserver(lifecycle: Lifecycle) {
        lifecycle.addObserver(recognizer)
    }

    suspend fun process(inputImage: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            Timber.tag(TAG).d("---------- process ---------")
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    continuation.resume(result)
                }
                .addOnFailureListener { exception ->
                    if (exception is MlKitException && exception.message?.contains("closed") == true) {
                        Timber.tag(TAG).d("TextRecognizer needs to be reinitialized.")
                        recognizer = createTextRecognizer(type)
                        recognizer.process(inputImage)
                            .addOnSuccessListener { result ->
                                continuation.resume(result)
                            }
                            .addOnFailureListener { ex ->
                                continuation.resumeWithException(ex)
                            }
                    } else {
                        continuation.resumeWithException(exception)
                    }
                }
        }

    // 필요한 경우 추가 기능을 클래스에 추가할 수 있습니다.
    fun closeRecognizer() {
        recognizer.close()
    }
}

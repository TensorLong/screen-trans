package com.yiqun.translator.data.local.preference

import android.content.Context
import android.speech.tts.Voice
import androidx.core.os.ConfigurationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yiqun.translator.R
import com.yiqun.translator.ui.screen.overlay.targethandle.DeviceFormFactorResolver
import com.yiqun.translator.ui.screen.overlay.targethandle.PointerDisplayPolicy
import com.yiqun.translator.ui.screen.overlay.targethandle.PointerOffset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


val Context.preferenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class PreferenceRepository @Inject constructor(@ApplicationContext val context: Context) {

    private val TAG = javaClass.simpleName

    companion object PreferencesKeys {
//        val IS_FIRST_START = booleanPreferencesKey("is_first_start")
        val WAS_TRAILER_SHOWN = booleanPreferencesKey("was_trailer_shown")
        val IS_REVIEW_DONE = booleanPreferencesKey("is_review_done")

        val IS_SAY_HERE_R_SHOWN = booleanPreferencesKey("is_say_here_r_shown")
        val IS_SAY_HERE_L_SHOWN = booleanPreferencesKey("is_say_here_l_shown")

        val TEXT_DETECT_MODE: Preferences.Key<String> = stringPreferencesKey("text_detect_mode")
        val SOURCE_LANGUAGE_CODE = stringPreferencesKey("source_language_code")
        val TARGET_LANGUAGE_CODE = stringPreferencesKey("target_language_code")
        val TRANSLATION_KIT_TYPE = stringPreferencesKey("translation_kit_type")

        val DRAG_HANDLE_DOCKING = booleanPreferencesKey("drag_handle_docking")
        val DOCKING_DELAY = longPreferencesKey("docking_delay")
        val DRAG_HANDLE_HAPTIC = booleanPreferencesKey("drag_handle_haptic")
        val POINTER_LEFT_OFFSET = stringPreferencesKey("pointer_left_offset")
        val POINTER_RIGHT_OFFSET = stringPreferencesKey("pointer_right_offset")
        val DUAL_POINTER_ENABLED = booleanPreferencesKey("dual_pointer_enabled")
        val TRANSLATION_TRANSPARENCY = floatPreferencesKey("translation_transparency")
        val TRANSLATION_CLOSE_DELAY = longPreferencesKey("translation_close_delay")
        val REPLY_TRANSPARENCY = floatPreferencesKey("reply_transparency")
        val AUTOMATIC_TRANSLATION_PLAYBACK = booleanPreferencesKey("automatic_translation_playback")
        val EINK_DISPLAY_MODE = booleanPreferencesKey("eink_display_mode")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_ORDERED_VOICE_NAMES = stringPreferencesKey("tts_ordered_voice_names")

        val SOURCE_LANGUAGE_CODE_HISTORY = stringPreferencesKey("source_language_code_history")
        val TARGET_LANGUAGE_CODE_HISTORY = stringPreferencesKey("target_language_code_history")
    }

    private val preferenceDataStore: DataStore<Preferences> = context.preferenceDataStore

    private val gson = Gson()

    val defaultPointerOffset: PointerOffset
        get() = PointerOffset.defaultTargetFromHandleOffset(
            pointerDimen = context.resources.getDimensionPixelSize(R.dimen.target_pointer_dimen),
            handleWidth = context.resources.getDimensionPixelSize(R.dimen.target_handle_width),
            pointerThumbSpace = context.resources.getDimensionPixelSize(R.dimen.target_handle_pointer_thumb_space),
        )

    private val preferenceFlow: Flow<Preferences> = preferenceDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    fun <T> update(key: Preferences.Key<T>, value: T) {
        CoroutineScope(Dispatchers.IO).launch {
            preferenceDataStore.edit { preferences ->
                preferences[key] = value
            }
        }
    }

//    val isFirstStartFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
//        preferences[IS_FIRST_START] ?: true
//    }

    val wasTrailerShownFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[WAS_TRAILER_SHOWN] ?: false
    }

    val isReviewDoneFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[IS_REVIEW_DONE] ?: false
    }

    val isSayHereRShownFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[IS_SAY_HERE_R_SHOWN] ?: false
    }

    val isSayHereLShownFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[IS_SAY_HERE_L_SHOWN] ?: false
    }

    val textDetectModeFlow: Flow<TextDetectMode> = preferenceFlow.map { preferences ->
        val textDetectModeString = preferences[TEXT_DETECT_MODE]
        textDetectModeString?.let { TextDetectMode.valueOf(textDetectModeString) } ?: TextDetectMode.SENTENCE
    }

//    val sourceLanguageCodeFlow: Flow<String> = preferenceFlow.map { preferences ->
//        preferences[SOURCE_LANGUAGE_CODE] ?: "en"//"auto"
//    }
//
//    val targetLanguageCodeFlow: Flow<String> = preferenceFlow.map { preferences ->
//        preferences[TARGET_LANGUAGE_CODE] ?: getCurrentLocale().language
//    }

    val sourceLanguageCodeFlow: Flow<String> = preferenceFlow.map { preferences ->
        Timber.tag(TAG).d(" preferences[SOURCE_LANGUAGE_CODE] ${preferences[SOURCE_LANGUAGE_CODE]} getCurrentLocale().language ${getCurrentLocale().language}")
        preferences[SOURCE_LANGUAGE_CODE] ?: getCurrentLocale().language
    }

    val targetLanguageCodeFlow: Flow<String> = preferenceFlow.map { preferences ->
        Timber.tag(TAG).d(" preferences[TARGET_LANGUAGE_CODE] ${preferences[TARGET_LANGUAGE_CODE]}")
        preferences[TARGET_LANGUAGE_CODE] ?: "en"
    }

    val translationKitTypeFlow: Flow<TranslationKitType> = preferenceFlow.map { preferences ->
        val translationKitTypeString = preferences[TRANSLATION_KIT_TYPE]
        translationKitTypeString?.let { TranslationKitType.valueOf(translationKitTypeString) } ?: TranslationKitType.GOOGLE
    }

    val dragHandleDockingFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[DRAG_HANDLE_DOCKING] ?: false
    }

    val dockingDelayFlow: Flow<Long> = preferenceFlow.map { preferences ->
        preferences[DOCKING_DELAY] ?: 15000L
    }

    val dragHandleHapticFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[DRAG_HANDLE_HAPTIC] ?: false
    }

    val pointerLeftOffsetFlow: Flow<PointerOffset> = preferenceFlow.map { preferences ->
        PointerOffset.decodeTargetFromHandle(preferences[POINTER_LEFT_OFFSET], defaultPointerOffset)
    }

    val pointerRightOffsetFlow: Flow<PointerOffset> = preferenceFlow.map { preferences ->
        PointerOffset.decodeTargetFromHandle(preferences[POINTER_RIGHT_OFFSET], defaultPointerOffset)
    }

    val dualPointerEnabledFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[DUAL_POINTER_ENABLED] ?: PointerDisplayPolicy.defaultDualPointerEnabled(
            DeviceFormFactorResolver.resolve(context.resources.configuration)
        )
    }

    val translationTransparencyFlow: Flow<Float> = preferenceFlow.map { preferences ->
        preferences[TRANSLATION_TRANSPARENCY] ?: 0.905f
    }

    val translationCloseDelayFlow: Flow<Long> = preferenceFlow.map { preferences ->
        preferences[TRANSLATION_CLOSE_DELAY] ?: 1600L
    }

    val replyTransparencyFlow: Flow<Float> = preferenceFlow.map { preferences ->
        preferences[REPLY_TRANSPARENCY] ?: 0.905f
    }

    val automaticTranslationPlaybackFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[AUTOMATIC_TRANSLATION_PLAYBACK] ?: false
    }

    val einkDisplayModeFlow: Flow<Boolean> = preferenceFlow.map { preferences ->
        preferences[EINK_DISPLAY_MODE] ?: false
    }

    val ttsSpeechRateFlow: Flow<Float> = preferenceFlow.map { preferences ->
        preferences[TTS_SPEECH_RATE] ?: 1.0f
    }

    val ttsOrderedVoiceNamesFlow: Flow<List<String>> = preferenceFlow.map { preferences ->
        preferences[TTS_ORDERED_VOICE_NAMES]?.let { jsonString ->
            gson.fromJson(jsonString, object : TypeToken<List<String>>() {}.type)
        } ?: listOf()
    }

    val sourceLanguageCodeHistoryFlow: Flow<List<String>> = preferenceFlow.map { preferences ->
        preferences[SOURCE_LANGUAGE_CODE_HISTORY]?.let { jsonString ->
            gson.fromJson(jsonString, object : TypeToken<List<String>>() {}.type)
        } ?: listOf()
    }

    val targetLanguageCodeHistoryFlow: Flow<List<String>> = preferenceFlow.map { preferences ->
        preferences[TARGET_LANGUAGE_CODE_HISTORY]?.let { jsonString ->
            gson.fromJson(jsonString, object : TypeToken<List<String>>() {}.type)
        } ?: listOf()
    }

    fun addOrUpdateLanguageHistory(newLanguageCode: String, isSourceLanguage: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            preferenceDataStore.edit { preferences ->
                preferences[if (isSourceLanguage) SOURCE_LANGUAGE_CODE else TARGET_LANGUAGE_CODE] = newLanguageCode
            }

            val currentHistory = if (isSourceLanguage) sourceLanguageCodeHistoryFlow.first() else targetLanguageCodeHistoryFlow.first()
            val updatedList = mutableListOf(newLanguageCode)
            currentHistory.filter { !it.equals(newLanguageCode, ignoreCase = true) }.forEach { updatedList.add(it) }
            val finalList = if (updatedList.size > 5) updatedList.take(5) else updatedList
            val jsonString = gson.toJson(finalList)
            update(if (isSourceLanguage) SOURCE_LANGUAGE_CODE_HISTORY else TARGET_LANGUAGE_CODE_HISTORY, jsonString)
        }
    }

    fun addOrUpdateOrderedVoiceNames(orderedVoices: List<Voice>) {
        CoroutineScope(Dispatchers.IO).launch {
            val orderedVoiceNames: List<String> = orderedVoices.map { voice -> voice.name }
            val jsonString = gson.toJson(orderedVoiceNames)
            update(TTS_ORDERED_VOICE_NAMES, jsonString)
        }
    }

    private fun getCurrentLocale(): Locale {
        // Configuration.getLocales() needs API 24; the compat call also works on minSdk 23.
        return ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
    }

}









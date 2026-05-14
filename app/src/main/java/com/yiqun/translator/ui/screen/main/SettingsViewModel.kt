package com.yiqun.translator.ui.screen.main

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.data.local.secure.SecureRepository
import com.yiqun.translator.data.local.tts.TTSRepository
import com.yiqun.translator.data.remote.ai.chatgpt.ChatGPTKit
import com.yiqun.translator.data.remote.firebase.AnalyticsRepository
import com.yiqun.translator.data.remote.firebase.RemoteConfigRepository
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.remote.translation.TranslationRepository
import com.yiqun.translator.ui.screen.overlay.menubar.MenuConfig
import com.yiqun.translator.ui.screen.overlay.targethandle.PointerOffset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val secureRepository: SecureRepository,
    val remoteConfigRepository: RemoteConfigRepository,
    val preferenceRepository: PreferenceRepository,
    val translationRepository: TranslationRepository,
    val ttsRepository: TTSRepository,
    val analyticsRepository: AnalyticsRepository,
    private val chatGPTKit: ChatGPTKit,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    val scrollState = ScrollState(initial = 0)

    fun updateIsReviewDone() {
        preferenceRepository.update(PreferenceRepository.IS_REVIEW_DONE, true)
    }

    fun updateDragHandleDocking(dragHandleDocking: Boolean) {
        preferenceRepository.update(PreferenceRepository.DRAG_HANDLE_DOCKING, dragHandleDocking)
    }

    fun updateDockingDelay(dockingDelay: Long) {
        preferenceRepository.update(PreferenceRepository.DOCKING_DELAY, dockingDelay)
    }

    fun updateDragHandleHaptic(dragHandleHaptic: Boolean) {
        preferenceRepository.update(PreferenceRepository.DRAG_HANDLE_HAPTIC, dragHandleHaptic)
    }

    fun updatePointerOffset(left: PointerOffset, right: PointerOffset) {
        preferenceRepository.update(PreferenceRepository.POINTER_LEFT_OFFSET, left.encode())
        preferenceRepository.update(PreferenceRepository.POINTER_RIGHT_OFFSET, right.encode())
    }

    fun updateDualPointerEnabled(enabled: Boolean) {
        preferenceRepository.update(PreferenceRepository.DUAL_POINTER_ENABLED, enabled)
    }

    fun updateMenuBarVisibility(menuVisibility: Boolean) {
        preferenceRepository.update(PreferenceRepository.MENU_BAR_VISIBILITY, menuVisibility)
    }

    fun updateMenuBarTransparency(transparency: Float) {
        preferenceRepository.update(PreferenceRepository.MENU_BAR_TRANSPARENCY, transparency)
    }

    fun updateMenuBarConfig(menuBarConfig: MenuConfig) {
        preferenceRepository.update(PreferenceRepository.MENU_BAR_COMPOSITION, menuBarConfig.name)
    }

    fun updateTranslationTransparency(transparency: Float) {
        preferenceRepository.update(PreferenceRepository.TRANSLATION_TRANSPARENCY, transparency)
    }

    fun updateTranslationCloseDelay(translationCloseDelay: Long) {
        preferenceRepository.update(PreferenceRepository.TRANSLATION_CLOSE_DELAY, translationCloseDelay)
    }

    fun updateReplyTransparency(transparency: Float) {
        preferenceRepository.update(PreferenceRepository.REPLY_TRANSPARENCY, transparency)
    }

    fun updateAutomaticTranslationPlayback(automaticTranslationPlayback: Boolean) {
        preferenceRepository.update(PreferenceRepository.AUTOMATIC_TRANSLATION_PLAYBACK, automaticTranslationPlayback)
    }

    fun updateTtsSpeechRate(speechRate: Float) {
        preferenceRepository.update(PreferenceRepository.TTS_SPEECH_RATE, speechRate)
    }

    fun isLanguageSwappable(sourceLanguageCode: String, targetLanguageCode: String, kitType: TranslationKitType): Boolean {
        return translationRepository.isLanguageSwappable(sourceLanguageCode, targetLanguageCode, kitType)
    }

    val latestVersionCodeFlow: Flow<Long> =
        remoteConfigRepository.remoteConfigFlow
            .map { remoteConfig ->
                remoteConfig[RemoteConfigRepository.LATEST_VERSION_CODE_KEY]?.asLong() ?: 0
            }

    suspend fun fetchAiModels(): List<String> {
        return chatGPTKit.fetchModels()
    }

    suspend fun testAiConnection(): String {
        return chatGPTKit.testConfiguredModel()
    }

    init {
        Timber.tag(TAG).i("#### init ####")
        translationRepository.acquire()
        ttsRepository.acquire()
    }

    override fun onCleared() {
        translationRepository.release()
        ttsRepository.release()
        super.onCleared()
    }
}

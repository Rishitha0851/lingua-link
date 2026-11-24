package com.lingualink.packmanager

import android.content.Context
import com.lingualink.stt.ModelDownloadState
import com.lingualink.stt.SttLanguage
import com.lingualink.stt.SttModelManager
import com.lingualink.translation.ModelStatus
import com.lingualink.translation.OfflineTranslationManager
import com.lingualink.translation.TranslationLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Unified Language Pack Manager for both offline Vosk STT models and ML Kit on-device translation models.
 */
class LanguagePackManager(
    private val context: Context,
    private val sttModelManager: SttModelManager,
    private val translationManager: OfflineTranslationManager,
    private val scope: CoroutineScope
) {
    private val _packItems = MutableStateFlow<List<LanguagePackItem>>(emptyList())
    val packItems: StateFlow<List<LanguagePackItem>> = _packItems.asStateFlow()

    init {
        scope.launch {
            combine(
                sttModelManager.downloadStates,
                translationManager.modelStatuses
            ) { sttStates, mlKitStatuses ->
                buildPackList(sttStates, mlKitStatuses)
            }.collect { items ->
                _packItems.value = items
            }
        }
    }

    private fun buildPackList(
        sttStates: Map<SttLanguage, ModelDownloadState>,
        mlKitStatuses: Map<TranslationLanguage, ModelStatus>
    ): List<LanguagePackItem> {
        val list = mutableListOf<LanguagePackItem>()

        // 1. Vosk Speech Recognition Packs
        for (lang in SttLanguage.entries) {
            val state = sttStates[lang] ?: ModelDownloadState.NotDownloaded
            val (status, progress, msg, actionEnabled) = when {
                !lang.isSpeechInputSupported -> Quadruple(
                    PackStatus.UNSUPPORTED,
                    0,
                    lang.unsupportedReason ?: "Unsupported by Vosk",
                    false
                )
                state is ModelDownloadState.Ready -> Quadruple(
                    PackStatus.READY,
                    100,
                    "Ready for offline speech recognition",
                    true
                )
                state is ModelDownloadState.Downloading -> Quadruple(
                    PackStatus.DOWNLOADING,
                    state.progressPercent,
                    "Downloading (${state.progressPercent}%)...",
                    false
                )
                state is ModelDownloadState.Error -> Quadruple(
                    PackStatus.ERROR,
                    0,
                    state.message,
                    true
                )
                else -> Quadruple(
                    PackStatus.NOT_DOWNLOADED,
                    0,
                    "Tap download for offline STT",
                    true
                )
            }

            val sizeMb = if (lang.approximateSizeBytes > 0) "${lang.approximateSizeBytes / (1024 * 1024)} MB" else "N/A"
            list.add(
                LanguagePackItem(
                    id = "stt_${lang.code}",
                    type = PackType.STT_VOSK,
                    languageCode = lang.code,
                    languageName = "${lang.displayName} (STT Voice)",
                    nativeName = lang.nativeName,
                    sizeText = sizeMb,
                    status = status,
                    progressPercent = progress,
                    statusMessage = msg,
                    isActionEnabled = actionEnabled
                )
            )
        }

        // 2. ML Kit Translation Packs
        for (lang in TranslationLanguage.entries) {
            val state = mlKitStatuses[lang] ?: ModelStatus.NotDownloaded
            val (status, progress, msg, actionEnabled) = when (state) {
                is ModelStatus.Downloaded -> Quadruple(
                    PackStatus.READY,
                    100,
                    "Ready for offline translation",
                    true
                )
                is ModelStatus.Downloading -> Quadruple(
                    PackStatus.DOWNLOADING,
                    state.progressPercent,
                    "Downloading translation pack...",
                    false
                )
                is ModelStatus.Error -> Quadruple(
                    PackStatus.ERROR,
                    0,
                    state.message,
                    true
                )
                is ModelStatus.NotDownloaded -> Quadruple(
                    PackStatus.NOT_DOWNLOADED,
                    0,
                    "Tap download for offline translation",
                    true
                )
            }

            list.add(
                LanguagePackItem(
                    id = "mlkit_${lang.bcp47Code}",
                    type = PackType.TRANSLATION_MLKIT,
                    languageCode = lang.bcp47Code,
                    languageName = "${lang.displayName} (Translation Model)",
                    nativeName = lang.nativeName,
                    sizeText = "~30 MB",
                    status = status,
                    progressPercent = progress,
                    statusMessage = msg,
                    isActionEnabled = actionEnabled
                )
            )
        }

        return list
    }

    fun downloadPack(item: LanguagePackItem) {
        scope.launch(Dispatchers.IO) {
            when (item.type) {
                PackType.STT_VOSK -> {
                    val lang = SttLanguage.fromCode(item.languageCode)
                    sttModelManager.downloadModel(lang)
                }
                PackType.TRANSLATION_MLKIT -> {
                    val lang = TranslationLanguage.fromCode(item.languageCode)
                    translationManager.downloadModel(lang)
                }
            }
        }
    }

    fun deletePack(item: LanguagePackItem) {
        scope.launch(Dispatchers.IO) {
            when (item.type) {
                PackType.STT_VOSK -> {
                    val lang = SttLanguage.fromCode(item.languageCode)
                    sttModelManager.deleteModel(lang)
                }
                PackType.TRANSLATION_MLKIT -> {
                    val lang = TranslationLanguage.fromCode(item.languageCode)
                    translationManager.deleteModel(lang)
                }
            }
        }
    }

    /**
     * Checks if both STT model and ML Kit translation model are ready for a given language pair.
     * Used by the header's real "Offline Ready" badge!
     */
    suspend fun isOfflineReady(sourceLang: TranslationLanguage, targetLang: TranslationLanguage): Boolean {
        // ML Kit models check
        val sourceMlKit = translationManager.isModelDownloaded(sourceLang)
        val targetMlKit = translationManager.isModelDownloaded(targetLang)

        // STT model check for source (if speech input supported)
        val sttLang = SttLanguage.fromCode(sourceLang.bcp47Code)
        val sttReady = if (sttLang.isSpeechInputSupported) {
            sttModelManager.isModelDownloaded(sttLang)
        } else {
            true // Text input is supported
        }

        return sourceMlKit && targetMlKit && sttReady
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

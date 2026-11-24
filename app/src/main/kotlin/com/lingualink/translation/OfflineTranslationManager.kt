package com.lingualink.translation

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: TranslationLanguage,
    val targetLanguage: TranslationLanguage,
    val latencyMs: Long
)

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progressPercent: Int) : ModelStatus()
    object Downloaded : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

/**
 * Module 2: Offline On-Device Translation Manager using Google ML Kit Translate.
 * Supports English, Hindi, Tamil, Telugu in all directions.
 * Verifies models are downloaded before translating; never mocks or echoes source text.
 */
class OfflineTranslationManager(
    private val context: Context
) {
    companion object {
        const val TAG = "TRANSLATE"
    }

    private val modelManager = RemoteModelManager.getInstance()
    private val translatorCache = mutableMapOf<String, Translator>()

    private val _modelStatuses = MutableStateFlow<Map<TranslationLanguage, ModelStatus>>(emptyMap())
    val modelStatuses: StateFlow<Map<TranslationLanguage, ModelStatus>> = _modelStatuses.asStateFlow()

    init {
        // Initial scan of downloaded ML Kit models
        checkAllModelStatuses()
    }

    fun checkAllModelStatuses() {
        for (lang in TranslationLanguage.entries) {
            val model = TranslateRemoteModel.Builder(lang.mlKitCode).build()
            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    val status = if (isDownloaded) ModelStatus.Downloaded else ModelStatus.NotDownloaded
                    updateStatus(lang, status)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error checking model status for ${lang.displayName}", e)
                    updateStatus(lang, ModelStatus.Error(e.message ?: "Check failed"))
                }
        }
    }

    private fun updateStatus(lang: TranslationLanguage, status: ModelStatus) {
        val map = _modelStatuses.value.toMutableMap()
        map[lang] = status
        _modelStatuses.value = map
    }

    suspend fun isModelDownloaded(language: TranslationLanguage): Boolean = withContext(Dispatchers.IO) {
        val model = TranslateRemoteModel.Builder(language.mlKitCode).build()
        try {
            modelManager.isModelDownloaded(model).awaitResult()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query model status for ${language.displayName}", e)
            false
        }
    }

    suspend fun downloadModel(
        language: TranslationLanguage,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val model = TranslateRemoteModel.Builder(language.mlKitCode).build()
        updateStatus(language, ModelStatus.Downloading(0))

        try {
            val isAlreadyDownloaded = modelManager.isModelDownloaded(model).awaitResult()
            if (isAlreadyDownloaded) {
                updateStatus(language, ModelStatus.Downloaded)
                return@withContext Result.success(Unit)
            }

            Log.i(TAG, "Starting ML Kit model download for ${language.displayName}...")
            val conditions = DownloadConditions.Builder().build()
            modelManager.download(model, conditions).awaitResult()

            updateStatus(language, ModelStatus.Downloaded)
            Log.i(TAG, "Successfully downloaded ML Kit model for ${language.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading ML Kit model for ${language.displayName}", e)
            val err = e.message ?: "Download failed"
            updateStatus(language, ModelStatus.Error(err))
            Result.failure(e)
        }
    }

    suspend fun deleteModel(language: TranslationLanguage): Result<Unit> = withContext(Dispatchers.IO) {
        val model = TranslateRemoteModel.Builder(language.mlKitCode).build()
        try {
            modelManager.deleteDownloadedModel(model).awaitResult()
            updateStatus(language, ModelStatus.NotDownloaded)
            // Invalidate translator cache
            translatorCache.clear()
            Log.i(TAG, "Deleted ML Kit model for ${language.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete ML Kit model for ${language.displayName}", e)
            Result.failure(e)
        }
    }

    /**
     * Translates input text from sourceLanguage to targetLanguage on-device.
     * Throws / fails if either language pack is not downloaded. Never echoes source text.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: TranslationLanguage,
        targetLanguage: TranslationLanguage
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Source text is empty"))
        }

        if (sourceLanguage == targetLanguage) {
            // Identity translation (same language)
            return@withContext Result.success(
                TranslationResult(
                    sourceText = trimmed,
                    translatedText = trimmed,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    latencyMs = 0L
                )
            )
        }

        val startTime = System.currentTimeMillis()

        // 1. Verify source language pack
        val sourceDownloaded = isModelDownloaded(sourceLanguage)
        if (!sourceDownloaded) {
            val err = "Language pack not downloaded: ${sourceLanguage.displayName}. Please download it in Language Packs."
            Log.w(TAG, err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        // 2. Verify target language pack
        val targetDownloaded = isModelDownloaded(targetLanguage)
        if (!targetDownloaded) {
            val err = "Language pack not downloaded: ${targetLanguage.displayName}. Please download it in Language Packs."
            Log.w(TAG, err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        // 3. Obtain or instantiate translator
        val cacheKey = "${sourceLanguage.bcp47Code}_${targetLanguage.bcp47Code}"
        val translator = translatorCache.getOrPut(cacheKey) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage.mlKitCode)
                .setTargetLanguage(targetLanguage.mlKitCode)
                .build()
            Translation.getClient(options)
        }

        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitResult()

            val translatedText = translator.translate(trimmed).awaitResult()
            val latencyMs = System.currentTimeMillis() - startTime

            Log.i(TAG, "Translated [${sourceLanguage.bcp47Code} -> ${targetLanguage.bcp47Code}] in ${latencyMs}ms: '$trimmed' -> '$translatedText'")

            Result.success(
                TranslationResult(
                    sourceText = trimmed,
                    translatedText = translatedText,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    latencyMs = latencyMs
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit translation failed for '$trimmed'", e)
            Result.failure(Exception("Translation error: ${e.message}"))
        }
    }

    fun release() {
        for ((_, translator) in translatorCache) {
            translator.close()
        }
        translatorCache.clear()
    }
}

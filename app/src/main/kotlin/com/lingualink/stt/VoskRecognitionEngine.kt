package com.lingualink.stt

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

sealed class SttRecognitionState {
    object Idle : SttRecognitionState()
    data class LoadingModel(val language: SttLanguage) : SttRecognitionState()
    data class Listening(val language: SttLanguage) : SttRecognitionState()
    data class PartialResult(val text: String) : SttRecognitionState()
    data class FinalResult(val text: String, val latencyMs: Long) : SttRecognitionState()
    data class Error(val message: String) : SttRecognitionState()
}

/**
 * Real Speech Recognition Engine powered by Vosk Android.
 * Runs off the main thread (Dispatchers.IO).
 * Feeds 16 kHz 16-bit PCM samples into Vosk Recognizer.
 * Accurately reports true transcripts and clear errors for missing models.
 */
class VoskRecognitionEngine(
    private val context: Context,
    private val modelManager: SttModelManager
) {
    companion object {
        const val TAG = "STT"
        const val SAMPLE_RATE = 16000.0f
    }

    private var activeModel: Model? = null
    private var activeLanguage: SttLanguage? = null
    private var activeRecognizer: Recognizer? = null
    private var utteranceStartTimeMs: Long = 0L

    private val _state = MutableStateFlow<SttRecognitionState>(SttRecognitionState.Idle)
    val state: StateFlow<SttRecognitionState> = _state.asStateFlow()

    /**
     * Loads the Vosk model for the specified language off the main thread.
     */
    suspend fun prepareLanguage(language: SttLanguage): Result<Unit> = withContext(Dispatchers.IO) {
        if (!language.isSpeechInputSupported) {
            val err = language.unsupportedReason ?: "STT not supported for ${language.displayName}"
            _state.value = SttRecognitionState.Error(err)
            return@withContext Result.failure(UnsupportedOperationException(err))
        }

        if (activeLanguage == language && activeModel != null) {
            return@withContext Result.success(Unit)
        }

        _state.value = SttRecognitionState.LoadingModel(language)
        Log.i(TAG, "Loading Vosk model for ${language.displayName} off main thread...")

        if (!modelManager.isModelDownloaded(language)) {
            // Attempt to unpack bundled asset first
            val unpacked = modelManager.unpackBundledAssetIfNeeded(language)
            if (!unpacked) {
                val err = "Language pack not downloaded for ${language.displayName}. Please download it in Language Packs."
                _state.value = SttRecognitionState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }
        }

        val modelDir = modelManager.getModelDir(language)
        try {
            // Clean up old model if switching languages
            activeRecognizer?.close()
            activeRecognizer = null
            activeModel?.close()
            activeModel = null

            val loadStart = System.currentTimeMillis()
            activeModel = Model(modelDir.absolutePath)
            activeLanguage = language
            Log.i(TAG, "Vosk model for ${language.displayName} loaded in ${System.currentTimeMillis() - loadStart} ms")
            _state.value = SttRecognitionState.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model at ${modelDir.absolutePath}", e)
            val err = "Failed to load ${language.displayName} model: ${e.message}"
            _state.value = SttRecognitionState.Error(err)
            Result.failure(e)
        }
    }

    /**
     * Start listening session.
     */
    suspend fun startListening(language: SttLanguage): Result<Unit> = withContext(Dispatchers.IO) {
        val prep = prepareLanguage(language)
        if (prep.isFailure) {
            return@withContext prep
        }

        val model = activeModel ?: return@withContext Result.failure(IllegalStateException("Model not loaded"))

        try {
            activeRecognizer?.close()
            activeRecognizer = Recognizer(model, SAMPLE_RATE)
            utteranceStartTimeMs = System.currentTimeMillis()
            _state.value = SttRecognitionState.Listening(language)
            Log.i(TAG, "Vosk Recognizer session started for ${language.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Vosk Recognizer", e)
            _state.value = SttRecognitionState.Error("Recognizer error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Feed processed 16-bit PCM samples into the Vosk Recognizer.
     * Called by AudioCaptureManager bufferCallback.
     */
    fun processAudioBuffer(buffer: ShortArray, length: Int) {
        val recognizer = activeRecognizer ?: return

        try {
            val isFinal = recognizer.acceptWaveForm(buffer, length)
            if (isFinal) {
                val json = recognizer.result
                val text = parseTextFromJson(json)
                if (text.isNotBlank()) {
                    val latencyMs = System.currentTimeMillis() - utteranceStartTimeMs
                    Log.i(TAG, "STT Intermediate Result: '$text' (latency ${latencyMs}ms)")
                    _state.value = SttRecognitionState.PartialResult(text)
                }
            } else {
                val json = recognizer.partialResult
                val partial = parsePartialFromJson(json)
                if (partial.isNotBlank()) {
                    _state.value = SttRecognitionState.PartialResult(partial)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error feeding buffer to Vosk", e)
        }
    }

    /**
     * Stop listening and obtain the true final transcript.
     */
    suspend fun stopListening(): Result<String> = withContext(Dispatchers.IO) {
        val recognizer = activeRecognizer ?: return@withContext Result.failure(
            IllegalStateException("Recognizer not active")
        )

        try {
            val finalJson = recognizer.finalResult
            val latencyMs = System.currentTimeMillis() - utteranceStartTimeMs
            val text = parseTextFromJson(finalJson)

            recognizer.close()
            activeRecognizer = null

            if (text.isBlank()) {
                val err = "Couldn't recognise speech"
                _state.value = SttRecognitionState.Error(err)
                Log.w(TAG, "STT finished with empty speech result")
                Result.failure(Exception(err))
            } else {
                Log.i(TAG, "STT Final Result: '$text' (Total STT latency: ${latencyMs}ms)")
                _state.value = SttRecognitionState.FinalResult(text, latencyMs)
                Result.success(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing Vosk STT", e)
            _state.value = SttRecognitionState.Error("STT Finalize error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseTextFromJson(json: String): String {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            obj.get("text")?.asString?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parsePartialFromJson(json: String): String {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            obj.get("partial")?.asString?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun release() {
        try {
            activeRecognizer?.close()
            activeRecognizer = null
            activeModel?.close()
            activeModel = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Vosk resources", e)
        }
    }
}

package com.lingualink.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.lingualink.translation.TranslationLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class TtsStatus {
    object Idle : TtsStatus()
    data class Speaking(val text: String, val language: TranslationLanguage) : TtsStatus()
    data class Completed(val text: String, val latencyMs: Long) : TtsStatus()
    data class VoiceMissing(val language: TranslationLanguage, val installIntent: Intent) : TtsStatus()
    data class Error(val message: String) : TtsStatus()
}

/**
 * Module 3: Text-to-Speech Manager using Android's native TextToSpeech engine.
 * Supports en-IN, hi-IN, ta-IN, te-IN locales.
 * If voice data is missing, emits VoiceMissing with the intent to install TTS data.
 * Includes replay capability and synthesis latency measurement.
 */
class TtsManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        const val TAG = "TTS"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Idle)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private var lastSpokenText: String = ""
    private var lastSpokenLanguage: TranslationLanguage = TranslationLanguage.ENGLISH
    private var utteranceStartTimeMs: Long = 0L

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(initStatus: Int) {
        if (initStatus == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.i(TAG, "Android TextToSpeech engine successfully initialized")
            setupUtteranceListener()
        } else {
            isInitialized = false
            Log.e(TAG, "Failed to initialize Android TextToSpeech engine (status $initStatus)")
            _status.value = TtsStatus.Error("TTS Engine initialization failed")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.i(TAG, "TTS playback started for utterance $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                val latencyMs = System.currentTimeMillis() - utteranceStartTimeMs
                Log.i(TAG, "TTS playback finished in ${latencyMs}ms")
                _status.value = TtsStatus.Completed(lastSpokenText, latencyMs)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS playback error for utterance $utteranceId")
                _status.value = TtsStatus.Error("TTS playback error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS playback error ($errorCode) for utterance $utteranceId")
                _status.value = TtsStatus.Error("TTS error code: $errorCode")
            }
        })
    }

    /**
     * Map TranslationLanguage to correct Indian locale.
     */
    private fun getLocaleForLanguage(language: TranslationLanguage): Locale {
        return when (language) {
            TranslationLanguage.ENGLISH -> Locale("en", "IN")
            TranslationLanguage.HINDI -> Locale("hi", "IN")
            TranslationLanguage.TAMIL -> Locale("ta", "IN")
            TranslationLanguage.TELUGU -> Locale("te", "IN")
        }
    }

    /**
     * Speaks the specified text in the target language.
     * Checks if language voice data is available; prompts user if missing.
     */
    fun speak(text: String, language: TranslationLanguage): Boolean {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not ready to speak")
            _status.value = TtsStatus.Error("TTS engine is initializing...")
            return false
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        lastSpokenText = trimmed
        lastSpokenLanguage = language

        val locale = getLocaleForLanguage(language)
        val availability = tts!!.isLanguageAvailable(locale)

        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS voice data missing for ${language.displayName} (locale $locale, code $availability)")
            val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            _status.value = TtsStatus.VoiceMissing(language, installIntent)
            return false
        }

        try {
            tts!!.language = locale
            utteranceStartTimeMs = System.currentTimeMillis()
            _status.value = TtsStatus.Speaking(trimmed, language)

            val utteranceId = "ll_tts_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = tts!!.speak(trimmed, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                Log.i(TAG, "Queued TTS playback for [${language.displayName}]: '$trimmed'")
                return true
            } else {
                Log.e(TAG, "Failed queuing TTS playback: code $result")
                _status.value = TtsStatus.Error("Failed to speak text")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during TTS playback", e)
            _status.value = TtsStatus.Error("TTS error: ${e.message}")
            return false
        }
    }

    /**
     * Replays the last spoken utterance.
     */
    fun replay(): Boolean {
        if (lastSpokenText.isNotBlank()) {
            Log.i(TAG, "Replaying last spoken text in ${lastSpokenLanguage.displayName}")
            return speak(lastSpokenText, lastSpokenLanguage)
        }
        return false
    }

    fun stop() {
        try {
            tts?.stop()
            _status.value = TtsStatus.Idle
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}

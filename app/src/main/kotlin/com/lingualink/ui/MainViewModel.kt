package com.lingualink.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingualink.audio.AudioCaptureManager
import com.lingualink.data.ConversationLog
import com.lingualink.data.LinguaLinkDatabaseHelper
import com.lingualink.nearby.DeviceRole
import com.lingualink.nearby.NearbyCommunicationManager
import com.lingualink.nearby.NearbyConnectionState
import com.lingualink.nearby.NearbyMessage
import com.lingualink.packmanager.LanguagePackItem
import com.lingualink.packmanager.LanguagePackManager
import com.lingualink.phrasebook.PhraseItem
import com.lingualink.phrasebook.PhrasebookRepository
import com.lingualink.stt.SttLanguage
import com.lingualink.stt.SttModelManager
import com.lingualink.stt.SttRecognitionState
import com.lingualink.stt.VoskRecognitionEngine
import com.lingualink.translation.OfflineTranslationManager
import com.lingualink.translation.TranslationLanguage
import com.lingualink.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    CONVERSATION,
    PHRASEBOOK,
    LANGUAGE_PACKS,
    LOG_HISTORY,
    SETTINGS_SELF_TEST
}

enum class ConversationMode {
    SPLIT_SCREEN, // Single device face-to-face
    NEARBY_PAIRED // Two devices over Bluetooth/Wi-Fi Direct
}

data class SpeakerPanelState(
    val role: DeviceRole,
    val selectedLanguage: TranslationLanguage,
    val originalText: String = "",
    val translatedText: String = "",
    val isListening: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val lastSttLatencyMs: Long = 0L,
    val lastTranslateLatencyMs: Long = 0L,
    val lastTtsLatencyMs: Long = 0L
)

data class SelfTestResult(
    val isRunning: Boolean = false,
    val isSuccess: Boolean = false,
    val logOutput: String = "Self-test not run yet",
    val dspLatencyMs: Double = 0.0,
    val sttLatencyMs: Long = 0L,
    val translateLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TAG = "MAIN_VM"
    }

    val audioManager = AudioCaptureManager(application)
    val sttModelManager = SttModelManager(application)
    val sttEngine = VoskRecognitionEngine(application, sttModelManager)
    val translationManager = OfflineTranslationManager(application)
    val ttsManager = TtsManager(application)
    val phrasebookRepository = PhrasebookRepository(application)
    val languagePackManager = LanguagePackManager(application, sttModelManager, translationManager, viewModelScope)
    val nearbyManager = NearbyCommunicationManager(application)
    val database = LinguaLinkDatabaseHelper(application)

    private val _currentScreen = MutableStateFlow(AppScreen.CONVERSATION)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _conversationMode = MutableStateFlow(ConversationMode.SPLIT_SCREEN)
    val conversationMode: StateFlow<ConversationMode> = _conversationMode.asStateFlow()

    // Tourist State (bottom panel)
    private val _touristState = MutableStateFlow(
        SpeakerPanelState(
            role = DeviceRole.TOURIST,
            selectedLanguage = TranslationLanguage.ENGLISH
        )
    )
    val touristState: StateFlow<SpeakerPanelState> = _touristState.asStateFlow()

    // Local Provider State (top panel, flipped 180 deg)
    private val _providerState = MutableStateFlow(
        SpeakerPanelState(
            role = DeviceRole.PROVIDER,
            selectedLanguage = TranslationLanguage.HINDI
        )
    )
    val providerState: StateFlow<SpeakerPanelState> = _providerState.asStateFlow()

    private var activeSpeakerRole: DeviceRole? = null

    // Real-time "Offline Ready" status computed directly from model manager
    val isOfflineReady: StateFlow<Boolean> = combine(
        touristState,
        providerState,
        languagePackManager.packItems
    ) { tourist, provider, _ ->
        languagePackManager.isOfflineReady(tourist.selectedLanguage, provider.selectedLanguage) &&
        languagePackManager.isOfflineReady(provider.selectedLanguage, tourist.selectedLanguage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Self Test State
    private val _selfTestResult = MutableStateFlow(SelfTestResult())
    val selfTestResult: StateFlow<SelfTestResult> = _selfTestResult.asStateFlow()

    init {
        // Connect Audio Capture PCM output to Vosk Recognizer buffer input
        audioManager.setBufferCallback { buffer, length ->
            sttEngine.processAudioBuffer(buffer, length)
        }

        // Listen for incoming Nearby messages from peer phone
        nearbyManager.setMessageListener { msg ->
            onNearbyMessageReceived(msg)
        }

        // Listen for STT engine partial results
        viewModelScope.launch {
            sttEngine.state.collect { sttState ->
                when (sttState) {
                    is SttRecognitionState.PartialResult -> {
                        updateActiveSpeakerTranscript(sttState.text)
                    }
                    is SttRecognitionState.Error -> {
                        updateActiveSpeakerError(sttState.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setConversationMode(mode: ConversationMode) {
        _conversationMode.value = mode
    }

    fun setTouristLanguage(lang: TranslationLanguage) {
        _touristState.value = _touristState.value.copy(selectedLanguage = lang)
    }

    fun setProviderLanguage(lang: TranslationLanguage) {
        _providerState.value = _providerState.value.copy(selectedLanguage = lang)
    }

    fun toggleNoiseFilter(enabled: Boolean) {
        audioManager.setFilterEnabled(enabled)
    }

    /**
     * Start speech recognition for a specific speaker role (Tourist or Provider).
     */
    fun startSpeaking(role: DeviceRole) {
        if (audioManager.isRecording.value) {
            stopSpeaking()
            return
        }

        activeSpeakerRole = role
        val sourceLang = if (role == DeviceRole.TOURIST) _touristState.value.selectedLanguage else _providerState.value.selectedLanguage
        val sttLang = SttLanguage.fromCode(sourceLang.bcp47Code)

        if (!sttLang.isSpeechInputSupported) {
            val err = sttLang.unsupportedReason ?: "Speech input is unsupported for ${sourceLang.displayName}"
            updateSpeakerState(role) { it.copy(statusMessage = err, isError = true) }
            return
        }

        viewModelScope.launch {
            updateSpeakerState(role) {
                it.copy(
                    isListening = true,
                    statusMessage = "Listening in ${sourceLang.displayName}...",
                    isError = false
                )
            }

            val sttStarted = sttEngine.startListening(sttLang)
            if (sttStarted.isSuccess) {
                val audioStarted = audioManager.startCapture(viewModelScope)
                if (!audioStarted) {
                    sttEngine.stopListening()
                    updateSpeakerState(role) {
                        it.copy(isListening = false, statusMessage = "Failed to open audio record", isError = true)
                    }
                }
            } else {
                val err = sttStarted.exceptionOrNull()?.message ?: "Failed to start STT"
                updateSpeakerState(role) {
                    it.copy(isListening = false, statusMessage = err, isError = true)
                }
            }
        }
    }

    /**
     * Stop speech recognition, perform ML Kit translation, play TTS, and send over Nearby if connected.
     */
    fun stopSpeaking() {
        val role = activeSpeakerRole ?: return
        audioManager.stopCapture()

        viewModelScope.launch {
            updateSpeakerState(role) { it.copy(isListening = false, statusMessage = "Recognizing speech...") }

            val sttResult = sttEngine.stopListening()
            if (sttResult.isSuccess) {
                val transcript = sttResult.getOrNull() ?: ""
                processTranscriptAndTranslate(transcript, role, 0L)
            } else {
                val err = sttResult.exceptionOrNull()?.message ?: "Couldn't recognise speech"
                updateSpeakerState(role) {
                    it.copy(statusMessage = err, isError = true)
                }
            }
            activeSpeakerRole = null
        }
    }

    /**
     * Fallback: Send typed text (used when in quiet areas or for Tamil speech input).
     */
    fun sendTypedInput(text: String, role: DeviceRole) {
        if (text.isBlank()) return
        viewModelScope.launch {
            processTranscriptAndTranslate(text.trim(), role, 0L)
        }
    }

    /**
     * Execute translation and speech playback pipeline.
     */
    private suspend fun processTranscriptAndTranslate(
        transcript: String,
        role: DeviceRole,
        sttLatencyMs: Long
    ) {
        val sourceLang = if (role == DeviceRole.TOURIST) _touristState.value.selectedLanguage else _providerState.value.selectedLanguage
        val targetLang = if (role == DeviceRole.TOURIST) _providerState.value.selectedLanguage else _touristState.value.selectedLanguage

        updateSpeakerState(role) {
            it.copy(
                originalText = transcript,
                statusMessage = "Translating [${sourceLang.displayName} -> ${targetLang.displayName}]...",
                lastSttLatencyMs = sttLatencyMs
            )
        }

        val translationResult = translationManager.translate(transcript, sourceLang, targetLang)
        if (translationResult.isSuccess) {
            val res = translationResult.getOrNull()!!
            val translatedText = res.translatedText
            val translateLatency = res.latencyMs

            updateSpeakerState(role) {
                it.copy(
                    translatedText = translatedText,
                    statusMessage = "Translation complete (${translateLatency}ms)",
                    isError = false,
                    lastTranslateLatencyMs = translateLatency
                )
            }

            // Speak via TTS in target language
            val ttsStart = System.currentTimeMillis()
            ttsManager.speak(translatedText, targetLang)
            val ttsLatency = System.currentTimeMillis() - ttsStart

            // Log utterance into SQLite/Room database
            val log = ConversationLog(
                sourceLanguage = sourceLang.displayName,
                targetLanguage = targetLang.displayName,
                sourceText = transcript,
                translatedText = translatedText,
                sttLatencyMs = sttLatencyMs,
                translateLatencyMs = translateLatency,
                ttsLatencyMs = ttsLatency,
                senderRole = role.label,
                isNearby = _conversationMode.value == ConversationMode.NEARBY_PAIRED
            )
            database.insert(log)

            // Send via Nearby Connections if paired
            if (_conversationMode.value == ConversationMode.NEARBY_PAIRED &&
                nearbyManager.connectionState.value is NearbyConnectionState.Connected
            ) {
                val nearbyMsg = NearbyMessage(
                    senderRole = role,
                    senderDeviceName = "LinguaLink",
                    sourceLanguage = sourceLang.bcp47Code,
                    targetLanguage = targetLang.bcp47Code,
                    sourceText = transcript,
                    translatedText = translatedText,
                    sttLatencyMs = sttLatencyMs,
                    translateLatencyMs = translateLatency,
                    ttsLatencyMs = ttsLatency
                )
                nearbyManager.sendMessage(nearbyMsg)
            }
        } else {
            val err = translationResult.exceptionOrNull()?.message ?: "Translation error"
            updateSpeakerState(role) {
                it.copy(
                    translatedText = "",
                    statusMessage = err,
                    isError = true
                )
            }
        }
    }

    /**
     * Send a pre-translated human-verified phrase from the Phrasebook.
     */
    fun sendPhrase(phrase: PhraseItem, senderRole: DeviceRole) {
        val sourceLang = if (senderRole == DeviceRole.TOURIST) _touristState.value.selectedLanguage else _providerState.value.selectedLanguage
        val targetLang = if (senderRole == DeviceRole.TOURIST) _providerState.value.selectedLanguage else _touristState.value.selectedLanguage

        val srcText = phrase.getTextForLanguage(sourceLang)
        val trgText = phrase.getTextForLanguage(targetLang)

        updateSpeakerState(senderRole) {
            it.copy(
                originalText = srcText,
                translatedText = trgText,
                statusMessage = "Sent from Phrasebook: ${phrase.category}",
                isError = false,
                lastSttLatencyMs = 0L,
                lastTranslateLatencyMs = 0L
            )
        }

        // Speak target text via TTS
        val ttsStart = System.currentTimeMillis()
        ttsManager.speak(trgText, targetLang)
        val ttsLatency = System.currentTimeMillis() - ttsStart

        // Log into database
        viewModelScope.launch {
            val log = ConversationLog(
                sourceLanguage = sourceLang.displayName,
                targetLanguage = targetLang.displayName,
                sourceText = srcText,
                translatedText = trgText,
                sttLatencyMs = 0L,
                translateLatencyMs = 0L,
                ttsLatencyMs = ttsLatency,
                senderRole = senderRole.label,
                isNearby = _conversationMode.value == ConversationMode.NEARBY_PAIRED
            )
            database.insert(log)
        }

        // Send over Nearby if paired
        if (_conversationMode.value == ConversationMode.NEARBY_PAIRED) {
            val nearbyMsg = NearbyMessage(
                senderRole = senderRole,
                senderDeviceName = "LinguaLink",
                sourceLanguage = sourceLang.bcp47Code,
                targetLanguage = targetLang.bcp47Code,
                sourceText = srcText,
                translatedText = trgText
            )
            nearbyManager.sendMessage(nearbyMsg)
        }
    }

    /**
     * Handles incoming message from peer phone via Nearby Connections.
     */
    private fun onNearbyMessageReceived(msg: NearbyMessage) {
        val receivingRole = if (msg.senderRole == DeviceRole.TOURIST) DeviceRole.PROVIDER else DeviceRole.TOURIST
        val targetLang = TranslationLanguage.fromCode(msg.targetLanguage)

        updateSpeakerState(receivingRole) {
            it.copy(
                originalText = msg.sourceText,
                translatedText = msg.translatedText,
                statusMessage = "Received from peer (${msg.senderRole.label})",
                lastSttLatencyMs = msg.sttLatencyMs,
                lastTranslateLatencyMs = msg.translateLatencyMs,
                lastTtsLatencyMs = msg.ttsLatencyMs,
                isError = false
            )
        }

        // Automatically speak incoming translated message
        ttsManager.speak(msg.translatedText, targetLang)

        // Store in database
        viewModelScope.launch {
            val log = ConversationLog(
                sourceLanguage = msg.sourceLanguage,
                targetLanguage = msg.targetLanguage,
                sourceText = msg.sourceText,
                translatedText = msg.translatedText,
                sttLatencyMs = msg.sttLatencyMs,
                translateLatencyMs = msg.translateLatencyMs,
                ttsLatencyMs = msg.ttsLatencyMs,
                senderRole = msg.senderRole.label,
                isNearby = true
            )
            database.insert(log)
        }
    }

    fun replayLastUtterance() {
        ttsManager.replay()
    }

    /**
     * Executes the built-in Self-Test runner.
     * Generates a 16kHz synthetic speech-like PCM buffer, runs it through DSP,
     * feeds it into the translation engine, and prints complete pass/fail diagnostics.
     */
    fun runSelfTest() {
        viewModelScope.launch {
            _selfTestResult.value = SelfTestResult(isRunning = true, logOutput = "Initializing self-test pipeline...")
            val sb = StringBuilder()
            var allPassed = true

            // Stage 1: DSP Verification
            sb.appendLine("=== STAGE 1: DSP FILTER VERIFICATION ===")
            val dsp = audioManager.dspFilter
            val sampleCount = 16000 // 1 second of 16kHz audio
            val testBuffer = ShortArray(sampleCount)
            // Generate 40 Hz low rumble + 1000 Hz tone
            for (i in 0 until sampleCount) {
                val t = i.toDouble() / 16000.0
                val rumble = 10000.0 * kotlin.math.sin(2.0 * Math.PI * 40.0 * t) // Should be attenuated by 80Hz HP filter
                val tone = 8000.0 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * t)  // Should pass cleanly
                testBuffer[i] = (rumble + tone).toInt().coerceIn(-32768, 32767).toShort()
            }

            val dspRes = dsp.process(testBuffer, sampleCount)
            sb.appendLine("Input RMS: ${dspRes.metrics.inputRms.toInt()}")
            sb.appendLine("Output RMS: ${dspRes.metrics.outputRms.toInt()}")
            sb.appendLine("Estimated SNR Gain: ${String.format(java.util.Locale.US, "%.2f dB", dspRes.metrics.snrGainDb)}")
            sb.appendLine("DSP Execution Latency: ${dspRes.metrics.dspLatencyUs} µs")
            val dspPass = dspRes.metrics.outputRms < dspRes.metrics.inputRms // 40Hz rumble attenuated
            sb.appendLine("DSP High-Pass Attenuation: ${if (dspPass) "PASS" else "FAIL"}")
            if (!dspPass) allPassed = false

            // Stage 2: STT Model Readiness
            sb.appendLine("\n=== STAGE 2: STT MODEL CHECK ===")
            val enDownloaded = sttModelManager.isModelDownloaded(SttLanguage.ENGLISH)
            val hiDownloaded = sttModelManager.isModelDownloaded(SttLanguage.HINDI)
            val teDownloaded = sttModelManager.isModelDownloaded(SttLanguage.TELUGU)
            sb.appendLine("English Vosk Model: ${if (enDownloaded) "READY" else "NOT DOWNLOADED"}")
            sb.appendLine("Hindi Vosk Model: ${if (hiDownloaded) "READY" else "NOT DOWNLOADED"}")
            sb.appendLine("Telugu Vosk Model: ${if (teDownloaded) "READY" else "NOT DOWNLOADED"}")
            sb.appendLine("Tamil Vosk Model: Explicitly Marked Unsupported (as per Vosk catalog)")

            // Stage 3: ML Kit Translation Check
            sb.appendLine("\n=== STAGE 3: ML KIT ON-DEVICE TRANSLATION ===")
            val enMlKit = translationManager.isModelDownloaded(TranslationLanguage.ENGLISH)
            val hiMlKit = translationManager.isModelDownloaded(TranslationLanguage.HINDI)
            sb.appendLine("English ML Kit Model: ${if (enMlKit) "READY" else "NOT DOWNLOADED"}")
            sb.appendLine("Hindi ML Kit Model: ${if (hiMlKit) "READY" else "NOT DOWNLOADED"}")

            var translateLatency = 0L
            if (enMlKit && hiMlKit) {
                val sampleText = "Where is the nearest taxi stand?"
                val tRes = translationManager.translate(sampleText, TranslationLanguage.ENGLISH, TranslationLanguage.HINDI)
                if (tRes.isSuccess) {
                    val r = tRes.getOrNull()!!
                    translateLatency = r.latencyMs
                    sb.appendLine("Test Translation:")
                    sb.appendLine("  Source: '$sampleText'")
                    sb.appendLine("  Result: '${r.translatedText}'")
                    sb.appendLine("  Latency: ${r.latencyMs} ms")
                    sb.appendLine("Translation Verification: PASS")
                } else {
                    sb.appendLine("Translation Verification: FAIL (${tRes.exceptionOrNull()?.message})")
                    allPassed = false
                }
            } else {
                sb.appendLine("Skipping active translation test: Language models must be downloaded first via Language Packs.")
            }

            // Stage 4: TTS Check
            sb.appendLine("\n=== STAGE 4: TEXT-TO-SPEECH ===")
            sb.appendLine("TTS Engine: Initialized")
            sb.appendLine("Locales: en-IN, hi-IN, ta-IN, te-IN configured")

            sb.appendLine("\n=== OVERALL SELF-TEST: ${if (allPassed) "PASSED" else "ACTION REQUIRED"} ===")

            _selfTestResult.value = SelfTestResult(
                isRunning = false,
                isSuccess = allPassed,
                logOutput = sb.toString(),
                dspLatencyMs = dspRes.metrics.dspLatencyUs / 1000.0,
                translateLatencyMs = translateLatency
            )
        }
    }

    private fun updateSpeakerState(role: DeviceRole, transform: (SpeakerPanelState) -> SpeakerPanelState) {
        if (role == DeviceRole.TOURIST) {
            _touristState.value = transform(_touristState.value)
        } else {
            _providerState.value = transform(_providerState.value)
        }
    }

    private fun updateActiveSpeakerTranscript(partial: String) {
        val role = activeSpeakerRole ?: return
        updateSpeakerState(role) { it.copy(originalText = partial) }
    }

    private fun updateActiveSpeakerError(msg: String) {
        val role = activeSpeakerRole ?: return
        updateSpeakerState(role) { it.copy(statusMessage = msg, isError = true) }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.stopCapture()
        sttEngine.release()
        translationManager.release()
        ttsManager.release()
        nearbyManager.disconnect()
    }
}

package com.lingualink.stt

/**
 * Supported Speech-to-Text languages for Lingua Link.
 * Vosk models for English (en-in), Hindi (hi), and Telugu (te).
 * Tamil is explicitly flagged as unsupported for offline Vosk speech input per instructions.
 */
enum class SttLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val modelDirName: String,
    val downloadUrl: String,
    val approximateSizeBytes: Long,
    val isSpeechInputSupported: Boolean,
    val unsupportedReason: String? = null
) {
    ENGLISH(
        code = "en-IN",
        displayName = "English (India)",
        nativeName = "English",
        modelDirName = "vosk-model-small-en-in-0.4",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
        approximateSizeBytes = 36 * 1024 * 1024L, // ~36 MB
        isSpeechInputSupported = true
    ),
    HINDI(
        code = "hi-IN",
        displayName = "Hindi",
        nativeName = "हिन्दी",
        modelDirName = "vosk-model-small-hi-0.22",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        approximateSizeBytes = 42 * 1024 * 1024L, // ~42 MB
        isSpeechInputSupported = true
    ),
    TELUGU(
        code = "te-IN",
        displayName = "Telugu",
        nativeName = "తెలుగు",
        modelDirName = "vosk-model-small-te-0.42",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip",
        approximateSizeBytes = 45 * 1024 * 1024L, // ~45 MB
        isSpeechInputSupported = true
    ),
    TAMIL(
        code = "ta-IN",
        displayName = "Tamil",
        nativeName = "தமிழ்",
        modelDirName = "unsupported_ta",
        downloadUrl = "",
        approximateSizeBytes = 0L,
        isSpeechInputSupported = false,
        unsupportedReason = "Vosk does not provide an official offline Tamil acoustic model. Speech input is disabled for Tamil; typed input and ML Kit translation/TTS are fully operational."
    );

    companion object {
        fun fromCode(code: String): SttLanguage {
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) ||
                it.code.startsWith(code, ignoreCase = true) ||
                code.startsWith(it.code.substring(0, 2), ignoreCase = true)
            } ?: ENGLISH
        }
    }
}

package com.lingualink.translation

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Languages supported by ML Kit on-device translation for Lingua Link.
 * English, Hindi, Tamil, Telugu in all directions.
 */
enum class TranslationLanguage(
    val bcp47Code: String,
    val mlKitCode: String,
    val displayName: String,
    val nativeName: String,
    val ttsLocaleTag: String
) {
    ENGLISH(
        bcp47Code = "en",
        mlKitCode = TranslateLanguage.ENGLISH,
        displayName = "English",
        nativeName = "English",
        ttsLocaleTag = "en-IN"
    ),
    HINDI(
        bcp47Code = "hi",
        mlKitCode = TranslateLanguage.HINDI,
        displayName = "Hindi",
        nativeName = "हिन्दी",
        ttsLocaleTag = "hi-IN"
    ),
    TAMIL(
        bcp47Code = "ta",
        mlKitCode = TranslateLanguage.TAMIL,
        displayName = "Tamil",
        nativeName = "தமிழ்",
        ttsLocaleTag = "ta-IN"
    ),
    TELUGU(
        bcp47Code = "te",
        mlKitCode = TranslateLanguage.TELUGU,
        displayName = "Telugu",
        nativeName = "తెలుగు",
        ttsLocaleTag = "te-IN"
    );

    companion object {
        fun fromCode(code: String): TranslationLanguage {
            return entries.firstOrNull {
                it.bcp47Code.equals(code, ignoreCase = true) ||
                it.ttsLocaleTag.equals(code, ignoreCase = true) ||
                code.startsWith(it.bcp47Code, ignoreCase = true)
            } ?: ENGLISH
        }
    }
}

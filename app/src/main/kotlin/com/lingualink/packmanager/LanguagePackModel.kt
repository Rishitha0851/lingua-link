package com.lingualink.packmanager

enum class PackType {
    STT_VOSK,
    TRANSLATION_MLKIT
}

enum class PackStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    UNSUPPORTED,
    ERROR
}

data class LanguagePackItem(
    val id: String,
    val type: PackType,
    val languageCode: String,
    val languageName: String,
    val nativeName: String,
    val sizeText: String,
    val status: PackStatus,
    val progressPercent: Int = 0,
    val statusMessage: String? = null,
    val isActionEnabled: Boolean = true
)

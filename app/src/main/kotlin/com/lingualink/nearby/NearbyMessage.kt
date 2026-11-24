package com.lingualink.nearby

import com.lingualink.translation.TranslationLanguage

enum class DeviceRole(val label: String) {
    TOURIST("Tourist"),
    PROVIDER("Local Service Provider")
}

data class NearbyMessage(
    val id: String = "msg_${System.currentTimeMillis()}",
    val senderRole: DeviceRole,
    val senderDeviceName: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceText: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sttLatencyMs: Long = 0L,
    val translateLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L
)

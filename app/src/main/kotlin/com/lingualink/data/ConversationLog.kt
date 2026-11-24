package com.lingualink.data

data class ConversationLog(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceText: String,
    val translatedText: String,
    val sttLatencyMs: Long,
    val translateLatencyMs: Long,
    val ttsLatencyMs: Long,
    val senderRole: String,
    val isNearby: Boolean = false
) {
    val totalLatencyMs: Long
        get() = sttLatencyMs + translateLatencyMs + ttsLatencyMs
}

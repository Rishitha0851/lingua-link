package com.lingualink.phrasebook

import com.lingualink.translation.TranslationLanguage

data class PhraseItem(
    val id: String,
    val category: String,
    val english: String,
    val hindi: String,
    val tamil: String,
    val telugu: String,
    val notes: String
) {
    fun getTextForLanguage(language: TranslationLanguage): String {
        return when (language) {
            TranslationLanguage.ENGLISH -> english
            TranslationLanguage.HINDI -> hindi
            TranslationLanguage.TAMIL -> tamil
            TranslationLanguage.TELUGU -> telugu
        }
    }
}

package com.lingualink.phrasebook

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads human-verified offline phrases across English, Hindi, Tamil, and Telugu
 * from assets/phrasebook.json.
 */
class PhrasebookRepository(private val context: Context) {

    companion object {
        const val TAG = "PHRASEBOOK"
        const val ASSET_FILE = "phrasebook.json"
    }

    private var cachedPhrases: List<PhraseItem>? = null

    suspend fun getPhrases(): List<PhraseItem> = withContext(Dispatchers.IO) {
        cachedPhrases?.let { return@withContext it }

        try {
            val jsonString = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val itemType = object : TypeToken<List<PhraseItem>>() {}.type
            val list: List<PhraseItem> = Gson().fromJson(jsonString, itemType)
            cachedPhrases = list
            Log.i(TAG, "Successfully loaded ${list.size} human-verified phrases from assets")
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load phrasebook from assets", e)
            emptyList()
        }
    }

    suspend fun getCategories(): List<String> {
        val phrases = getPhrases()
        return phrases.map { it.category }.distinct()
    }

    suspend fun searchPhrases(query: String, category: String? = null): List<PhraseItem> {
        val phrases = getPhrases()
        val q = query.trim().lowercase()

        return phrases.filter { item ->
            val matchesCategory = category == null || category == "All" || item.category.equals(category, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                    item.english.lowercase().contains(q) ||
                    item.hindi.lowercase().contains(q) ||
                    item.tamil.lowercase().contains(q) ||
                    item.telugu.lowercase().contains(q) ||
                    item.notes.lowercase().contains(q)

            matchesCategory && matchesQuery
        }
    }
}

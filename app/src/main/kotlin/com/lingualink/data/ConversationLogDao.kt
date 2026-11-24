package com.lingualink.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Data Access Object for Conversation Logs storing every utterance with
 * timestamp, language pair, transcript, translation, and per-stage latency (STT, translate, TTS).
 */
interface ConversationLogDao {
    suspend fun insert(log: ConversationLog): Long
    suspend fun getAllLogs(): List<ConversationLog>
    suspend fun clearLogs()
    suspend fun getAverageLatencies(): Triple<Double, Double, Double>
    val logsFlow: StateFlow<List<ConversationLog>>
}

class LinguaLinkDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), ConversationLogDao {

    companion object {
        const val DATABASE_NAME = "lingualink.db"
        const val DATABASE_VERSION = 1
        const val TABLE_LOGS = "conversation_logs"

        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_SOURCE_LANG = "source_language"
        const val COL_TARGET_LANG = "target_language"
        const val COL_SOURCE_TEXT = "source_text"
        const val COL_TRANSLATED_TEXT = "translated_text"
        const val COL_STT_LATENCY = "stt_latency_ms"
        const val COL_TRANSLATE_LATENCY = "translate_latency_ms"
        const val COL_TTS_LATENCY = "tts_latency_ms"
        const val COL_SENDER_ROLE = "sender_role"
        const val COL_IS_NEARBY = "is_nearby"
    }

    private val _logsFlow = MutableStateFlow<List<ConversationLog>>(emptyList())
    override val logsFlow: StateFlow<List<ConversationLog>> = _logsFlow.asStateFlow()

    init {
        // Load initial records
        loadLogsIntoFlow()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_LOGS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_SOURCE_LANG TEXT NOT NULL,
                $COL_TARGET_LANG TEXT NOT NULL,
                $COL_SOURCE_TEXT TEXT NOT NULL,
                $COL_TRANSLATED_TEXT TEXT NOT NULL,
                $COL_STT_LATENCY INTEGER NOT NULL,
                $COL_TRANSLATE_LATENCY INTEGER NOT NULL,
                $COL_TTS_LATENCY INTEGER NOT NULL,
                $COL_SENDER_ROLE TEXT NOT NULL,
                $COL_IS_NEARBY INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOGS")
        onCreate(db)
    }

    private fun loadLogsIntoFlow() {
        try {
            val list = queryAllLogsInternal()
            _logsFlow.value = list
        } catch (e: Exception) {
            _logsFlow.value = emptyList()
        }
    }

    private fun queryAllLogsInternal(): List<ConversationLog> {
        val list = mutableListOf<ConversationLog>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val log = ConversationLog(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    sourceLanguage = it.getString(it.getColumnIndexOrThrow(COL_SOURCE_LANG)),
                    targetLanguage = it.getString(it.getColumnIndexOrThrow(COL_TARGET_LANG)),
                    sourceText = it.getString(it.getColumnIndexOrThrow(COL_SOURCE_TEXT)),
                    translatedText = it.getString(it.getColumnIndexOrThrow(COL_TRANSLATED_TEXT)),
                    sttLatencyMs = it.getLong(it.getColumnIndexOrThrow(COL_STT_LATENCY)),
                    translateLatencyMs = it.getLong(it.getColumnIndexOrThrow(COL_TRANSLATE_LATENCY)),
                    ttsLatencyMs = it.getLong(it.getColumnIndexOrThrow(COL_TTS_LATENCY)),
                    senderRole = it.getString(it.getColumnIndexOrThrow(COL_SENDER_ROLE)),
                    isNearby = it.getInt(it.getColumnIndexOrThrow(COL_IS_NEARBY)) == 1
                )
                list.add(log)
            }
        }
        return list
    }

    override suspend fun insert(log: ConversationLog): Long = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, log.timestamp)
            put(COL_SOURCE_LANG, log.sourceLanguage)
            put(COL_TARGET_LANG, log.targetLanguage)
            put(COL_SOURCE_TEXT, log.sourceText)
            put(COL_TRANSLATED_TEXT, log.translatedText)
            put(COL_STT_LATENCY, log.sttLatencyMs)
            put(COL_TRANSLATE_LATENCY, log.translateLatencyMs)
            put(COL_TTS_LATENCY, log.ttsLatencyMs)
            put(COL_SENDER_ROLE, log.senderRole)
            put(COL_IS_NEARBY, if (log.isNearby) 1 else 0)
        }
        val insertedId = db.insert(TABLE_LOGS, null, values)
        loadLogsIntoFlow()
        insertedId
    }

    override suspend fun getAllLogs(): List<ConversationLog> = withContext(Dispatchers.IO) {
        queryAllLogsInternal()
    }

    override suspend fun clearLogs() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_LOGS, null, null)
        loadLogsIntoFlow()
    }

    override suspend fun getAverageLatencies(): Triple<Double, Double, Double> = withContext(Dispatchers.IO) {
        val logs = queryAllLogsInternal()
        if (logs.isEmpty()) return@withContext Triple(0.0, 0.0, 0.0)

        val avgStt = logs.map { it.sttLatencyMs }.average()
        val avgTranslate = logs.map { it.translateLatencyMs }.average()
        val avgTts = logs.map { it.ttsLatencyMs }.average()

        Triple(avgStt, avgTranslate, avgTts)
    }
}

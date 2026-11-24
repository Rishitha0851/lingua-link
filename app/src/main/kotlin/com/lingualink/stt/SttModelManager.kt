package com.lingualink.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int) : ModelDownloadState()
    object Ready : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

/**
 * Manages offline STT Vosk model directories, unbundling from assets,
 * downloading from official model repositories, and reporting download status.
 */
class SttModelManager(private val context: Context) {

    companion object {
        const val TAG = "STT"
        const val MODELS_DIR_NAME = "models"
    }

    private val modelsBaseDir: File
        get() = File(context.filesDir, MODELS_DIR_NAME).apply { if (!exists()) mkdirs() }

    private val _downloadStates = MutableStateFlow<Map<SttLanguage, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<SttLanguage, ModelDownloadState>> = _downloadStates.asStateFlow()

    init {
        refreshStatuses()
    }

    fun getModelDir(language: SttLanguage): File {
        return File(modelsBaseDir, language.modelDirName)
    }

    fun isModelDownloaded(language: SttLanguage): Boolean {
        if (!language.isSpeechInputSupported) return false
        val dir = getModelDir(language)
        // A valid Vosk model directory contains am/ or conf/ or final.mdl or similar files
        if (!dir.exists() || !dir.isDirectory) return false
        val contents = dir.listFiles() ?: return false
        return contents.isNotEmpty()
    }

    fun refreshStatuses() {
        val states = mutableMapOf<SttLanguage, ModelDownloadState>()
        for (lang in SttLanguage.entries) {
            states[lang] = if (!lang.isSpeechInputSupported) {
                ModelDownloadState.Error(lang.unsupportedReason ?: "Unsupported")
            } else if (isModelDownloaded(lang)) {
                ModelDownloadState.Ready
            } else {
                ModelDownloadState.NotDownloaded
            }
        }
        _downloadStates.value = states
    }

    /**
     * Unpack a bundled model from assets if available (e.g. assets/models/<modelDirName>.zip).
     */
    suspend fun unpackBundledAssetIfNeeded(language: SttLanguage): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded(language)) return@withContext true
        val assetPath = "models/${language.modelDirName}.zip"
        try {
            context.assets.open(assetPath).use { input ->
                Log.i(TAG, "Unpacking bundled asset model: $assetPath")
                updateState(language, ModelDownloadState.Downloading(10))
                unzipStream(input, modelsBaseDir)
                updateState(language, ModelDownloadState.Ready)
                Log.i(TAG, "Successfully unpacked bundled model for ${language.displayName}")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.d(TAG, "No bundled asset found for ${language.displayName}: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Download and unpack model from official Alphacephei repository.
     */
    suspend fun downloadModel(
        language: SttLanguage,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!language.isSpeechInputSupported) {
            return@withContext Result.failure(
                IllegalStateException(language.unsupportedReason ?: "STT not supported for ${language.displayName}")
            )
        }

        if (isModelDownloaded(language)) {
            updateState(language, ModelDownloadState.Ready)
            return@withContext Result.success(getModelDir(language))
        }

        // Check if bundled in assets first
        if (unpackBundledAssetIfNeeded(language)) {
            return@withContext Result.success(getModelDir(language))
        }

        if (language.downloadUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No download URL for ${language.displayName}"))
        }

        updateState(language, ModelDownloadState.Downloading(0))
        var connection: HttpURLConnection? = null
        try {
            Log.i(TAG, "Starting download for ${language.displayName} from ${language.downloadUrl}")
            val url = URL(language.downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                val errorMsg = "HTTP Error ${connection.responseCode}: ${connection.responseMessage}"
                updateState(language, ModelDownloadState.Error(errorMsg))
                return@withContext Result.failure(Exception(errorMsg))
            }

            val totalBytes = connection.contentLengthLong.let { if (it > 0) it else language.approximateSizeBytes }
            val tempZip = File(modelsBaseDir, "${language.modelDirName}.zip.tmp")

            connection.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportedPercent = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt()
                            if (percent > lastReportedPercent) {
                                lastReportedPercent = percent
                                updateState(language, ModelDownloadState.Downloading(percent))
                                onProgress?.invoke(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            // Unzip the downloaded zip file
            updateState(language, ModelDownloadState.Downloading(99))
            Log.i(TAG, "Download complete. Extracting ${tempZip.name} to ${modelsBaseDir.absolutePath}")
            tempZip.inputStream().use { fileIn ->
                unzipStream(fileIn, modelsBaseDir)
            }
            tempZip.delete()

            val targetDir = getModelDir(language)
            if (isModelDownloaded(language)) {
                updateState(language, ModelDownloadState.Ready)
                Log.i(TAG, "Model ready for ${language.displayName} at ${targetDir.absolutePath}")
                Result.success(targetDir)
            } else {
                val err = "Model directory verification failed after unzip"
                updateState(language, ModelDownloadState.Error(err))
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model for ${language.displayName}", e)
            updateState(language, ModelDownloadState.Error(e.message ?: "Download failed"))
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun deleteModel(language: SttLanguage): Boolean {
        val dir = getModelDir(language)
        return try {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            refreshStatuses()
            Log.i(TAG, "Deleted model for ${language.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model for ${language.displayName}", e)
            false
        }
    }

    private fun updateState(language: SttLanguage, state: ModelDownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[language] = state
        _downloadStates.value = current
    }

    private fun unzipStream(inputStream: java.io.InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                // Guard against Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw SecurityException("Zip traversal vulnerability in entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (zis.read(buffer).also { count = it } != -1) {
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

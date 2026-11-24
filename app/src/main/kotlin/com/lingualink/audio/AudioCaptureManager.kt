package com.lingualink.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Module 1: Speech Capture and Noise Suppression
 * - AudioRecord at 16,000 Hz, mono, PCM 16-bit in a foreground-safe coroutine.
 * - Attaches NoiseSuppressor, AutomaticGainControl, AcousticEchoCanceler if available.
 * - Applies 80 Hz high-pass biquad filter + adaptive noise gate.
 * - Noise Filter ON/OFF toggle truly bypasses or applies processing.
 * - Computes real-time metrics: input RMS, output RMS, SNR gain (dB), DSP latency (ns).
 */
class AudioCaptureManager(
    private val context: Context
) {
    companion object {
        const val TAG = "AUDIO"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    val dspFilter = AudioDspFilter(sampleRate = SAMPLE_RATE, cutoffHz = 80.0, isFilterEnabled = true)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _metrics = MutableStateFlow(AudioMetrics())
    val metrics: StateFlow<AudioMetrics> = _metrics.asStateFlow()

    private val _isFilterEnabled = MutableStateFlow(true)
    val isFilterEnabled: StateFlow<Boolean> = _isFilterEnabled.asStateFlow()

    // Recent normalized waveform buffer (64 points)
    private val waveformBuffer = FloatArray(64)
    private var waveformIndex = 0

    private var recordingJob: Job? = null
    private var bufferCallback: ((ShortArray, Int) -> Unit)? = null

    fun setFilterEnabled(enabled: Boolean) {
        dspFilter.isFilterEnabled = enabled
        _isFilterEnabled.value = enabled
        Log.i(TAG, "DSP Noise Filter toggled: $enabled")
    }

    fun setBufferCallback(callback: (ShortArray, Int) -> Unit) {
        this.bufferCallback = callback
    }

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope): Boolean {
        if (_isRecording.value) return true

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            Log.e(TAG, "Invalid AudioRecord buffer size: $minBufSize")
            return false
        }
        val bufferSize = minBufSize * BUFFER_SIZE_FACTOR

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val sessionId = audioRecord!!.audioSessionId

            // Attach hardware acoustic effects if available
            attachHardwareEffects(sessionId)

            audioRecord!!.startRecording()
            _isRecording.value = true
            dspFilter.reset()
            Log.i(TAG, "AudioRecord started at 16000Hz mono 16-bit PCM (Session $sessionId)")

            recordingJob = scope.launch(Dispatchers.Default) {
                val readBuffer = ShortArray(1024)
                while (isActive && _isRecording.value) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (read > 0) {
                        // Real-time DSP filtering & metrics calculation
                        val result = dspFilter.process(readBuffer, read)

                        // Update waveform history
                        updateWaveform(result.output, read)

                        val updatedMetrics = result.metrics.copy(
                            waveformSamples = waveformBuffer.clone()
                        )
                        _metrics.value = updatedMetrics

                        // Pass processed buffer to STT recognizer
                        bufferCallback?.invoke(result.output, read)
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord capture", e)
            stopCapture()
            return false
        }
    }

    fun stopCapture() {
        if (!_isRecording.value && audioRecord == null) return
        _isRecording.value = false

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        releaseHardwareEffects()
        _metrics.value = AudioMetrics(isFilterEnabled = _isFilterEnabled.value)
        Log.i(TAG, "Audio capture stopped and resources released")
    }

    private fun attachHardwareEffects(sessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware NoiseSuppressor enabled on session $sessionId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
            }
        } else {
            Log.i(TAG, "Hardware NoiseSuppressor not available on this device")
        }

        if (AutomaticGainControl.isAvailable()) {
            try {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware AutomaticGainControl enabled on session $sessionId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AGC: ${e.message}")
            }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware AcousticEchoCanceler enabled on session $sessionId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AEC: ${e.message}")
            }
        }
    }

    private fun releaseHardwareEffects() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
            automaticGainControl?.release()
            automaticGainControl = null
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing hardware effects: ${e.message}")
        }
    }

    private fun updateWaveform(samples: ShortArray, length: Int) {
        // Downsample buffer into waveformBuffer
        val step = maxOf(1, length / 4)
        for (i in 0 until length step step) {
            val normalized = samples[i].toFloat() / 32768.0f
            waveformBuffer[waveformIndex] = normalized
            waveformIndex = (waveformIndex + 1) % waveformBuffer.size
        }
    }
}

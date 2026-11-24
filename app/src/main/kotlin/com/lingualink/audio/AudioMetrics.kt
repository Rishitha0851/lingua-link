package com.lingualink.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time computed audio metrics from sample buffers.
 * Every figure displayed in the UI is mathematically calculated from actual PCM samples.
 */
data class AudioMetrics(
    val inputRms: Double = 0.0,
    val outputRms: Double = 0.0,
    val snrGainDb: Double = 0.0,
    val dspLatencyNs: Long = 0L,
    val isFilterEnabled: Boolean = true,
    val waveformSamples: FloatArray = FloatArray(64)
) {
    /**
     * Latency in microseconds.
     */
    val dspLatencyUs: Double
        get() = dspLatencyNs / 1000.0

    /**
     * Input level in decibels relative to full scale (dBFS), range -90 dB to 0 dB.
     */
    val inputDb: Double
        get() = 20.0 * log10(max(1.0, inputRms) / 32767.0)

    /**
     * Output level in decibels relative to full scale (dBFS), range -90 dB to 0 dB.
     */
    val outputDb: Double
        get() = 20.0 * log10(max(1.0, outputRms) / 32767.0)

    /**
     * Normalized output volume fraction from 0.0 to 1.0 for UI progress bar.
     */
    val normalizedLevel: Float
        get() {
            // Map [-60 dB, 0 dB] -> [0.0, 1.0]
            val db = outputDb
            val norm = (db + 60.0) / 60.0
            return max(0.0f, min(1.0f, norm.toFloat()))
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioMetrics) return false
        if (inputRms != other.inputRms) return false
        if (outputRms != other.outputRms) return false
        if (snrGainDb != other.snrGainDb) return false
        if (dspLatencyNs != other.dspLatencyNs) return false
        if (isFilterEnabled != other.isFilterEnabled) return false
        if (!waveformSamples.contentEquals(other.waveformSamples)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = inputRms.hashCode()
        result = 31 * result + outputRms.hashCode()
        result = 31 * result + snrGainDb.hashCode()
        result = 31 * result + dspLatencyNs.hashCode()
        result = 31 * result + isFilterEnabled.hashCode()
        result = 31 * result + waveformSamples.contentHashCode()
        return result
    }
}

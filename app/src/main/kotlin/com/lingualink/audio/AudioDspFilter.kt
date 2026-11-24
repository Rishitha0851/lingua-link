package com.lingualink.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time Software DSP Filter:
 * 1. 2nd-order Butterworth 80 Hz High-Pass Biquad Filter (Direct Form II Transposed)
 *    to eliminate DC offset, handling rumble, and plosives.
 * 2. Adaptive Noise Gate with real-time background noise floor tracking and smooth
 *    attack/release envelope smoothing.
 * 3. True bypass switch (ON/OFF) that measurably changes audio and metrics.
 */
class AudioDspFilter(
    val sampleRate: Int = 16000,
    val cutoffHz: Double = 80.0,
    var isFilterEnabled: Boolean = true
) {
    // Biquad coefficients (normalized)
    private var b0: Double = 1.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0

    // Direct Form II Transposed state registers
    private var z1: Double = 0.0
    private var z2: Double = 0.0

    // Noise gate parameters & state
    private var noiseFloorIn: Double = 200.0
    private var noiseFloorOut: Double = 100.0
    private var gateGain: Double = 1.0
    private val attackCoeff = 0.15   // Fast opening of gate
    private val releaseCoeff = 0.01  // Smooth closure of gate

    init {
        computeBiquadCoefficients()
    }

    private fun computeBiquadCoefficients() {
        val q = 1.0 / sqrt(2.0) // Butterworth Q = 0.7071
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val rawB0 = (1.0 + cosW0) / 2.0
        val rawB1 = -(1.0 + cosW0)
        val rawB2 = (1.0 + cosW0) / 2.0
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha

        // Normalize by rawA0
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
        noiseFloorIn = 200.0
        noiseFloorOut = 100.0
        gateGain = 1.0
    }

    /**
     * Process an array of 16-bit PCM samples in place or copy to output.
     * Computes real metrics: input RMS, output RMS, estimated SNR gain (dB), DSP latency (ns).
     */
    fun process(input: ShortArray, length: Int): DspProcessResult {
        val startTime = System.nanoTime()

        if (length <= 0) {
            return DspProcessResult(
                output = ShortArray(0),
                metrics = AudioMetrics(0.0, 0.0, 0.0, 0L, isFilterEnabled)
            )
        }

        val output = ShortArray(length)
        var sumSquareIn = 0.0
        var sumSquareOut = 0.0

        if (!isFilterEnabled) {
            // True bypass: raw samples copied directly
            for (i in 0 until length) {
                val sample = input[i]
                output[i] = sample
                val s = sample.toDouble()
                sumSquareIn += s * s
            }
            sumSquareOut = sumSquareIn

            val rmsIn = sqrt(sumSquareIn / length)
            val rmsOut = rmsIn
            val elapsedNs = System.nanoTime() - startTime

            // Bypass: estimated SNR gain is 0 dB
            return DspProcessResult(
                output = output,
                metrics = AudioMetrics(
                    inputRms = rmsIn,
                    outputRms = rmsOut,
                    snrGainDb = 0.0,
                    dspLatencyNs = elapsedNs,
                    isFilterEnabled = false
                )
            )
        }

        // Active DSP processing
        for (i in 0 until length) {
            val rawSample = input[i].toDouble()
            sumSquareIn += rawSample * rawSample

            // 1. Direct Form II Transposed High-Pass Biquad
            val hpOut = b0 * rawSample + z1
            z1 = b1 * rawSample - a1 * hpOut + z2
            z2 = b2 * rawSample - a2 * hpOut

            // Update input noise floor estimate (slow tracking)
            val absIn = kotlin.math.abs(rawSample)
            if (absIn < noiseFloorIn) {
                noiseFloorIn = noiseFloorIn * 0.999 + absIn * 0.001
            } else {
                noiseFloorIn = noiseFloorIn * 0.9999 + absIn * 0.0001
            }

            // 2. Adaptive Noise Gate
            val absHp = kotlin.math.abs(hpOut)
            val gateThreshold = max(180.0, noiseFloorIn * 2.2)

            val targetGain = if (absHp > gateThreshold) {
                1.0
            } else {
                // Attenuate down to 0.08 (-22 dB)
                0.08
            }

            // Envelope follower smoothing
            val coeff = if (targetGain > gateGain) attackCoeff else releaseCoeff
            gateGain += (targetGain - gateGain) * coeff

            val gatedSample = hpOut * gateGain

            // Clamp to 16-bit signed range
            val clamped = min(32767.0, max(-32768.0, gatedSample))
            output[i] = clamped.toInt().toShort()

            sumSquareOut += clamped * clamped

            // Update output noise floor
            val absOut = kotlin.math.abs(clamped)
            if (absOut < noiseFloorOut) {
                noiseFloorOut = noiseFloorOut * 0.999 + absOut * 0.001
            } else {
                noiseFloorOut = noiseFloorOut * 0.9999 + absOut * 0.0001
            }
        }

        val rmsIn = sqrt(sumSquareIn / length)
        val rmsOut = sqrt(sumSquareOut / length)
        val elapsedNs = System.nanoTime() - startTime

        // Compute Estimated SNR Gain (dB)
        // SNR_in = rmsIn / noiseFloorIn
        // SNR_out = rmsOut / noiseFloorOut
        // SNR_gain_dB = 20 * log10(SNR_out / SNR_in)
        val snrIn = max(0.1, rmsIn / max(1.0, noiseFloorIn))
        val snrOut = max(0.1, rmsOut / max(1.0, noiseFloorOut))
        val snrGainDb = max(0.0, min(36.0, 20.0 * log10(snrOut / snrIn)))

        return DspProcessResult(
            output = output,
            metrics = AudioMetrics(
                inputRms = rmsIn,
                outputRms = rmsOut,
                snrGainDb = snrGainDb,
                dspLatencyNs = elapsedNs,
                isFilterEnabled = true
            )
        )
    }
}

data class DspProcessResult(
    val output: ShortArray,
    val metrics: AudioMetrics
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DspProcessResult) return false
        if (!output.contentEquals(other.output)) return false
        return metrics == other.metrics
    }

    override fun hashCode(): Int {
        var result = output.contentHashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }
}

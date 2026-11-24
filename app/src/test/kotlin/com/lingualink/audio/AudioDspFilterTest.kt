package com.lingualink.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit tests verifying Module 1's real DSP filter mathematics:
 * 1. 80 Hz High-Pass attenuation on low-frequency rumble (40 Hz).
 * 2. 1000 Hz speech passband preservation.
 * 3. Adaptive noise gate attenuation on stationary low-level noise.
 * 4. Bypass toggle fidelity (exact sample passthrough).
 * 5. Analytical RMS and SNR gain calculation accuracy.
 */
class AudioDspFilterTest {

    private lateinit var dsp: AudioDspFilter
    private val sampleRate = 16000

    @Before
    fun setUp() {
        dsp = AudioDspFilter(sampleRate = sampleRate, cutoffHz = 80.0, isFilterEnabled = true)
    }

    @Test
    fun testHighPassFilterAttenuatesLowFrequencyRumble() {
        val count = sampleRate // 1 second of audio
        val buffer = ShortArray(count)
        val f40 = 40.0 // 40 Hz rumble, well below 80 Hz cutoff

        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val sample = (10000.0 * sin(2.0 * PI * f40 * t)).toInt()
            buffer[i] = sample.toShort()
        }

        val result = dsp.process(buffer, count)

        // A 2nd-order Butterworth filter at 1 octave below fc (-6dB to -12dB)
        // should significantly attenuate 40 Hz signal. Output RMS must be < 40% of Input RMS.
        assertTrue("Input RMS should be substantial", result.metrics.inputRms > 6000.0)
        assertTrue(
            "Output RMS (${result.metrics.outputRms}) must be less than 40% of Input RMS (${result.metrics.inputRms})",
            result.metrics.outputRms < (result.metrics.inputRms * 0.40)
        )
    }

    @Test
    fun testPassBandAllows1000HzSpeechTone() {
        val count = sampleRate
        val buffer = ShortArray(count)
        val f1000 = 1000.0 // 1 kHz speech vowel formant frequency

        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val sample = (10000.0 * sin(2.0 * PI * f1000 * t)).toInt()
            buffer[i] = sample.toShort()
        }

        val result = dsp.process(buffer, count)

        // In the passband (1000 Hz >> 80 Hz), amplitude is preserved
        val ratio = result.metrics.outputRms / result.metrics.inputRms
        assertTrue(
            "1000 Hz passband ratio should be near 1.0 (actual: $ratio)",
            ratio > 0.90
        )
    }

    @Test
    fun testNoiseGateAttenuatesSilenceAndStationaryNoise() {
        val count = sampleRate
        val noiseBuffer = ShortArray(count)

        // Generate low-amplitude white/ambient noise (amplitude ~60)
        for (i in 0 until count) {
            noiseBuffer[i] = ((Math.random() - 0.5) * 120.0).toInt().toShort()
        }

        val result = dsp.process(noiseBuffer, count)

        // Noise gate should detect background noise floor and attenuate
        assertTrue("Noise gate must reduce noise amplitude", result.metrics.outputRms < result.metrics.inputRms)
        assertTrue("SNR gain must be computed and positive", result.metrics.snrGainDb >= 0.0)
    }

    @Test
    fun testBypassSwitchMaintainsExactSamples() {
        dsp.isFilterEnabled = false

        val count = 512
        val input = ShortArray(count) { (it * 37 % 32000).toShort() }

        val result = dsp.process(input, count)

        // When bypassed:
        assertEquals("Bypassed filter must set isFilterEnabled to false", false, result.metrics.isFilterEnabled)
        assertEquals("Bypassed filter must report 0.0 dB SNR gain", 0.0, result.metrics.snrGainDb, 0.001)
        assertEquals("Input and Output RMS must match exactly", result.metrics.inputRms, result.metrics.outputRms, 0.001)

        for (i in 0 until count) {
            assertEquals("Sample at index $i must match input exactly", input[i], result.output[i])
        }
    }

    @Test
    fun testRmsMathematicalAccuracy() {
        val count = sampleRate
        val amplitude = 10000.0
        val buffer = ShortArray(count)

        // Pure 500 Hz sine wave
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            buffer[i] = (amplitude * sin(2.0 * PI * 500.0 * t)).toInt().toShort()
        }

        // Run bypassed so filter does not alter the amplitude
        dsp.isFilterEnabled = false
        val result = dsp.process(buffer, count)

        // Theoretical RMS of sine wave = A / sqrt(2) ≈ 7071.07
        val expectedRms = amplitude / sqrt(2.0)
        val tolerance = expectedRms * 0.02 // within 2%

        assertEquals(
            "Computed input RMS must match theoretical sine RMS",
            expectedRms,
            result.metrics.inputRms,
            tolerance
        )
    }
}

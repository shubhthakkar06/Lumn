package com.example.musicapp.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class CinematicDspTest {
    private fun sine(frames: Int, rate: Int, freq: Float, amp: Float, phaseR: Float = 0f) = FloatArray(frames * 2).also { b ->
        for (i in 0 until frames) {
            b[2 * i] = amp * sin(2 * PI * freq * i / rate).toFloat()
            b[2 * i + 1] = amp * sin(2 * PI * freq * i / rate + phaseR).toFloat()
        }
    }

    @Test
    fun studioModeIsBitTransparent() {
        val dsp = CinematicDsp(48000)
        dsp.setParams(SoundSettings.preset(SoundMode.STUDIO).effective(OutputKind.HEADPHONES))
        val input = sine(4800, 48000, 440f, 0.5f)
        val out = input.copyOf()
        dsp.process(out, 4800)
        assertArrayEquals(input, out, 0f)
    }

    @Test
    fun everyModeStaysBelowFullScaleOnLoudInput() {
        for (mode in SoundMode.entries) for (output in OutputKind.entries) {
            val dsp = CinematicDsp(44100)
            dsp.setParams(SoundSettings.preset(mode).copy(bassDb = 12f, loudnessDb = 8f).effective(output))
            // Full-scale bass with opposite phase on the right: worst case for width + bass boost.
            val buf = sine(44100, 44100, 60f, 0.99f, PI.toFloat())
            dsp.process(buf, 44100)
            assertTrue("$mode/$output produced NaN", buf.none { it.isNaN() })
            assertTrue("$mode/$output clipped: ${buf.maxOf { abs(it) }}", buf.all { abs(it) <= 1f })
        }
    }

    @Test
    fun cinematicAddsSpaceAfterTheSoundStops() {
        val dsp = CinematicDsp(48000)
        dsp.setParams(SoundSettings.preset(SoundMode.CONCERT).effective(OutputKind.HEADPHONES))
        val buf = FloatArray(48000 * 2)
        for (i in 0 until 2400) { buf[2 * i] = 0.5f * sin(i * 0.1f); buf[2 * i + 1] = buf[2 * i] }
        dsp.process(buf, 48000)
        // Reverb tail: energy remains 200-400 ms after a 50 ms burst ended.
        var tail = 0f
        for (i in 9600 until 19200) tail += abs(buf[2 * i])
        assertTrue("no reverb tail ($tail)", tail > 1f)
    }
}

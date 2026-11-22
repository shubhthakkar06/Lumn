package com.example.musicapp.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rivara's sound engine: a stereo DSP chain that runs on decoded audio before it reaches the
 * output, so it sounds the same on every phone, Bluetooth headset and computer (unlike
 * Android's optional system effects, which many devices do not implement).
 *
 * Chain, per stereo frame:
 *  1. Tone: low shelf (bass), peaking presence (vocals), high shelf (air / detail).
 *  2. Width: mid/side, widening only above ~180 Hz so bass stays centred and punchy.
 *  3. Surround: headphone crossfeed. A delayed, low-passed copy of each channel reaches the
 *     other ear, as it would from speakers in front of you, so sound leaves the head.
 *  4. Space: early reflections (distinct per ear) and a band-limited stereo hall reverb.
 *  5. Punch: stereo-linked glue compressor.
 *  6. Loudness makeup, then a peak limiter and soft clipper so boosts never clip.
 *
 * Audio is processed as interleaved float stereo in -1..1. Parameters glide over ~30 ms, so
 * moving a slider never clicks.
 */
class CinematicDsp(sampleRate: Int) {
    var sampleRate: Int = sampleRate
        private set

    @Volatile
    private var target: DspParams = DspParams.BYPASS
    private var current: DspParams = DspParams.BYPASS
    private var appliedVersion = -1
    @Volatile
    private var targetVersion = 0

    // Tone filters (one per channel).
    private val bassL = Biquad(); private val bassR = Biquad()
    private val presL = Biquad(); private val presR = Biquad()
    private val trebL = Biquad(); private val trebR = Biquad()
    // Side-channel high-pass for bass-safe widening.
    private val sideHp = Biquad()
    // Crossfeed: delay + low-pass of the opposite channel.
    private val xfDelayL = DelayLine(64); private val xfDelayR = DelayLine(64)
    private val xfLpL = OnePole(); private val xfLpR = OnePole()
    // Early reflections.
    private val erLine = DelayLine(8192)
    private val erTapsL = intArrayOf(0, 0, 0, 0, 0, 0)
    private val erTapsR = intArrayOf(0, 0, 0, 0, 0, 0)
    private val erGains = floatArrayOf(0.62f, 0.5f, 0.41f, 0.33f, 0.26f, 0.2f)
    private val erLpL = OnePole(); private val erLpR = OnePole()
    // Hall reverb (Freeverb-style), fed through a band-pass so it never gets muddy or hissy.
    private val revInHp = Biquad(); private val revInLp = Biquad()
    private val preDelay = DelayLine(4096)
    private var combsL = emptyArray<Comb>(); private var combsR = emptyArray<Comb>()
    private var allpassL = emptyArray<Allpass>(); private var allpassR = emptyArray<Allpass>()
    // Dynamics.
    private var envelope = 0f
    private var limiterGain = 1f

    // Smoothed scalar parameters.
    private var gWidth = 0f; private var gCross = 0f; private var gEr = 0f; private var gRev = 0f
    private var gPunch = 0f; private var gMakeup = 1f; private var gWet = 0f
    private var xfDelaySamples = 1

    init { configure(sampleRate) }

    fun configure(rate: Int) {
        sampleRate = rate
        val scale = rate / 44100f
        val tapsMsL = floatArrayOf(11.3f, 19.7f, 29.1f, 37.9f, 47.3f, 61.1f)
        val tapsMsR = floatArrayOf(13.1f, 23.3f, 31.7f, 41.9f, 53.9f, 67.3f)
        for (i in tapsMsL.indices) {
            erTapsL[i] = (tapsMsL[i] * rate / 1000f).toInt()
            erTapsR[i] = (tapsMsR[i] * rate / 1000f).toInt()
        }
        val combTunings = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491)
        val allpassTunings = intArrayOf(556, 441, 341)
        val spread = 23
        combsL = Array(combTunings.size) { Comb((combTunings[it] * scale).toInt()) }
        combsR = Array(combTunings.size) { Comb(((combTunings[it] + spread) * scale).toInt()) }
        allpassL = Array(allpassTunings.size) { Allpass((allpassTunings[it] * scale).toInt()) }
        allpassR = Array(allpassTunings.size) { Allpass(((allpassTunings[it] + spread) * scale).toInt()) }
        xfDelaySamples = max(1, (0.28f * rate / 1000f).toInt())
        sideHp.highPass(rate, 180f, 0.7f)
        revInHp.highPass(rate, 220f, 0.7f)
        revInLp.lowPass(rate, 6500f, 0.7f)
        xfLpL.lowPass(rate, 700f); xfLpR.lowPass(rate, 700f)
        erLpL.lowPass(rate, 5200f); erLpR.lowPass(rate, 4600f)
        appliedVersion = -1
        reset()
    }

    fun setParams(params: DspParams) {
        target = params
        targetVersion++
    }

    fun reset() {
        listOf(bassL, bassR, presL, presR, trebL, trebR, sideHp, revInHp, revInLp).forEach { it.clear() }
        xfDelayL.clear(); xfDelayR.clear(); erLine.clear(); preDelay.clear()
        xfLpL.clear(); xfLpR.clear(); erLpL.clear(); erLpR.clear()
        combsL.forEach { it.clear() }; combsR.forEach { it.clear() }
        allpassL.forEach { it.clear() }; allpassR.forEach { it.clear() }
        envelope = 0f
        limiterGain = 1f
    }

    private fun applyTone(p: DspParams) {
        val rate = sampleRate
        bassL.lowShelf(rate, 105f, p.bassDb); bassR.copyFrom(bassL)
        presL.peaking(rate, 3000f, 0.9f, p.presenceDb); presR.copyFrom(presL)
        trebL.highShelf(rate, 9000f, p.trebleDb); trebR.copyFrom(trebL)
        current = p
    }

    /** Processes [frames] interleaved stereo frames in place. */
    fun process(buffer: FloatArray, frames: Int) {
        val p = target
        if (appliedVersion != targetVersion) {
            appliedVersion = targetVersion
            applyTone(p)
        }
        val active = p.enabled
        // Targets for smoothed values; a disabled engine glides everything to neutral.
        val tWidth = if (active) p.width * 0.9f else 0f
        val tCross = if (active) p.crossfeed * 0.42f else 0f
        val tEr = if (active) p.space * 0.2f else 0f
        val tRev = if (active) p.space * 0.17f else 0f
        val tPunch = if (active) p.punch else 0f
        // Headroom for boosts, then makeup; the limiter catches what is left.
        val boost = if (active) max(0f, max(p.bassDb, max(p.trebleDb, p.presenceDb))) else 0f
        val tMakeup = if (active) dbToGain(p.loudnessDb - boost * 0.55f) else 1f
        val tWet = if (active && (p.space > 0f || p.crossfeed > 0f)) 1f else 0f
        val glide = 1f - exp(-1f / (0.03f * sampleRate))
        val roomFeedback = 0.78f + 0.15f * p.space
        val damp = 0.32f

        val attack = exp(-1f / (0.012f * sampleRate))
        val release = exp(-1f / (0.2f * sampleRate))
        val limRelease = exp(-1f / (0.08f * sampleRate))
        val thresholdDb = -20f
        val ratio = 1f + 2.5f * tPunch

        var i = 0
        for (f in 0 until frames) {
            gWidth += (tWidth - gWidth) * glide
            gCross += (tCross - gCross) * glide
            gEr += (tEr - gEr) * glide
            gRev += (tRev - gRev) * glide
            gPunch += (tPunch - gPunch) * glide
            gMakeup += (tMakeup - gMakeup) * glide
            gWet += (tWet - gWet) * glide

            var l = buffer[i]
            var r = buffer[i + 1]
            val dryL = l
            val dryR = r

            if (active || gMakeup != 1f) {
                // 1. Tone
                l = trebL.process(presL.process(bassL.process(l)))
                r = trebR.process(presR.process(bassR.process(r)))
            }

            // 2. Width (side above 180 Hz only)
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            val sideWide = side + sideHp.process(side) * gWidth
            l = mid + sideWide
            r = mid - sideWide

            // 3. Crossfeed for headphones
            val fromR = xfLpL.process(xfDelayL.readWrite(r, xfDelaySamples))
            val fromL = xfLpR.process(xfDelayR.readWrite(l, xfDelaySamples))
            val norm = 1f / (1f + gCross * 0.6f)
            l = (l + fromR * gCross) * norm
            r = (r + fromL * gCross) * norm

            // 4. Space: reflections + hall
            if (gWet > 0.0005f) {
                val mono = (l + r) * 0.5f
                erLine.write(mono)
                var erL = 0f
                var erR = 0f
                for (t in erTapsL.indices) {
                    erL += erLine.read(erTapsL[t]) * erGains[t]
                    erR += erLine.read(erTapsR[t]) * erGains[t]
                }
                erL = erLpL.process(erL)
                erR = erLpR.process(erR)

                val revIn = revInLp.process(revInHp.process(preDelay.readWrite(mono, (0.018f * sampleRate).toInt()))) * 0.5f
                var revL = 0f
                var revR = 0f
                for (c in combsL.indices) {
                    revL += combsL[c].process(revIn, roomFeedback, damp)
                    revR += combsR[c].process(revIn, roomFeedback, damp)
                }
                for (a in allpassL.indices) {
                    revL = allpassL[a].process(revL)
                    revR = allpassR[a].process(revR)
                }
                l += (erL * gEr + revL * gRev * 0.25f) * gWet
                r += (erR * gEr + revR * gRev * 0.25f) * gWet
            }

            // 5. Glue compression (stereo linked, RMS-ish)
            val level = max(abs(l), abs(r))
            envelope = if (level > envelope) attack * envelope + (1f - attack) * level else release * envelope + (1f - release) * level
            var gain = 1f
            if (gPunch > 0.001f) {
                val levelDb = gainToDb(envelope + 1e-9f)
                if (levelDb > thresholdDb) {
                    val reduced = thresholdDb + (levelDb - thresholdDb) / ratio
                    gain = dbToGain((reduced - levelDb) * gPunch)
                }
                // Gentle makeup for the compression itself.
                gain *= dbToGain(3f * gPunch)
            }

            l *= gain * gMakeup
            r *= gain * gMakeup

            // 6. Peak limiter at -1 dBFS plus soft clip for safety
            val peak = max(abs(l), abs(r))
            val ceiling = 0.89f
            val wanted = if (peak * limiterGain > ceiling) ceiling / peak else 1f
            limiterGain = if (wanted < limiterGain) wanted else limRelease * limiterGain + (1f - limRelease) * wanted
            l = softClip(l * limiterGain)
            r = softClip(r * limiterGain)

            if (!active && gWet < 0.0005f && abs(gMakeup - 1f) < 0.0005f && gWidth < 0.0005f && gCross < 0.0005f) {
                // Fully bypassed: hand back the untouched input.
                l = dryL
                r = dryR
            }
            buffer[i] = l
            buffer[i + 1] = r
            i += 2
        }
    }

    private fun softClip(x: Float): Float {
        // Transparent below 0.9, smoothly saturating towards 1.0 above it.
        val a = abs(x)
        if (a <= 0.9f) return x
        val over = (a - 0.9f) / 0.1f
        val shaped = 0.9f + 0.1f * (over / (1f + over))
        return if (x < 0) -shaped else shaped
    }

    // ------------------------------------------------------------------ building blocks

    private class Biquad {
        private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
        private var z1 = 0f; private var z2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun clear() { z1 = 0f; z2 = 0f }

        fun copyFrom(o: Biquad) { b0 = o.b0; b1 = o.b1; b2 = o.b2; a1 = o.a1; a2 = o.a2 }

        private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
            b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
            a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
        }

        // RBJ audio EQ cookbook.
        fun lowShelf(rate: Int, freq: Float, gainDb: Float) {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * freq / rate
            val cw = cos(w); val sw = sin(w)
            val alpha = sw / 2 * sqrt((a + 1 / a) * (1 / 0.8 - 1) + 2)
            val sa = 2 * sqrt(a) * alpha
            set(a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
                (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa)
        }

        fun highShelf(rate: Int, freq: Float, gainDb: Float) {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * freq / rate
            val cw = cos(w); val sw = sin(w)
            val alpha = sw / 2 * sqrt((a + 1 / a) * (1 / 0.8 - 1) + 2)
            val sa = 2 * sqrt(a) * alpha
            set(a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
                (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa)
        }

        fun peaking(rate: Int, freq: Float, q: Float, gainDb: Float) {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val cw = cos(w)
            set(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
        }

        fun highPass(rate: Int, freq: Float, q: Float) {
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val cw = cos(w)
            set((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
        }

        fun lowPass(rate: Int, freq: Float, q: Float) {
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val cw = cos(w)
            set((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
        }
    }

    private class OnePole {
        private var a = 0f
        private var z = 0f
        fun lowPass(rate: Int, freq: Float) { a = exp(-2.0 * PI * freq / rate).toFloat() }
        fun process(x: Float): Float { z = x * (1f - a) + z * a; return z }
        fun clear() { z = 0f }
    }

    private class DelayLine(size: Int) {
        private val data = FloatArray(Integer.highestOneBit(size - 1) shl 1)
        private val mask = data.size - 1
        private var pos = 0
        fun write(x: Float) { data[pos] = x; pos = (pos + 1) and mask }
        /** Sample written [delay] samples ago (1 = previous). */
        fun read(delay: Int): Float = data[(pos - delay) and mask]
        fun readWrite(x: Float, delay: Int): Float { val out = read(delay); write(x); return out }
        fun clear() { data.fill(0f); pos = 0 }
    }

    private class Comb(size: Int) {
        private val buf = FloatArray(max(1, size))
        private var idx = 0
        private var store = 0f
        fun process(x: Float, feedback: Float, damp: Float): Float {
            val out = buf[idx]
            store = out * (1f - damp) + store * damp
            buf[idx] = x + store * feedback
            idx = if (idx + 1 >= buf.size) 0 else idx + 1
            return out
        }
        fun clear() { buf.fill(0f); store = 0f; idx = 0 }
    }

    private class Allpass(size: Int) {
        private val buf = FloatArray(max(1, size))
        private var idx = 0
        fun process(x: Float): Float {
            val delayed = buf[idx]
            val out = -x + delayed
            buf[idx] = x + delayed * 0.5f
            idx = if (idx + 1 >= buf.size) 0 else idx + 1
            return out
        }
        fun clear() { buf.fill(0f); idx = 0 }
    }

    companion object {
        fun dbToGain(db: Float): Float = 10f.pow(db / 20f)
        fun gainToDb(g: Float): Float = 20f * kotlin.math.log10(g)
    }
}

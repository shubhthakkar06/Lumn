package com.example.musicapp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs [CinematicDsp] inside ExoPlayer's audio pipeline. Accepts mono or stereo 16-bit or
 * float PCM and always outputs stereo in the same encoding (mono is spread to both ears so
 * the 3D processing has two channels to work with). Other layouts pass through untouched.
 */
@UnstableApi
class CinematicAudioProcessor : BaseAudioProcessor() {
    private var dsp: CinematicDsp? = null
    private var inChannels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var scratch = FloatArray(0)

    @Volatile
    private var params: DspParams = DspParams.BYPASS

    fun setParams(p: DspParams) {
        params = p
        dsp?.setParams(p)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supportedEncoding || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inChannels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        val engine = dsp?.takeIf { it.sampleRate == inputAudioFormat.sampleRate }
            ?: CinematicDsp(inputAudioFormat.sampleRate)
        engine.setParams(params)
        dsp = engine
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val engine = dsp ?: return
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frames = inputBuffer.remaining() / (bytesPerSample * inChannels)
        if (frames == 0) return
        if (scratch.size < frames * 2) scratch = FloatArray(frames * 2)
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        var i = 0
        for (f in 0 until frames) {
            val l = read(src, bytesPerSample)
            val r = if (inChannels == 2) read(src, bytesPerSample) else l
            scratch[i] = l
            scratch[i + 1] = r
            i += 2
        }
        engine.process(scratch, frames)
        val out = replaceOutputBuffer(frames * 2 * bytesPerSample)
        for (s in 0 until frames * 2) {
            val v = scratch[s]
            if (bytesPerSample == 4) out.putFloat(v)
            else out.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        out.flip()
    }

    private fun read(buffer: ByteBuffer, bytes: Int): Float =
        if (bytes == 4) buffer.float else buffer.short / 32768f

    override fun onFlush() {
        dsp?.reset()
    }

    override fun onReset() {
        dsp = null
        scratch = FloatArray(0)
    }
}

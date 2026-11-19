package `in`.qaida.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import `in`.qaida.app.data.content.ContentPaths
import `in`.qaida.app.dsp.Framing
import `in`.qaida.app.dsp.Resampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes a bundled Opus clip to 16 kHz mono PCM for analysis, with the platform decoder (no library).
 * Opus always decodes at 48 kHz, so the result is resampled. Decoded clips are cached for the process.
 */
class ReferenceDecoder(private val context: Context) {
    private val cache = ConcurrentHashMap<String, ShortArray>()

    suspend fun decode(file: String): ShortArray = cache[file] ?: withContext(Dispatchers.IO) {
        val (pcm, rate, channels) = decodeRaw(ContentPaths.audio(file))
        val mono = if (channels == 1) pcm else ShortArray(pcm.size / channels) { i ->
            (0 until channels).sumOf { pcm[i * channels + it].toInt() }.div(channels).toShort()
        }
        Resampler.resample(mono, rate, Framing.SAMPLE_RATE).also { cache[file] = it }
    }

    private fun decodeRaw(assetPath: String): Triple<ShortArray, Int, Int> {
        val extractor = MediaExtractor()
        context.assets.openFd(assetPath).use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = ArrayList<Short>(48_000)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.asShortBuffer()
                        while (shorts.hasRemaining()) out += shorts.get()
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        return Triple(out.toShortArray(), rate, channels)
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}

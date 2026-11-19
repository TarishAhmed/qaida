package `in`.qaida.app.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit mono PCM WAV encode/decode for recordings and trimmed A/B clips. */
object Wav {
    fun encode(pcm: ShortArray, sampleRate: Int = Framing.SAMPLE_RATE): ByteArray {
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (s in pcm) b.putShort(s)
        return b.array()
    }

    /** Decodes a 16-bit mono PCM WAV; returns samples and sample rate. */
    fun decode(bytes: ByteArray): Pair<ShortArray, Int> {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "not a WAV file" }
        var pos = 12
        var rate = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = b.getInt(pos + 4)
            if (id == "fmt ") {
                require(b.getShort(pos + 8).toInt() == 1 && b.getShort(pos + 10).toInt() == 1 && b.getShort(pos + 22).toInt() == 16) {
                    "only 16-bit mono PCM is supported"
                }
                rate = b.getInt(pos + 12)
            } else if (id == "data") {
                val n = minOf(size, bytes.size - pos - 8) / 2
                return ShortArray(n) { b.getShort(pos + 8 + it * 2) } to rate
            }
            pos += 8 + size + (size and 1)
        }
        error("WAV has no data chunk")
    }
}

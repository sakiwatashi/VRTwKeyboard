package tw.pinnedbopomofo.quest.voice

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 測試用的合成聲音：每框 20 ms、16 kHz。 */
object Signals {
    private val frame = Endpointer.FRAME_SAMPLES

    fun quiet(ms: Int, amplitude: Int = 30, seed: Int = 1) = noise(ms, amplitude, seed)

    fun noise(ms: Int, amplitude: Int, seed: Int = 7): List<ShortArray> {
        val random = Random(seed)
        return List(ms / Endpointer.FRAME_MS) {
            ShortArray(frame) { random.nextInt(-amplitude, amplitude + 1).toShort() }
        }
    }

    /**
     * 比較像真人說話：音量有起伏（音節大聲、音節之間小聲），所以平均音量比峰值低不少。
     * 固定音量的正弦波會讓每一框都過門檻，測不出真機上「只有 11% 的框過門檻」的情況。
     */
    fun speech(ms: Int, peakAmplitude: Int, noiseAmplitude: Int = 0): List<ShortArray> {
        val random = Random(23)
        var t = 0
        var frame = 0
        return List(ms / Endpointer.FRAME_MS) {
            // 100 ms 大聲、60 ms 小聲，小聲的部分約低 15 dB
            val loud = frame++ % 8 < 5
            val amplitude = if (loud) peakAmplitude else peakAmplitude / 6
            ShortArray(Endpointer.FRAME_SAMPLES) {
                val tone = amplitude * sin(2 * PI * 300 * t++ / Endpointer.SAMPLE_RATE)
                val hiss = if (noiseAmplitude > 0) random.nextInt(-noiseAmplitude, noiseAmplitude + 1) else 0
                (tone + hiss).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }

    /** 模擬說話：300 Hz 正弦波，可疊在噪音上。 */
    fun voice(ms: Int, amplitude: Int = 8000, noiseAmplitude: Int = 0): List<ShortArray> {
        val random = Random(11)
        var t = 0
        return List(ms / Endpointer.FRAME_MS) {
            ShortArray(frame) {
                val tone = amplitude * sin(2 * PI * 300 * t++ / Endpointer.SAMPLE_RATE)
                val hiss = if (noiseAmplitude > 0) random.nextInt(-noiseAmplitude, noiseAmplitude + 1) else 0
                (tone + hiss).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }
}

package tw.pinnedbopomofo.quest.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 麥克風收音測試：同一段時間分別用不同的錄音來源錄，量出音量，用來診斷「收不到聲音」。
 *
 * 2026-09-16 在頭盔上量到說話峰值只有 −36 dB（更早的成功案例約 −20 dB），懷疑是
 * `VOICE_RECOGNITION` 這個來源關掉了自動增益。只記統計數字，不留聲音。
 */
object MicProbe {

    data class Result(val source: String, val peakDb: Double, val meanDb: Double, val frames: Int, val error: String?) {
        override fun toString() = if (error != null) {
            "$source 失敗：$error"
        } else {
            String.format(java.util.Locale.US, "%s peakDb=%.1f meanDb=%.1f frames=%d", source, peakDb, meanDb, frames)
        }
    }

    val SOURCES = listOf(
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "MIC" to MediaRecorder.AudioSource.MIC,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "CAMCORDER" to MediaRecorder.AudioSource.CAMCORDER,
    )

    /** 在背景執行緒呼叫：每個來源錄 [millis] 毫秒。 */
    @SuppressLint("MissingPermission")
    fun measure(source: Int, name: String, millis: Int): Result {
        val minBuffer = AudioRecord.getMinBufferSize(
            Endpointer.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return Result(name, 0.0, 0.0, 0, "不支援 16 kHz")
        val record = try {
            AudioRecord(
                source, Endpointer.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, Endpointer.FRAME_SAMPLES * 20),
            )
        } catch (e: Exception) {
            return Result(name, 0.0, 0.0, 0, e.message ?: "無法開啟")
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return Result(name, 0.0, 0.0, 0, "初始化失敗")
        }
        return try {
            record.startRecording()
            val buffer = ShortArray(Endpointer.FRAME_SAMPLES)
            val frames = millis / Endpointer.FRAME_MS
            var peak = 0.0
            var sum = 0.0
            var read = 0
            repeat(frames) {
                var filled = 0
                while (filled < buffer.size) {
                    val count = record.read(buffer, filled, buffer.size - filled)
                    if (count <= 0) break
                    filled += count
                }
                if (filled == buffer.size) {
                    val energy = Endpointer.meanSquare(buffer)
                    if (energy > peak) peak = energy
                    sum += energy
                    read += 1
                }
            }
            Result(name, db(peak), db(if (read > 0) sum / read else 0.0), read, null)
        } catch (e: Exception) {
            Result(name, 0.0, 0.0, 0, e.message ?: "錄音失敗")
        } finally {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // 已經停了
            }
            record.release()
        }
    }

    private fun db(energy: Double) = if (energy <= 0.0) -99.0 else 10 * log10(energy)
}

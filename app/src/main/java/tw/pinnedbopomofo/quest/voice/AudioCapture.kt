package tw.pinnedbopomofo.quest.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * 麥克風錄音：16 kHz 單聲道 16-bit，每 20 ms 一框交給 [onFrame]（在錄音執行緒上呼叫）。
 * 聲音只在記憶體裡交給 VoiceSession，不寫檔。
 */
class AudioCapture(
    private val context: Context,
    private val onFrame: (ShortArray) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile
    private var running = false

    /** 呼叫前要先確認有 RECORD_AUDIO 權限。成功回傳 null，失敗回傳給使用者看的原因。 */
    @SuppressLint("MissingPermission")
    fun start(): String? {
        val minBuffer = AudioRecord.getMinBufferSize(Endpointer.SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return "這台裝置不支援 16 kHz 錄音"
        val record = try {
            AudioRecord(
                // 2026-09-18 頭盔實測（App 內建的麥克風測試，四種來源各錄 3 秒、同一段說話）：
                //     VOICE_RECOGNITION   peakDb=-35.8  meanDb=-53.2   <- 原本用這個
                //     MIC                 peakDb=-65.8  meanDb=-81.8
                //     VOICE_COMMUNICATION peakDb=-70.9  meanDb=-85.2
                //     CAMCORDER           peakDb=-31.4  meanDb=-45.7   <- 現在用這個
                // CAMCORDER 峰值高 4.4 dB、平均高 7.5 dB。MIC 與 VOICE_COMMUNICATION
                // 在這顆頭盔上低了 30 dB 以上，等於是聾的，不要用。
                // 這關係到能不能聽到：同一次實測裡 111 框只有 14 框過門檻，訊號多幾 dB 差很多。
                MediaRecorder.AudioSource.CAMCORDER,
                Endpointer.SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer, Endpointer.FRAME_SAMPLES * BUFFER_FRAMES * 2),
            )
        } catch (e: Exception) {
            return "無法開啟麥克風：${e.message}"
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return "無法開啟麥克風（初始化失敗）"
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            return "無法開始錄音：${e.message}"
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return "系統沒有讓麥克風開始錄音"
        }

        logRecordingState(record)

        running = true
        Thread({
            val frame = Endpointer.FRAME_SAMPLES
            val buffer = ShortArray(frame)
            try {
                while (running) {
                    var filled = 0
                    while (running && filled < frame) {
                        val read = record.read(buffer, filled, frame - filled)
                        if (read < 0) {
                            if (running) onError("錄音中斷（代碼 $read）")
                            running = false
                            break
                        }
                        filled += read
                    }
                    if (filled == frame) onFrame(buffer.copyOf())
                }
            } finally {
                try {
                    record.stop()
                } catch (e: IllegalStateException) {
                    // 已經停了
                }
                record.release()
            }
        }, "voice-capture").start()
        return null
    }

    fun stop() {
        running = false
    }

    /**
     * 問系統：我們的錄音有沒有被靜音（Android 14 的 isClientSilenced），還有誰在錄音。
     *
     * 2026-09-16 診斷「時好時壞」：麥克風測試量到 −15 dB 很正常，但實際語音輸入時
     * 有幾次整段只有 −63 dB。這行記錄用來確認那種時候系統是不是把我們靜音了。
     */
    private fun logRecordingState(record: AudioRecord) {
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        val configs = try {
            manager.activeRecordingConfigurations
        } catch (e: Exception) {
            Log.d(TAG, "mic state 查不到：${e.message}")
            return
        }
        val ours = configs.filter { it.clientAudioSessionId == record.audioSessionId }
        val others = configs.size - ours.size
        val silenced = ours.joinToString(",") { it.isClientSilenced.toString() }.ifEmpty { "不在清單裡" }
        Log.d(TAG, "mic state silenced=$silenced session=${record.audioSessionId} otherRecorders=$others")
    }

    private companion object {
        const val TAG = "ZhuyinIme"
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_FRAMES = 10
    }
}

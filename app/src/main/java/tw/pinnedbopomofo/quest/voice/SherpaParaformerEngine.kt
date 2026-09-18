package tw.pinnedbopomofo.quest.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File

/**
 * sherpa-onnx ＋ 串流 Paraformer 中英雙語（int8）。模型檔放在 App 的外部資料夾，見 [VoiceModels]。
 *
 * [load] 要在背景執行緒呼叫：模型約 226 MiB，載入要好幾秒。
 * 之後的 [startUtterance] 與 [SpeechEngine.Utterance] 也都在辨識執行緒上，VoiceSession 已經保證同一時間只有一條。
 */
class SherpaParaformerEngine private constructor(private val recognizer: OnlineRecognizer) : SpeechEngine {

    override fun startUtterance(): SpeechEngine.Utterance = ParaformerUtterance(recognizer)

    /** 釋放模型；釋放後不能再用。 */
    fun release() = recognizer.release()

    private class ParaformerUtterance(private val recognizer: OnlineRecognizer) : SpeechEngine.Utterance {
        private val stream: OnlineStream = recognizer.createStream()
        private var closed = false

        override fun accept(samples: FloatArray, sampleRate: Int): String {
            if (closed) return ""
            stream.acceptWaveform(samples, sampleRate)
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            return recognizer.getResult(stream).text
        }

        override fun finish(): String {
            if (closed) return ""
            // 串流模型分塊解碼，最後一塊要有足夠的音訊才會吐出來。
            // 不補這段靜音，句尾會被吃掉：2026-09-16 頭盔實測 today 只出到 to、MINECRAFT 出成 MYCRA。
            stream.acceptWaveform(FloatArray(TAIL_SAMPLES), Endpointer.SAMPLE_RATE)
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val text = recognizer.getResult(stream).text
            close()
            return text
        }

        override fun cancel() = close()

        private fun close() {
            if (closed) return
            closed = true
            stream.release()
        }
    }

    companion object {
        /** 頭盔是 8 核心（2 大 6 小）；辨識用 2 條執行緒，留 CPU 給鍵盤畫面。之後量測再調。 */
        private const val THREADS = 2

        /**
         * 結束前補進去的靜音長度，用來沖出最後一塊解碼結果。
         *
         * 原本是 0.5 秒（2026-09-16 為了修「today 只出到 to」加的）。2026-09-18 使用者回報
         * 句尾的最後一個字又被吃掉，而這支檔案從那次之後沒有再改過，所以是 0.5 秒本身不夠：
         * 串流 Paraformer 是固定區塊解碼，sherpa-onnx 的區塊加上前瞻大約要 0.6 秒以上，
         * 補 0.5 秒剛好差一點，最後一塊湊不滿就不會吐出來——所以會「大部分時候還行、
         * 偶爾吃掉最後一個字」。改成 1.0 秒留出餘裕。
         *
         * 2026-09-18 實測：0.5 -> 1.0 秒之後，講慢的不再掉字，但講快的仍有機率掉。
         * 講快時同一塊裡塞進更多音節，要更多解碼步驟才吐得完，所以再加到 2.0 秒。
         *
         * 代價實測過，很小：log 裡 voice recognizing 到 voice done 只差 128～138 毫秒，
         * 那就是補靜音加解碼的全部時間。補的是靜音，不影響辨識內容。
         */
        private const val TAIL_SAMPLES = Endpointer.SAMPLE_RATE * 2

        fun load(modelDir: File): SherpaParaformerEngine {
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = Endpointer.SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    paraformer = OnlineParaformerModelConfig(
                        encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = THREADS,
                    modelType = "paraformer",
                ),
            )
            // assetManager 傳 null 代表用檔案路徑載入
            return SherpaParaformerEngine(OnlineRecognizer(assetManager = null, config = config))
        }
    }
}

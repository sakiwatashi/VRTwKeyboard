package tw.pinnedbopomofo.quest.voice

import kotlin.math.log10

/**
 * 只看音量判斷「開始說話」與「說完了」，不是辨識模型。
 *
 * - 按下麥克風後先聽 [calibrationMs] 當作環境噪音底（頭盔風扇、房間底噪），這段不判斷。
 * - 音量超過噪音底的 [ratio] 倍（且不低於 [minEnergy]），連續 [startMs] 才算開始說話。
 *   [minEnergy] 原本是 1e-5（−50 dBFS），但 2026-09-16 頭盔實測：安靜時噪音底 −76 dB、
 *   正常說話峰值只有 −37 dB，門檻被這個下限壓在 −50，477 框只有 53 框過門檻、拖到 5.5 秒才觸發。
 *   改成 1e-6（−60 dBFS），讓噪音底乘以 [ratio] 來決定門檻。
 * - 說話後安靜 [endSilenceMs] 就算說完；講太久（[maxSpeechMs]）或一直沒開口（[waitMs]）也結束。
 * - 沒在說話時，噪音底跟著環境慢慢調整。
 *
 * 音框是 16 kHz 單聲道 16-bit，每框 [FRAME_MS]。
 */
class Endpointer(
    private val calibrationMs: Int = 200,
    private val startMs: Int = 120,
    private val endSilenceMs: Int = 900,
    private val maxSpeechMs: Int = 30_000,
    private val waitMs: Int = 6_000,
    private val ratio: Double = 4.0,
    private val minEnergy: Double = 1e-6,
    private val blankWaitMs: Int = 1_000,
) {
    enum class Event { NONE, STARTED, ENDED, NO_SPEECH, TOO_LONG }

    var speaking = false
        private set

    /** 最近一框的音量，0（-60 dBFS 以下）到 1（0 dBFS），給畫面上的音量條用。 */
    var level = 0f
        private set

    private var elapsedMs = 0

    /** 第一個「真的有訊號」的框發生在第幾毫秒；還沒等到是 -1。 */
    private var calibrationBeganMs = -1

    /** 校正結束在第幾毫秒；還沒結束是 -1。等待上限是從這裡開始算，不是從按下去算。 */
    private var calibrationEndMs = -1
    private var calibrationSum = 0.0
    private var calibrationMin = 0.0
    private var calibrationFrames = 0
    private var noiseFloor = 0.0
    private var voicedRunMs = 0
    private var silenceRunMs = 0
    private var speechMs = 0

    // 診斷用（只有統計數字，沒有聲音內容）
    private var peakEnergy = 0.0
    /** 校正結束當下的噪音底：診斷「按下就講話」的關鍵值，之後噪音底會再調整。 */
    private var calibrationFloor = -1.0

    /**
     * 校正那 200 ms 裡有沒有人在說話。有的話量到的噪音底不能採信，
     * 給畫面用來提示「按下後請等半秒再開口」。
     */
    var calibrationPolluted = false
        private set
    private var voicedFrames = 0
    private var totalFrames = 0
    /** 從按下麥克風到判定開口花了幾毫秒；沒開口是 -1。 */
    var startedAtMs = -1
        private set

    /** 一次只聽一段：說完、講太久或一直沒開口之後就不再判斷。 */
    private var done = false

    fun accept(frame: ShortArray): Event {
        if (done) return Event.NONE
        val energy = meanSquare(frame)
        level = levelOf(energy)
        elapsedMs += FRAME_MS
        totalFrames += 1
        if (energy > peakEnergy) peakEnergy = energy

        if (calibrationEndMs < 0) {
            // AudioRecord 剛啟動時會先吐幾框全 0 的空白，那不是環境噪音。
            // 2026-09-18 頭盔實測：校正量到 calDb=-115.4，而真正的環境噪音是 -76.6，
            // 等於每次都拿「麥克風還沒開始送資料」當基準，校正結果從來沒有意義。
            if (calibrationBeganMs < 0) {
                when {
                    energy > SILENT_FLOOR -> calibrationBeganMs = elapsedMs
                    // 等太久還是全靜音：可能真的被系統擋下。照樣往下走，
                    // 讓「麥克風收到的全是靜音」那個訊息有機會出現，不要無聲卡住。
                    elapsedMs >= blankWaitMs -> calibrationBeganMs = elapsedMs
                    else -> return Event.NONE
                }
            }
            calibrationSum += energy
            calibrationMin = if (calibrationFrames == 0) energy else minOf(calibrationMin, energy)
            calibrationFrames += 1
            noiseFloor = calibrationMin
            calibrationFloor = calibrationMin
            if (elapsedMs - calibrationBeganMs >= calibrationMs - FRAME_MS) {
                calibrationEndMs = elapsedMs
                finishCalibration()
            }
            return Event.NONE
        }

        val voiced = energy > threshold
        if (voiced) voicedFrames += 1
        if (!speaking) {
            if (voiced) {
                voicedRunMs += FRAME_MS
            } else {
                voicedRunMs = 0
                noiseFloor = noiseFloor * (1 - ADAPT) + energy * ADAPT
            }
            if (voicedRunMs >= startMs) {
                speaking = true
                startedAtMs = elapsedMs
                speechMs = voicedRunMs
                silenceRunMs = 0
                return Event.STARTED
            }
            if (elapsedMs >= calibrationEndMs + waitMs) {
                done = true
                return Event.NO_SPEECH
            }
            return Event.NONE
        }

        speechMs += FRAME_MS
        silenceRunMs = if (voiced) 0 else silenceRunMs + FRAME_MS
        if (silenceRunMs >= endSilenceMs) {
            speaking = false
            done = true
            return Event.ENDED
        }
        if (speechMs >= maxSpeechMs) {
            speaking = false
            done = true
            return Event.TOO_LONG
        }
        return Event.NONE
    }

    /**
     * 校正結束時決定要不要採信這段量到的噪音底。
     *
     * 分辨「環境噪音」與「使用者按下就開口」**不能看絕對音量**：頭盔實測過環境噪音 −31 dB、
     * 說話峰值只有 −37 dB，噪音比說話還大聲，任何絕對門檻都會把安靜的說話跟吵雜的房間搞混。
     *
     * 能分辨的是**調變**——說話有音節起伏（大聲小聲交替），風扇、冷氣這類噪音是平的。
     * 所以看校正窗內「平均 / 最小」的比值：比值大就代表這段裡面有人在講話。
     *
     * 被污染時不採信量到的值，改用「最小值再往下 9 dB」當底。因為那個最小值是音節之間的
     * 空隙，本身已經含有說話聲，直接拿來當底的話門檻仍然壓在說話音量附近，還是聽不到。
     */
    private fun finishCalibration() {
        val mean = if (calibrationFrames > 0) calibrationSum / calibrationFrames else 0.0
        calibrationFloor = calibrationMin
        calibrationPolluted = calibrationMin > 0.0 && mean / calibrationMin > POLLUTION_RATIO
        noiseFloor = if (calibrationPolluted) calibrationMin / POLLUTION_MARGIN else calibrationMin
    }

    /** 目前的觸發門檻（能量）。 */
    private val threshold get() = maxOf(noiseFloor * ratio, minEnergy)

    /**
     * 診斷字串：噪音底、門檻、最大音量（都是 dBFS）、有聲框比例、開口時間。
     * **不含聲音內容**，只有統計數字。
     *
     * 判讀：**calDb**（校正當下的噪音底）若明顯高於安靜環境（例如 -15 dB 而不是 -60 dB），
     * 代表校正那 200 ms 把說話聲當成環境噪音了，門檻被拉高、整段都很難觸發。
     * noiseDb 是結束當下的值，校正後還會調整，不能拿來判斷這件事。
     */
    fun summary(): String = String.format(
        java.util.Locale.US,
        "calDb=%.1f noiseDb=%.1f thrDb=%.1f peakDb=%.1f voiced=%d/%d startAtMs=%d polluted=%s",
        db(calibrationFloor), db(noiseFloor), db(threshold), db(peakEnergy), voicedFrames, totalFrames, startedAtMs,
        calibrationPolluted,
    )

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000

        /** 沒在說話時噪音底往目前音量靠的比例（每框）。 */
        private const val ADAPT = 0.05

        /**
         * 低於這個能量（約 −90 dBFS）視為「麥克風還沒開始送資料」的空白框，不是環境噪音。
         * 實測的參考點：空白框 −115 dB、真正的安靜環境 −76 dB，這個值落在兩者中間。
         */
        private const val SILENT_FLOOR = 1e-9

        /** 校正窗內「平均 / 最小」超過這個比值，就判定那段有人在說話。 */
        private const val POLLUTION_RATIO = 3.0

        /** 校正被污染時，把噪音底壓到最小值的幾分之一（8 倍約 9 dB）。 */
        private const val POLLUTION_MARGIN = 8.0

        fun meanSquare(frame: ShortArray): Double {
            if (frame.isEmpty()) return 0.0
            var sum = 0.0
            for (sample in frame) {
                val value = sample / 32768.0
                sum += value * value
            }
            return sum / frame.size
        }

        internal fun db(energy: Double): Double = if (energy <= 0.0) -99.0 else 10 * log10(energy)

        internal fun levelOf(energy: Double): Float {
            if (energy <= 0.0) return 0f
            val dbfs = 10 * log10(energy)
            return ((dbfs + 60) / 60).toFloat().coerceIn(0f, 1f)
        }
    }
}

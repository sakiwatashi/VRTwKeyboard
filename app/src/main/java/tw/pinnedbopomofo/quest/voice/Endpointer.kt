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
) {
    enum class Event { NONE, STARTED, ENDED, NO_SPEECH, TOO_LONG }

    var speaking = false
        private set

    /** 最近一框的音量，0（-60 dBFS 以下）到 1（0 dBFS），給畫面上的音量條用。 */
    var level = 0f
        private set

    private var elapsedMs = 0
    private var calibrationSum = 0.0
    private var calibrationFrames = 0
    private var noiseFloor = 0.0
    private var voicedRunMs = 0
    private var silenceRunMs = 0
    private var speechMs = 0

    // 診斷用（只有統計數字，沒有聲音內容）
    private var peakEnergy = 0.0
    /** 校正結束當下的噪音底：診斷「按下就講話」的關鍵值，之後噪音底會再調整。 */
    private var calibrationFloor = -1.0
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

        if (elapsedMs <= calibrationMs) {
            calibrationSum += energy
            calibrationFrames += 1
            noiseFloor = calibrationSum / calibrationFrames
            calibrationFloor = noiseFloor
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
            if (elapsedMs >= calibrationMs + waitMs) {
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
        "calDb=%.1f noiseDb=%.1f thrDb=%.1f peakDb=%.1f voiced=%d/%d startAtMs=%d",
        db(calibrationFloor), db(noiseFloor), db(threshold), db(peakEnergy), voicedFrames, totalFrames, startedAtMs,
    )

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000

        /** 沒在說話時噪音底往目前音量靠的比例（每框）。 */
        private const val ADAPT = 0.05

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

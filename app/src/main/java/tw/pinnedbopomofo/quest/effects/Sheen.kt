package tw.pinnedbopomofo.quest.effects

/**
 * 流光的時間軸：一道斜光帶掃過鍵盤，掃完停一段時間再掃。
 *
 * 為什麼要停：打字的時候一直有東西在動很吵。掃 [sweepMs]、停 [pauseMs]，
 * 看起來像偶爾反了一下光，不像跑馬燈。
 *
 * 這裡只算「現在光帶該在哪」，不碰畫布，才測得動。
 */
class Sheen(
    private val sweepMs: Long = 900,
    private val pauseMs: Long = 5_000,
) {
    /** 光帶超出畫面兩側的距離，用寬度的比例表示：進場與出場都要在畫面外。 */
    val overshoot = 0.35f

    private val cycleMs get() = sweepMs + pauseMs

    /**
     * [elapsedMs] 是從開始算起的毫秒。回傳光帶中心的位置，
     * `0` 是最左（畫面外）、`1` 是最右（畫面外）；停頓中回傳 `null`，呼叫端就不用畫。
     */
    fun positionAt(elapsedMs: Long): Float? {
        if (elapsedMs < 0) return null
        val phase = elapsedMs % cycleMs
        if (phase >= sweepMs) return null
        return phase.toFloat() / sweepMs
    }

    /** 光帶中心在畫布上的 x，含進出場的超出量。 */
    fun centerX(position: Float, width: Float): Float {
        val from = -overshoot * width
        val to = width * (1 + overshoot)
        return from + (to - from) * position
    }

    /** 一個週期要畫幾幀（給診斷用，不是給動畫用）。 */
    fun framesPerCycle(fps: Float) = (sweepMs * fps / 1000f)
}

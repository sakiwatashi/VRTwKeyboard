package tw.pinnedbopomofo.quest.effects

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 把 Choreographer 每一幀的時間戳算成「這塊面板實際上跑得動幾 fps」。
 *
 * 為什麼要量這個：輸入法面板在 Horizon OS 上是合成器裡的一層，不保證跟著螢幕的
 * 72／90 Hz 重畫。如果實際只有 20 fps，粒子、拖尾那類連續特效就不用做了，
 * 只能做「按一下演一次」的短動畫。猜不出來，只能在頭盔上跑一次。
 *
 * [targetHz] 是螢幕回報的更新率，用來判斷哪些間隔算掉幀。
 */
class FrameStats(private val targetHz: Float) {

    private val gapsMs = ArrayList<Double>()
    private var firstNanos = 0L
    private var lastNanos = 0L
    private var frames = 0

    fun add(frameTimeNanos: Long) {
        if (frames == 0) {
            firstNanos = frameTimeNanos
        } else {
            gapsMs += (frameTimeNanos - lastNanos) / 1_000_000.0
        }
        lastNanos = frameTimeNanos
        frames += 1
    }

    val frameCount get() = frames

    val spanMs get() = if (frames < 2) 0.0 else (lastNanos - firstNanos) / 1_000_000.0

    /** 實測更新率：量到的幀數除以量測時間。 */
    val fps get() = if (spanMs <= 0.0) 0.0 else (frames - 1) * 1000.0 / spanMs

    /** 掉了幾幀：間隔明顯超過一幀的，換算成漏掉幾次重畫。 */
    val dropped get() = dropped(gapsMs, targetHz)

    /** 幀間隔的第 [p] 百分位（毫秒）。p95 比平均值誠實：卡頓都藏在尾巴。 */
    fun percentileMs(p: Double) = percentile(gapsMs, p)

    /** 診斷字串，只有數字。 */
    fun summary(): String = String.format(
        Locale.US,
        "frames=%d span=%.0fms fps=%.1f target=%.1f p50=%.1fms p95=%.1fms dropped=%d",
        frames, spanMs, fps, targetHz, percentileMs(50.0), percentileMs(95.0), dropped,
    )

    companion object {
        /** 間隔超過理想值這個倍數才算掉幀，免得把正常的抖動算進去。 */
        const val DROP_FACTOR = 1.5

        fun idealMs(targetHz: Float) = if (targetHz <= 0f) 0.0 else 1000.0 / targetHz

        /** 線性內插的百分位。輸入不必先排序。 */
        fun percentile(values: List<Double>, p: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            if (sorted.size == 1) return sorted[0]
            val rank = (p.coerceIn(0.0, 100.0) / 100.0) * (sorted.size - 1)
            val low = floor(rank).toInt()
            val high = (low + 1).coerceAtMost(sorted.size - 1)
            return sorted[low] + (sorted[high] - sorted[low]) * (rank - low)
        }

        /**
         * 一個 27.8 ms 的間隔在 72 Hz 上是「兩幀的時間只畫了一幀」，算漏掉 1 幀。
         * 剛好一幀多一點點的不算，那是正常抖動。
         */
        fun dropped(gapsMs: List<Double>, targetHz: Float): Int {
            val ideal = idealMs(targetHz)
            if (ideal <= 0.0) return 0
            var missed = 0
            for (gap in gapsMs) {
                if (gap > ideal * DROP_FACTOR) missed += (gap / ideal).roundToInt() - 1
            }
            return missed
        }
    }
}

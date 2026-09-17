package tw.pinnedbopomofo.quest.effects

import kotlin.math.sin

/**
 * 煙霧繚繞：幾團很淡的霧橫著飄過鍵盤，飄出去就從另一邊回來。
 *
 * 座標都用 0～1 的比例，跟實際像素無關——面板尺寸會變，用比例就不必重算。
 * 一樣是純運算不碰畫布：會出錯的是「有沒有飄出去回不來」「會不會全部擠在一起」，
 * 這些測得出來。
 *
 * 霧刻意畫得大而淡：VR 裡細密的東西會閃，大塊的柔邊不會。
 */
class Mist(private val count: Int = 7, seed: Int = 11) {

    class Wisp(
        var x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val speed: Float,
        val alpha: Float,
        val phase: Float,
    ) {
        /** 上下輕微起伏，不然看起來像跑馬燈。 */
        fun yAt(elapsed: Float) = y + sin(elapsed * 0.6f + phase) * 0.035f
    }

    private val wisps = ArrayList<Wisp>(count)
    private var elapsed = 0f

    init {
        // 不用亂數：固定的配置每次啟用都一樣，也才測得準
        for (index in 0 until count) {
            // 用黃金比例間隔把每一團的參數散開。
            // 原本寫成 (index + seed * 0.37f) % 1f，但 index 是整數，
            // 取完餘數每一團的小數部分都一樣，七團的大小速度濃度完全相同——
            // 看起來會像一整塊板子在平移（測試抓到的）。
            val t = (index * GOLDEN + seed * 0.37f) % 1f
            val slot = index.toFloat() / count
            wisps += Wisp(
                // 起點錯開，不會一開始全部擠在左邊
                x = slot - MARGIN,
                y = 0.18f + t * 0.64f,
                width = 0.24f + t * 0.26f,
                height = 0.16f + (1f - t) * 0.20f,
                speed = SPEED_MIN + t * (SPEED_MAX - SPEED_MIN),
                alpha = ALPHA_MIN + (1f - t) * (ALPHA_MAX - ALPHA_MIN),
                phase = index * 1.31f,
            )
        }
    }

    val items: List<Wisp> get() = wisps
    val seconds get() = elapsed

    fun update(dt: Float) {
        elapsed += dt
        for (wisp in wisps) {
            wisp.x += wisp.speed * dt
            // 整團都飄出右邊之後才從左邊回來，不會憑空消失
            if (wisp.x - wisp.width / 2 > 1f + MARGIN) {
                wisp.x = -MARGIN - wisp.width / 2
            }
        }
    }

    companion object {
        /** 進出場都要在畫面外，邊緣才不會看到霧憑空出現。 */
        const val MARGIN = 0.3f
        /**
         * 每秒橫越畫面的比例。
         *
         * 原本是 0.012～0.035，也就是**橫越整個畫面要 50 到 130 秒**——
         * 2026-09-17 實機回報「煙霧根本沒感覺」，因為它確實幾乎沒在動。
         *
         * 要走的距離是 `1 + MARGIN * 2`（兩端都要在畫面外進出），不是 1——
         * 我第一次改就是漏算這個，改完還是 37 秒。現在是 14～27 秒橫越：
         * 還是很慢、像霧不像雲，但看得出來在飄。
         */
        const val SPEED_MIN = 0.060f
        const val SPEED_MAX = 0.115f
        /** 濃度。太濃會把鍵帽上的字蓋掉，但原本的 0.05 在淺色玉板上等於沒有。 */
        const val ALPHA_MIN = 0.10f
        const val ALPHA_MAX = 0.24f
        /** 黃金比例的小數部分：拿來當間隔可以把值鋪得最均勻。 */
        private const val GOLDEN = 0.6180339f
    }
}

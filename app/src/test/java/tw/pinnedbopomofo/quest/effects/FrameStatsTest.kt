package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {

    /** 依照每幀間隔（毫秒）餵進去，回傳統計。 */
    private fun feed(targetHz: Float, gapsMs: List<Double>): FrameStats {
        val stats = FrameStats(targetHz)
        var nanos = 1_000_000_000L
        stats.add(nanos)
        for (gap in gapsMs) {
            nanos += (gap * 1_000_000).toLong()
            stats.add(nanos)
        }
        return stats
    }

    @Test
    fun `完美的 72 Hz：fps 就是 72、沒有掉幀`() {
        val ideal = 1000.0 / 72
        val stats = feed(72f, List(144) { ideal })
        assertEquals(72.0, stats.fps, 0.1)
        assertEquals(0, stats.dropped)
        assertEquals(ideal, stats.percentileMs(50.0), 0.01)
        assertEquals(145, stats.frameCount)
    }

    @Test
    fun `每十幀漏一次重畫：掉幀數要算出來，不是只算「有幾個長間隔」`() {
        val ideal = 1000.0 / 72
        // 每 10 幀有一次間隔是三倍長 = 那次漏掉 2 幀
        val gaps = (1..60).map { if (it % 10 == 0) ideal * 3 else ideal }
        val stats = feed(72f, gaps)
        assertEquals(6 * 2, stats.dropped)
    }

    @Test
    fun `p95 抓得到尾巴的卡頓，平均值會被稀釋`() {
        val ideal = 1000.0 / 72
        // 90 幀正常、10 幀很慢：慢的要超過 5% 才會被 p95 抓到
        val gaps = List(90) { ideal } + List(10) { 100.0 }
        val stats = feed(72f, gaps)
        assertTrue("p50 應該還是正常值：${stats.percentileMs(50.0)}", stats.percentileMs(50.0) < ideal + 1)
        assertTrue("p95 應該看得到卡頓：${stats.percentileMs(95.0)}", stats.percentileMs(95.0) > 50.0)
    }

    @Test
    fun `只有一幀或沒有幀：回報 0，不會除以零`() {
        assertEquals(0.0, FrameStats(72f).fps, 0.0)
        assertEquals(0.0, feed(72f, emptyList()).fps, 0.0)
        assertEquals(0, feed(72f, emptyList()).dropped)
        assertEquals(0.0, FrameStats(0f).fps, 0.0)
    }

    @Test
    fun `百分位用內插，不是取最接近的那個`() {
        // 0,10,20,30,40：p50 = 20；p25 落在 10 與 20 中間 = 10
        val values = listOf(0.0, 10.0, 20.0, 30.0, 40.0)
        assertEquals(20.0, FrameStats.percentile(values, 50.0), 0.001)
        assertEquals(10.0, FrameStats.percentile(values, 25.0), 0.001)
        assertEquals(5.0, FrameStats.percentile(values, 12.5), 0.001)
        assertEquals(40.0, FrameStats.percentile(values, 100.0), 0.001)
        assertEquals(0.0, FrameStats.percentile(emptyList(), 50.0), 0.001)
    }

    @Test
    fun `正常抖動不算掉幀`() {
        val ideal = 1000.0 / 72
        // 1.4 倍還在容忍範圍內
        assertEquals(0, FrameStats.dropped(List(50) { ideal * 1.4 }, 72f))
        assertTrue(FrameStats.dropped(List(50) { ideal * 2 }, 72f) > 0)
    }
}

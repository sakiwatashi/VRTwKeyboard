package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistTest {

    @Test
    fun `霧會一直往右飄，飄出去之後從左邊回來，不會消失`() {
        val mist = Mist()
        val start = mist.items.map { it.x }
        repeat(60 * 120) { mist.update(1f / 60) }   // 兩分鐘
        for (wisp in mist.items) {
            assertTrue(
                "有一團飄到畫面外回不來了：${wisp.x}",
                wisp.x >= -Mist.MARGIN - wisp.width && wisp.x <= 1f + Mist.MARGIN + wisp.width,
            )
        }
        assertTrue("兩分鐘後位置應該變過", mist.items.map { it.x } != start)
    }

    @Test
    fun `一開始就散在各處，不會全部擠在同一邊`() {
        val xs = Mist().items.map { it.x }
        assertTrue("起點應該散開：$xs", xs.max() - xs.min() > 0.5f)
    }

    @Test
    fun `每一團的速度不同，不然會整排一起平移看起來像一塊板子`() {
        val speeds = Mist().items.map { it.speed }.distinct()
        assertTrue("速度應該有差異：$speeds", speeds.size >= 3)
        for (speed in speeds) assertTrue("速度 $speed 超出範圍", speed in Mist.SPEED_MIN..Mist.SPEED_MAX)
    }

    @Test
    fun `飄的速度要看得出來：橫越畫面不能超過半分鐘`() {
        // 原本 0.012/秒 等於 83 秒才橫越一次，實機上看起來就是靜止的。
        for (wisp in Mist().items) {
            val crossSeconds = (1f + Mist.MARGIN * 2) / wisp.speed
            assertTrue("太慢了，要 ${crossSeconds.toInt()} 秒才飄過去", crossSeconds < 30f)
            assertTrue("太快了，要 ${crossSeconds.toInt()} 秒就飄過去，像雲不像霧", crossSeconds > 8f)
        }
    }

    @Test
    fun `霧要夠淡，不然會蓋住鍵帽上的字`() {
        for (wisp in Mist().items) {
            assertTrue("太濃了：${wisp.alpha}", wisp.alpha <= Mist.ALPHA_MAX)
            assertTrue("太淡了看不見：${wisp.alpha}", wisp.alpha >= Mist.ALPHA_MIN)
        }
    }

    @Test
    fun `上下會輕微起伏，不是水平直線平移`() {
        val wisp = Mist().items.first()
        val heights = (0..40).map { wisp.yAt(it * 0.25f) }
        assertTrue("應該有上下起伏：${heights.max() - heights.min()}", heights.max() - heights.min() > 0.02f)
        // 但不能飄出畫面
        for (y in heights) assertTrue("飄出畫面了：$y", y in -0.1f..1.1f)
    }

    @Test
    fun `團數固定，不會越跑越多`() {
        val mist = Mist(count = 5)
        assertEquals(5, mist.items.size)
        repeat(600) { mist.update(1f / 60) }
        assertEquals(5, mist.items.size)
    }
}

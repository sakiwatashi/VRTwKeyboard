package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FxTest {

    private val cyan = 0xFF45E0FF.toInt()

    @Test
    fun `噴出來的火花會往四面八方，不是全往同一邊`() {
        val particles = Particles(random = Random(1))
        particles.burst(100f, 100f, count = 16, speed = 200f, color = cyan)
        assertEquals(16, particles.size)
        assertTrue("應該有往左的", particles.items.any { it.vx < 0 })
        assertTrue("應該有往右的", particles.items.any { it.vx > 0 })
        assertTrue("應該有往上的", particles.items.any { it.vy < 0 })
    }

    @Test
    fun `火花會落下並消失，不會永遠留著`() {
        val particles = Particles(random = Random(2))
        particles.burst(100f, 100f, count = 12, speed = 200f, color = cyan)
        val startY = particles.items.map { it.y }.average()
        repeat(6) { particles.update(1f / 60) }
        assertTrue("重力應該把它們往下拉", particles.items.map { it.y }.average() > startY)
        // fade 2.6：大約 0.39 秒燒完
        repeat(40) { particles.update(1f / 60) }
        assertEquals(0, particles.size)
        assertFalse(particles.busy)
    }

    @Test
    fun `連打不會讓粒子無限累積：超過上限就不再噴`() {
        val particles = Particles(max = 30, random = Random(3))
        repeat(20) { particles.burst(0f, 0f, count = 16, speed = 200f, color = cyan) }
        assertTrue("不可以超過上限：${particles.size}", particles.size <= 30)
    }

    @Test
    fun `能量環會擴散、會淡掉，而且連打只留最新的幾圈`() {
        val shocks = Shocks(max = 3)
        shocks.add(0f, 0f)
        repeat(5) { shocks.update(1f / 60) }
        assertTrue("半徑要變大：${shocks.items[0].radius}", shocks.items[0].radius > 0f)

        repeat(10) { shocks.add(0f, 0f) }
        assertTrue("只留最新的幾圈：${shocks.items.size}", shocks.items.size <= 3)

        val one = Shocks()
        one.add(0f, 0f)
        repeat(60) { one.update(1f / 60) }
        assertFalse("一秒後應該散掉", one.busy)
    }

    @Test
    fun `飛字會到站，而且中途是弧線不是直線`() {
        val flyer = Flyer("ㄅ", fromX = 100f, fromY = 300f, toX = 400f, toY = 60f)
        assertEquals(100f, flyer.x, 0.5f)
        assertEquals(300f, flyer.y, 0.5f)

        while (flyer.update(1f / 60)) Unit
        assertTrue(flyer.done)
        assertEquals(400f, flyer.x, 0.5f)
        assertEquals(60f, flyer.y, 0.5f)
    }

    @Test
    fun `飛字中途會被弧度抬高：起終點同高時才看得出來`() {
        // 拿斜線比「直線」會被緩動曲線騙過去：緩動本身就會讓 y 偏離等速直線。
        // 起點與終點同高，任何偏離就一定是弧度造成的。
        val flyer = Flyer("ㄅ", fromX = 0f, fromY = 200f, toX = 300f, toY = 200f)
        assertEquals(200f, flyer.y, 0.5f)
        while (flyer.progress < 0.5f) flyer.update(1f / 60)
        assertTrue("中途應該被抬高：${flyer.y}", flyer.y < 200f - 20f)
        while (flyer.update(1f / 60)) Unit
        assertEquals("到站要回到原本的高度", 200f, flyer.y, 0.5f)
    }

    @Test
    fun `飛字最後才淡出，不是一路半透明`() {
        val flyer = Flyer("ㄅ", 0f, 0f, 100f, 0f)
        assertEquals(1f, flyer.alpha, 0.001f)
        while (flyer.progress < 0.5f) flyer.update(1f / 60)
        assertEquals("一半路程還要是實心的", 1f, flyer.alpha, 0.001f)
        while (flyer.update(1f / 60)) Unit
        assertTrue("到站時應該幾乎透明：${flyer.alpha}", flyer.alpha < 0.05f)
    }
}

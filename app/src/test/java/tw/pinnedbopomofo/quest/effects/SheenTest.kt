package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheenTest {

    @Test
    fun `掃的期間從 0 走到 1，停頓期間不畫`() {
        val sheen = Sheen(sweepMs = 900, pauseMs = 5_000)
        assertEquals(0f, sheen.positionAt(0)!!, 0.001f)
        assertEquals(0.5f, sheen.positionAt(450)!!, 0.001f)
        // 899 還在掃，900 就進入停頓
        assertNotNull(sheen.positionAt(899))
        assertNull(sheen.positionAt(900))
        assertNull(sheen.positionAt(3_000))
        assertNull(sheen.positionAt(5_899))
    }

    @Test
    fun `停頓結束後會再掃一次，不是只掃一輪`() {
        val sheen = Sheen(sweepMs = 900, pauseMs = 5_000)
        // 一個週期是 5900 ms
        assertEquals(0f, sheen.positionAt(5_900)!!, 0.001f)
        assertEquals(0.5f, sheen.positionAt(6_350)!!, 0.001f)
        assertNull(sheen.positionAt(6_800))
    }

    @Test
    fun `光帶要從畫面外進來、從畫面外出去，不能在邊緣憑空出現`() {
        val sheen = Sheen()
        val width = 800f
        assertTrue("起點應該在左邊界外：${sheen.centerX(0f, width)}", sheen.centerX(0f, width) < 0f)
        assertTrue("終點應該在右邊界外：${sheen.centerX(1f, width)}", sheen.centerX(1f, width) > width)
        // 中間點要落在畫面內
        val middle = sheen.centerX(0.5f, width)
        assertTrue("中點應該在畫面內：$middle", middle > 0f && middle < width)
    }

    @Test
    fun `時間是負的就不要畫`() {
        assertNull(Sheen().positionAt(-1))
    }

    @Test
    fun `停頓比掃描長很多：絕大多數時間不需要畫`() {
        val sheen = Sheen(sweepMs = 900, pauseMs = 5_000)
        val drawn = (0 until 5_900L).count { sheen.positionAt(it) != null }
        assertTrue("應該只有不到兩成的時間在畫：$drawn", drawn < 5_900 * 0.2)
    }
}

package tw.pinnedbopomofo.quest.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test
    fun `一般情況算得出百分比`() {
        assertEquals(0, Progress(0, 1000).percent)
        assertEquals(50, Progress(500, 1000).percent)
        assertEquals(100, Progress(1000, 1000).percent)
    }

    @Test
    fun `伺服器沒給長度時回負數，讓畫面畫不定量的進度條`() {
        assertEquals(-1, Progress(500, 0).percent)
        assertEquals(-1f, Progress(500, 0).fraction, 0.001f)
    }

    @Test
    fun `已完成超過總量也不會超過 100`() {
        // 續傳時偶爾會多算，不能讓進度條爆掉
        assertEquals(100, Progress(1200, 1000).percent)
        assertTrue(Progress(1200, 1000).fraction <= 1f)
    }

    @Test
    fun `大小顯示成人看得懂的單位`() {
        assertEquals("512 B", Progress.readable(512))
        assertEquals("1 KB", Progress.readable(1024))
        assertEquals("1.0 MB", Progress.readable(1024L * 1024))
        assertEquals("226.0 MB", Progress.readable(226L * 1024 * 1024))
        assertEquals("1.50 GB", Progress.readable((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("?", Progress.readable(-1))
    }

    @Test
    fun `語音模型的實際大小要顯示成 226 MB 左右`() {
        val total = 165462184L + 71664561L + 75756L
        val text = Progress.readable(total)
        assertTrue("顯示成 $text，應該是 2xx MB", text.endsWith(" MB") && text.startsWith("22"))
    }
}

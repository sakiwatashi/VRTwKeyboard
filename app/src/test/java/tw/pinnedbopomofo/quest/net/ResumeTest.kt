package tw.pinnedbopomofo.quest.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeTest {

    private val expected = 1000L

    @Test
    fun `沒有檔案就從頭下載`() {
        assertEquals(Resume.Plan.Continue(0L), Resume.plan(existing = 0L, expected = expected))
    }

    @Test
    fun `下到一半就從斷點接下去`() {
        assertEquals(Resume.Plan.Continue(400L), Resume.plan(existing = 400L, expected = expected))
    }

    @Test
    fun `剛好等於預期大小就只驗雜湊，不要再送 Range`() {
        // 這裡若回 Continue(1000)，伺服器會回 416 Range Not Satisfiable，
        // 看起來像下載失敗，實際上檔案早就好了。
        assertEquals(Resume.Plan.Verify, Resume.plan(existing = expected, expected = expected))
    }

    @Test
    fun `比預期大代表不是同一個檔案，要重來`() {
        // 若當成可以續傳，會一直送出超過檔案長度的 Range，永遠下載不完。
        assertEquals(Resume.Plan.Restart, Resume.plan(existing = expected + 1, expected = expected))
    }

    @Test
    fun `不知道預期大小就不要猜`() {
        assertEquals(Resume.Plan.Restart, Resume.plan(existing = 400L, expected = 0L))
        assertEquals(Resume.Plan.Restart, Resume.plan(existing = 400L, expected = -1L))
    }

    @Test
    fun `Range 標頭只在真的要續傳時才出現`() {
        assertNull("從 0 開始不該送 Range", Resume.rangeHeader(0L))
        assertEquals("bytes=400-", Resume.rangeHeader(400L))
    }
}

package tw.pinnedbopomofo.quest.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `抽得出 tag 裡的數字，前綴不影響`() {
        assertEquals(listOf(0, 1), Version.parse("quest-keyboard-v0.1"))
        assertEquals(listOf(0, 2), Version.parse("v0.2"))
        assertEquals(listOf(1, 0, 3), Version.parse("1.0.3"))
        assertEquals(emptyList<Int>(), Version.parse(null))
        assertEquals(emptyList<Int>(), Version.parse("nightly"))
    }

    @Test
    fun `新版才回 true`() {
        assertTrue(Version.isNewer("v0.2", "v0.1"))
        assertTrue(Version.isNewer("quest-keyboard-v0.2", "quest-keyboard-v0.1"))
        assertFalse(Version.isNewer("v0.1", "v0.2"))
        assertFalse("同版本不該提示更新", Version.isNewer("v0.1", "v0.1"))
    }

    @Test
    fun `段數不同時短的補零`() {
        assertFalse("0.2 與 0.2.0 是同一版", Version.isNewer("v0.2", "v0.2.0"))
        assertFalse(Version.isNewer("v0.2.0", "v0.2"))
        assertTrue("0.2.1 比 0.2 新", Version.isNewer("v0.2.1", "v0.2"))
    }

    @Test
    fun `十位數要照數值比，不能照字串比`() {
        // 字串比較會說 "9" > "10"，那樣 v0.10 出來之後就再也不會提示更新
        assertTrue(Version.isNewer("v0.10", "v0.9"))
        assertFalse(Version.isNewer("v0.9", "v0.10"))
        assertTrue(Version.isNewer("v2.0", "v1.99"))
    }

    @Test
    fun `看不懂的 tag 一律不提示，不要亂猜`() {
        assertFalse(Version.isNewer("nightly", "v0.1"))
        assertFalse(Version.isNewer("v0.2", "unknown"))
        assertFalse(Version.isNewer(null, "v0.1"))
        assertFalse(Version.isNewer("v0.2", null))
    }
}

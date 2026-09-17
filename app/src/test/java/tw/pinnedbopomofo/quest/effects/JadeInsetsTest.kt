package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.pinnedbopomofo.quest.KeyboardSizing

class JadeInsetsTest {

    @Test
    fun `扣掉內距之後，鍵盤仍然裝得進視窗`() {
        // 2026-09-17 的 bug：內距是算完尺寸才加上去的，
        // 內容 351 + 內距 58 = 409 塞進 351 的視窗，底部被切掉。
        for ((width, height) in listOf(780 to 351, 780 to 355, 640 to 300, 1200 to 600, 3664 to 1920)) {
            val insets = JadeInsets.of(width, height)
            val sizing = KeyboardSizing.fit(width - insets.width, height - insets.height, 1.25f)
            assertTrue(
                "${width}x$height：內容 ${sizing.totalHeight} + 內距 ${insets.height} 超出視窗 $height",
                sizing.totalHeight + insets.height <= height,
            )
            assertTrue(
                "${width}x$height：面板 ${sizing.panelWidth} + 內距 ${insets.width} 超出視窗 $width",
                sizing.panelWidth + insets.width <= width,
            )
        }
    }

    @Test
    fun `表情頁的格子高度用剩下的空間算，功能列一定留得住`() {
        val insets = JadeInsets.of(780, 351)
        val sizing = KeyboardSizing.fit(780 - insets.width, 351 - insets.height, 1.25f)
        val rowHeight = sizing.keyHeight + sizing.keyGap * 2
        // 分類列 + 功能列固定佔兩排，剩下的給格子
        val gridHeight = (sizing.keyAreaHeight - rowHeight * 2).coerceAtLeast(rowHeight)
        assertTrue(
            "格子 $gridHeight + 兩排 ${rowHeight * 2} 不可以超過按鍵區 ${sizing.keyAreaHeight}",
            gridHeight + rowHeight * 2 <= sizing.keyAreaHeight,
        )
        assertTrue("格子至少要有一排高", gridHeight >= rowHeight)
    }

    @Test
    fun `尺寸是零就沒有內距，不要除出奇怪的值`() {
        assertEquals(JadeInsets.NONE, JadeInsets.of(0, 351))
        assertEquals(JadeInsets.NONE, JadeInsets.of(780, 0))
    }

    @Test
    fun `左右上下不一樣：金框本來就不對稱`() {
        val insets = JadeInsets.of(1000, 1000)
        assertTrue("上下應該不同：${insets.top} vs ${insets.bottom}", insets.top != insets.bottom)
        assertTrue("左右應該不同：${insets.left} vs ${insets.right}", insets.left != insets.right)
    }
}

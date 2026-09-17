package tw.pinnedbopomofo.quest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSizingTest {

    @Test
    fun `手機空間足夠時維持原本尺寸`() {
        // Pixel 7：輸入法可用整個 1080x2400，密度 2.625
        val sizing = KeyboardSizing.fit(1080, 2400, 2.625f)
        assertEquals((56 * 2.625f).toInt(), sizing.keyHeight)
        assertEquals((52 * 2.625f).toInt(), sizing.candidateHeight)
        assertEquals(22f, sizing.keyTextSp)
    }

    @Test
    fun `Quest 的 780x355 視窗放得下功能列`() {
        // 2026-09-15 頭盔實測：輸入法視窗 780×355，密度 200dpi（1.25）。原本尺寸要 465 像素高。
        val sizing = KeyboardSizing.fit(780, 355, 1.25f)
        assertTrue("總高 ${sizing.totalHeight} 超出 355：$sizing", sizing.totalHeight <= 355)
        assertTrue("按鍵太小不好點：$sizing", sizing.keyHeight >= 40)
        assertEquals("這個寬度本來就合理，不該再縮", 780, sizing.panelWidth)
    }

    @Test
    fun `頭盔改成回報整個顯示器時，鍵盤不會鋪滿整個寬度`() {
        // 2026-09-16 實測：重開機後系統回報 3664x1920，鍵盤被排成 3664 像素寬的長條
        val sizing = KeyboardSizing.fit(3664, 1920, 1.25f)
        assertTrue("面板寬 ${sizing.panelWidth} 應該留白置中", sizing.panelWidth < 3664)
        assertTrue("面板寬 ${sizing.panelWidth} 超過上限", sizing.panelWidth <= (640 * 1.25f).toInt())
    }

    @Test
    fun `面板很窄時按鍵不會變成又高又細`() {
        val sizing = KeyboardSizing.fit(300, 1920, 1.25f)
        assertTrue(
            "鍵高 ${sizing.keyHeight}、鍵寬 ${sizing.keyWidth} 比例失衡",
            sizing.keyHeight <= sizing.keyWidth * 2.5f,
        )
    }

    @Test
    fun `各種窄視窗都不會超出`() {
        for (height in 200..600) {
            for (density in listOf(1f, 1.25f, 2f, 2.625f)) {
                val sizing = KeyboardSizing.fit(780, height, density)
                val preferred = KeyboardSizing.fit(780, 0, density)
                val expected = if (preferred.totalHeight <= height) preferred.totalHeight else height
                assertTrue(
                    "高度 $height 密度 $density：總高 ${sizing.totalHeight}",
                    sizing.totalHeight <= expected,
                )
            }
        }
    }
}

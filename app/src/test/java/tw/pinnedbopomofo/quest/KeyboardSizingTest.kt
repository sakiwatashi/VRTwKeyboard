package tw.pinnedbopomofo.quest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSizingTest {

    @Test
    fun `手機空間足夠時維持原本尺寸`() {
        // Pixel 7：輸入法可用整個 2400 像素高，密度 2.625
        val sizing = KeyboardSizing.fit(2400, 2.625f)
        assertEquals((56 * 2.625f).toInt(), sizing.keyHeight)
        assertEquals((52 * 2.625f).toInt(), sizing.candidateHeight)
        assertEquals(22f, sizing.keyTextSp)
    }

    @Test
    fun `Quest 的 355 像素視窗放得下功能列`() {
        // 頭盔實測：輸入法視窗 780×355，密度 200dpi（1.25）。原本尺寸要 465 像素高。
        val sizing = KeyboardSizing.fit(355, 1.25f)
        assertTrue("總高 ${sizing.totalHeight} 超出 355：$sizing", sizing.totalHeight <= 355)
        assertTrue("按鍵太小不好點：$sizing", sizing.keyHeight >= 40)
    }

    @Test
    fun `各種窄視窗都不會超出`() {
        for (height in 200..600) {
            for (density in listOf(1f, 1.25f, 2f, 2.625f)) {
                val sizing = KeyboardSizing.fit(height, density)
                val preferred = KeyboardSizing.fit(0, density)
                val expected = if (preferred.totalHeight <= height) preferred.totalHeight else height
                assertTrue(
                    "高度 $height 密度 $density：總高 ${sizing.totalHeight}",
                    sizing.totalHeight <= expected,
                )
            }
        }
    }
}

package tw.pinnedbopomofo.quest

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每套配色的字都要看得清楚。算的是「實際呈現的顏色」：鍵面是面板底色往按鍵色調出來的，
 * 字是文字色往白色提亮過的，不是設定檔裡的原始值。
 */
class KeyboardContrastTest {

    @Test
    fun `每套配色的字在鍵帽上都要夠清楚`() {
        for (theme in KeyboardThemes.all) {
            val label = KeyboardThemes.labelColor(theme)
            for (special in listOf(false, true)) {
                val ratio = KeyboardThemes.contrastRatio(label, KeyboardThemes.faceColor(theme, special))
                assertTrue(
                    "配色「${theme.name}」的${if (special) "功能鍵" else "一般鍵"}對比只有 ${"%.2f".format(ratio)}",
                    ratio >= MINIMUM,
                )
            }
        }
    }

    @Test
    fun `Enter 與選中鍵上的字也要夠清楚`() {
        for (theme in KeyboardThemes.all) {
            val ratio = KeyboardThemes.contrastRatio(theme.accentText, KeyboardThemes.activeFace(theme))
            assertTrue(
                "配色「${theme.name}」的重點鍵對比只有 ${"%.2f".format(ratio)}",
                ratio >= MINIMUM,
            )
        }
    }

    /**
     * 候選字是畫在**透明底**上的，也就是直接畫在面板底色上。
     *
     * 2026-09-17 發現「竹簡」踩到這個：鍵面是淺竹色所以字是墨色，但面板是深棕，
     * 墨色字對深棕面板的對比只有 1.06，候選字等於看不見。
     * 原本的測試只量「字對鍵帽」，量不到這條。
     */
    @Test
    fun `候選字在面板底色上也要看得清楚`() {
        for (theme in KeyboardThemes.all) {
            val ratio = KeyboardThemes.contrastRatio(KeyboardThemes.panelTextColor(theme), theme.tint)
            assertTrue(
                "配色「${theme.name}」的候選字對面板只有 ${"%.2f".format(ratio)}",
                ratio >= MINIMUM,
            )
        }
    }

    /**
     * 組字中的字也畫在面板上。深色面板直接用重點色沒問題，但淺色面板不行：
     * 2026-09-17 青玉的金色組字對淺玉板只有 1.63，實機上完全看不見。
     */
    @Test
    fun `組字中的字在面板上也要看得清楚`() {
        for (theme in KeyboardThemes.all) {
            val ratio = KeyboardThemes.contrastRatio(KeyboardThemes.panelAccentColor(theme), theme.tint)
            assertTrue(
                "配色「${theme.name}」的組字色對面板只有 ${"%.2f".format(ratio)}",
                ratio >= MINIMUM,
            )
        }
    }

    private companion object {
        /** WCAG AA 對一般文字的門檻。鍵帽上的字偏大，但頭盔解析度有限，這裡不放寬。 */
        const val MINIMUM = 4.5
    }
}

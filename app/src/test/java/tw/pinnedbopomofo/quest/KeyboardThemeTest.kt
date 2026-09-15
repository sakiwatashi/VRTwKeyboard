package tw.pinnedbopomofo.quest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeTest {

    @Test
    fun `沒存過或存了不存在的配色時用暖茶`() {
        assertEquals("暖茶", KeyboardThemes.default.name)
        assertEquals(KeyboardThemes.default, KeyboardThemes.named(null))
        assertEquals(KeyboardThemes.default, KeyboardThemes.named("不存在的配色"))
        assertEquals("靛夜", KeyboardThemes.named("靛夜").name)
    }

    @Test
    fun `無彩配色的每個顏色都沒有色相`() {
        val mono = KeyboardThemes.named("無彩")
        assertEquals("無彩", mono.name)
        for (color in listOf(mono.tint, mono.text, mono.key, mono.accent, mono.accentText)) {
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            assertTrue("顏色 %06X 帶有色相".format(color and 0xFFFFFF), red == green && green == blue)
        }
    }

    @Test
    fun `透明度換算成 0 到 255，顏色本身不變`() {
        val color = KeyboardThemes.rgb(10, 20, 30)
        assertEquals(128, KeyboardThemes.withAlpha(color, 0.5f) ushr 24)
        assertEquals(0, KeyboardThemes.withAlpha(color, 0f) ushr 24)
        assertEquals(255, KeyboardThemes.withAlpha(color, 1f) ushr 24)
        assertEquals(color and 0xFFFFFF, KeyboardThemes.withAlpha(color, 0.35f) and 0xFFFFFF)
    }

    @Test
    fun `兩個顏色依比例混合，各色版各自計算`() {
        val red = KeyboardThemes.rgb(255, 0, 0)
        val blue = KeyboardThemes.rgb(0, 0, 255)
        assertEquals(KeyboardThemes.rgb(128, 0, 128), KeyboardThemes.mix(red, blue, 0.5f))
        assertEquals(red, KeyboardThemes.mix(red, blue, 0f))
        assertEquals(blue, KeyboardThemes.mix(red, blue, 1f))
        assertEquals(255, KeyboardThemes.mix(red, blue, 0.3f) ushr 24)
    }

    @Test
    fun `無彩配色調出來的鍵帽顏色也沒有色相`() {
        val mono = KeyboardThemes.named("無彩")
        for (fraction in listOf(0.11f, 0.18f, 0.2f, 0.3f)) {
            val cap = KeyboardThemes.mix(mono.tint, mono.key, fraction)
            val red = (cap shr 16) and 0xFF
            val green = (cap shr 8) and 0xFF
            val blue = cap and 0xFF
            assertTrue("鍵帽 %06X 帶有色相".format(cap and 0xFFFFFF), red == green && green == blue)
        }
    }

    @Test
    fun `配色名稱不重複`() {
        val names = KeyboardThemes.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}

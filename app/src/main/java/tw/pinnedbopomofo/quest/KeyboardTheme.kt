package tw.pinnedbopomofo.quest

/**
 * 鍵盤配色，數值來自設計稿「D 暖色玻璃」的六套配色。
 * 顏色都是不含透明度的 ARGB（alpha 固定 0xFF），實際用途再用 [KeyboardThemes.withAlpha] 套透明度。
 */
data class KeyboardTheme(
    val name: String,
    /** 面板底色。 */
    val tint: Int,
    val text: Int,
    /** 按鍵填色；一般鍵、功能鍵、按下時各用不同透明度。 */
    val key: Int,
    /** Enter、選定的字、第一個候選。 */
    val accent: Int,
    /** 放在重點色上的字。 */
    val accentText: Int,
)

/**
 * Quest 沒有視窗模糊（2026-09 實測），玻璃感只能靠半透明做；三段讓使用者依環境亮度挑。
 * 段落要拉得夠開：35% 和 50% 在頭盔的透視畫面裡幾乎看不出差別。
 */
enum class PanelOpacity(val label: String, val alpha: Float) {
    CLEAR("清透", 0.2f),
    MIST("霧面", 0.6f),
    SOLID("實色", 0.92f),
}

object KeyboardThemes {
    fun rgb(red: Int, green: Int, blue: Int) = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    /** 兩個顏色依比例混合（fraction 0 是 from、1 是 to），結果不透明。用來從面板底色調出鍵帽顏色。 */
    fun mix(from: Int, to: Int, fraction: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * fraction.coerceIn(0f, 1f))
        }
        return rgb(channel(16), channel(8), channel(0))
    }

    /** 換掉顏色的透明度，保留 RGB。純計算，單元測試不需要 Android。 */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = Math.round(alpha.coerceIn(0f, 1f) * 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    val all = listOf(
        KeyboardTheme("暖茶", rgb(38, 28, 22), rgb(0xE9, 0xDD, 0xCF), rgb(255, 244, 232), rgb(0xDE, 0x6A, 0x52), rgb(0xFF, 0xF3, 0xEA)),
        KeyboardTheme("抹茶", rgb(24, 34, 28), rgb(0xDD, 0xE6, 0xD8), rgb(236, 248, 236), rgb(0x93, 0xBD, 0x7F), rgb(0x15, 0x20, 0x1A)),
        KeyboardTheme("靛夜", rgb(22, 26, 44), rgb(0xDC, 0xE1, 0xF0), rgb(232, 238, 255), rgb(0x86, 0xA2, 0xEC), rgb(0x11, 0x16, 0x28)),
        KeyboardTheme("霧櫻", rgb(44, 28, 34), rgb(0xEE, 0xDD, 0xE1), rgb(255, 238, 242), rgb(0xD9, 0x8C, 0xA0), rgb(0x2A, 0x14, 0x1A)),
        KeyboardTheme("石墨金", rgb(30, 30, 32), rgb(0xE2, 0xE0, 0xDA), rgb(245, 244, 240), rgb(0xCF, 0xA9, 0x65), rgb(0x23, 0x1C, 0x10)),
        // 完全沒有色相：Enter 和第一候選靠亮灰與粗體區分
        KeyboardTheme("無彩", rgb(26, 26, 26), rgb(0xE6, 0xE6, 0xE6), rgb(255, 255, 255), rgb(0xD6, 0xD6, 0xD6), rgb(0x16, 0x16, 0x16)),
    )

    val default = all.first()

    /** 設定檔裡的名字對不上（沒存過、或配色改名）就用預設，不讓鍵盤起不來。 */
    fun named(name: String?) = all.firstOrNull { it.name == name } ?: default
}

package tw.pinnedbopomofo.quest

/**
 * 鍵盤配色，數值來自設計稿「D 暖色玻璃」的六套配色。
 * 顏色都是不含透明度的 ARGB（alpha 固定 0xFF），實際用途再用 [KeyboardThemes.withAlpha] 套透明度。
 */
/** 鍵帽的畫法；顏色以外的風格差異。 */
enum class KeyFace {
    /** 漸層鍵面加下緣厚度，原本的樣子。 */
    FLAT,
    /** 瑪利歐磚塊：三排交錯細磚、近黑描邊；重點鍵是問號磚。 */
    BRICK,
    /** 霓虹：深色鍵面加外發光邊框。 */
    NEON,

    /** 竹簡：直向竹片、兩道繩結。 */
    BAMBOO,

    /**
     * 完全平的：單色鍵面、大圓角，沒有漸層、沒有下緣厚度、沒有反光。
     * 系統鍵盤那種乾淨的樣子，靠留白與圓角撐場面，不靠打光。
     */
    PLAIN,

    /** 青玉：半透明玉片、上緣一道透光、金線收邊。 */
    JADE,
}

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
    val face: KeyFace = KeyFace.FLAT,
    /** 風格用的第二個顏色：磚縫、金框、霓虹光、竹簡的繩結。FLAT 用不到。 */
    val detail: Int = 0,
    /**
     * 鍵帽上的字往白色提亮多少。深色鍵盤要提亮才不會糊進背景；
     * 淺色鍵面（竹簡）要的是墨字，就設 0。
     */
    val labelMix: Float = 0.45f,
    /** 直接指定鍵面顏色（上、下）。0 表示照慣例從面板底色調出來。 */
    val faceTop: Int = 0,
    val faceBottom: Int = 0,
    /**
     * 正在組字的那幾個字的顏色（畫在面板上）。0 表示用 [accent]。
     *
     * 深色面板的配色直接用重點色就好，但**淺色面板**不行：青玉的重點色是金，
     * 金字畫在淺玉板上對比只有 1.63，等於看不見。
     */
    val panelAccent: Int = 0,
    /**
     * 畫在面板上（不是鍵帽上）的字色：候選字、提示字。
     *
     * 大部分配色的面板與鍵帽明暗一致，用同一個字色就好，所以預設 0 =「跟鍵帽的字同色」。
     * 但**淺鍵帽配深面板**（竹簡、青玉）的鍵帽字是墨色，直接拿去畫在深面板上會看不見，
     * 這種配色一定要另外指定。
     */
    val panelText: Int = 0,
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

    // 鍵帽顏色是從面板底色往按鍵色調出來的，比例放在這裡，測試才能算出實際顏色
    const val CAP_NORMAL_TOP = 0.3f
    const val CAP_NORMAL_BOTTOM = 0.2f
    const val CAP_SPECIAL_TOP = 0.18f
    const val CAP_SPECIAL_BOTTOM = 0.11f
    const val ACTIVE_BRIGHTEN = 0.18f
    const val LABEL_BRIGHTEN = 0.45f

    /** 鍵帽上的字實際呈現的顏色。 */
    fun labelColor(theme: KeyboardTheme) = mix(theme.text, rgb(255, 255, 255), theme.labelMix)

    /** 畫在面板上的字色：沒特別指定就跟鍵帽的字同色。 */
    fun panelTextColor(theme: KeyboardTheme) =
        if (theme.panelText != 0) theme.panelText else labelColor(theme)

    /** 組字中的字色：沒特別指定就用重點色。 */
    fun panelAccentColor(theme: KeyboardTheme) =
        if (theme.panelAccent != 0) theme.panelAccent else theme.accent

    /** 鍵面的上下漸層顏色。配色可以直接指定，沒指定就從面板底色往按鍵色調。 */
    fun faceColors(theme: KeyboardTheme, special: Boolean): Pair<Int, Int> {
        if (theme.faceTop != 0) {
            // 功能鍵稍微往面板底色壓暗，跟字鍵分得開
            val shade = if (special) 0.18f else 0f
            return mix(theme.faceTop, theme.tint, shade) to mix(theme.faceBottom, theme.tint, shade)
        }
        val top = if (special) CAP_SPECIAL_TOP else CAP_NORMAL_TOP
        val bottom = if (special) CAP_SPECIAL_BOTTOM else CAP_NORMAL_BOTTOM
        return mix(theme.tint, theme.key, top) to mix(theme.tint, theme.key, bottom)
    }

    /** 一般鍵與功能鍵鍵面的平均顏色（上下漸層的中間），對比度計算用。 */
    fun faceColor(theme: KeyboardTheme, special: Boolean): Int {
        val (top, bottom) = faceColors(theme, special)
        return mix(top, bottom, 0.5f)
    }

    /** Enter、選中的鍵的鍵面顏色。 */
    fun activeFace(theme: KeyboardTheme) = mix(theme.accent, rgb(255, 255, 255), ACTIVE_BRIGHTEN / 2)

    /** 相對亮度（WCAG）。純計算，測試用得到。 */
    fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** 兩個顏色的對比度（WCAG），1 到 21。鍵盤上的字至少要 4.5 才看得清楚。 */
    fun contrastRatio(foreground: Int, background: Int): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    val all = listOf(
        // Enter 上原本是白字，對比只有 2.74（2026-09-16 量出來的）；改成深褪色字，對比 5.39
        KeyboardTheme("暖茶", rgb(38, 28, 22), rgb(0xE9, 0xDD, 0xCF), rgb(255, 244, 232), rgb(0xDE, 0x6A, 0x52), rgb(0x3A, 0x16, 0x0E)),
        KeyboardTheme("抹茶", rgb(24, 34, 28), rgb(0xDD, 0xE6, 0xD8), rgb(236, 248, 236), rgb(0x93, 0xBD, 0x7F), rgb(0x15, 0x20, 0x1A)),
        KeyboardTheme("靛夜", rgb(22, 26, 44), rgb(0xDC, 0xE1, 0xF0), rgb(232, 238, 255), rgb(0x86, 0xA2, 0xEC), rgb(0x11, 0x16, 0x28)),
        KeyboardTheme("霧櫻", rgb(44, 28, 34), rgb(0xEE, 0xDD, 0xE1), rgb(255, 238, 242), rgb(0xD9, 0x8C, 0xA0), rgb(0x2A, 0x14, 0x1A)),
        KeyboardTheme("石墨金", rgb(30, 30, 32), rgb(0xE2, 0xE0, 0xDA), rgb(245, 244, 240), rgb(0xCF, 0xA9, 0x65), rgb(0x23, 0x1C, 0x10)),
        // 完全沒有色相：Enter 和第一候選靠亮灰與粗體區分
        KeyboardTheme("無彩", rgb(26, 26, 26), rgb(0xE6, 0xE6, 0xE6), rgb(255, 255, 255), rgb(0xD6, 0xD6, 0xD6), rgb(0x16, 0x16, 0x16)),
        // 夜空藍面板、深赭紅磚（NES 磚色 #9C4A00 系），Enter 是問號磚的金黃
        KeyboardTheme(
            name = "磚塊", tint = rgb(24, 32, 72), text = rgb(0xFF, 0xF2, 0xE0), key = rgb(0xD2, 0x5E, 0x2E),
            accent = rgb(0xF7, 0xC1, 0x2E), accentText = rgb(0x3A, 0x22, 0x00),
            face = KeyFace.BRICK, detail = rgb(0x4A, 0x1C, 0x0C),
        ),
        // 竹簡：竹片配墨字，Enter 是朱漆木牌。淺色鍵面所以不提亮文字
        KeyboardTheme(
            name = "竹簡", tint = rgb(0x24, 0x1C, 0x12), text = rgb(0x2A, 0x20, 0x16), key = rgb(0xD6, 0xBA, 0x84),
            accent = rgb(0x9E, 0x28, 0x20), accentText = rgb(0xFA, 0xEC, 0xD8),
            face = KeyFace.BAMBOO, detail = rgb(0x5A, 0x3C, 0x20),
            labelMix = 0f, faceTop = rgb(0xDC, 0xC2, 0x8E), faceBottom = rgb(0xC0, 0xA0, 0x66),
            // 鍵帽是淺竹色所以字是墨色，但面板是深棕：候選字得另外給淺色，不然對比只有 1.05。
            // 組字色同理：硃砂原色對深棕面板只有 2.22，提亮成朱橘才看得見。
            panelAccent = rgb(0xE0, 0x92, 0x5C), panelText = rgb(0xE8, 0xD9, 0xB4),
        ),
        // 青玉山水：深潭色面板上畫層疊遠山與霧，鍵帽是半透明的青玉片，金線收邊。
        // 參考圖是淺青玉配白字，但那個組合對比只有 2.77，在頭盔上讀不動；
        // 改成青玉配墨字（8.93），也更接近水墨本來的樣子。
        KeyboardTheme(
            // tint 是「眼睛看到的面板底色」。青玉的面板整片被背景板那張圖蓋住，
            // 所以這裡放的是從圖上量到的玉板色 #B9CBBB，不是原本那個深潭色——
            // 填深色的話對比測試會拿一個看不到的顏色去算，算出來的數字沒有意義
            // （2026-09-17 實機：面板字對候選條只有 1.22，完全看不見）。
            name = "青玉", tint = rgb(0xB9, 0xCB, 0xBB), text = rgb(0x14, 0x26, 0x1F), key = rgb(0xB6, 0xD3, 0xC3),
            accent = rgb(0xC9, 0xA2, 0x27), accentText = rgb(0x14, 0x25, 0x1A),
            face = KeyFace.JADE, detail = rgb(0xD9, 0xB8, 0x4A),
            labelMix = 0f, faceTop = rgb(0xB6, 0xD3, 0xC3), faceBottom = rgb(0x9D, 0xC0, 0xAE),
            // 淺玉板上一律用墨色；組字中的字改用深赭，跟墨色候選字分得開又看得見
            panelAccent = rgb(0x75, 0x30, 0x0F), panelText = rgb(0x14, 0x26, 0x1F),
        ),
        // 系統鍵盤那種乾淨的淺色：暖灰面板、炭黑鍵、琥珀 Enter，完全平、不打光。
        // 面板是淺的、鍵是深的，所以 text 要深（候選字在面板上）而 labelMix 要拉到 0.95，
        // 讓鍵帽上的字提亮成近白。這是唯一一套「面板與鍵帽明暗相反」的配色。
        KeyboardTheme(
            name = "素白", tint = rgb(0xC9, 0xC8, 0xC3), text = rgb(0x33, 0x33, 0x2F), key = rgb(0x2E, 0x2E, 0x30),
            accent = rgb(0xF2, 0xC9, 0x4C), accentText = rgb(0x2A, 0x22, 0x05),
            face = KeyFace.PLAIN, detail = rgb(0xA3, 0xA3, 0xA6),
            labelMix = 0.95f, faceTop = rgb(0x2E, 0x2E, 0x30), faceBottom = rgb(0x2E, 0x2E, 0x30),
            // 鍵帽字提亮成近白（給深鍵帽），面板卻是淺的：候選字要用原本的深色，不然只有 1.54。
            // 組字色同理：琥珀原色對淺灰面板只有 1.06，壓深成褐金才看得見。
            panelAccent = rgb(0x5F, 0x48, 0x08), panelText = rgb(0x33, 0x33, 0x2F),
        ),
        // 夜市燈牌：深紫底、青色外發光、桃紅重點
        KeyboardTheme(
            name = "霓虹", tint = rgb(14, 10, 26), text = rgb(0xEC, 0xE6, 0xFF), key = rgb(0x6A, 0x50, 0xB4),
            accent = rgb(0xFF, 0x46, 0xA0), accentText = rgb(0x1A, 0x04, 0x12),
            face = KeyFace.NEON, detail = rgb(0x4A, 0xF0, 0xFF),
        ),
    )

    val default = all.first()

    /** 設定檔裡的名字對不上（沒存過、或配色改名）就用預設，不讓鍵盤起不來。 */
    fun named(name: String?) = all.firstOrNull { it.name == name } ?: default
}

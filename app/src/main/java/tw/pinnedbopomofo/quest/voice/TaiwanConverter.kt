package tw.pinnedbopomofo.quest.voice

import com.github.houbb.opencc4j.util.ZhTwConverterUtil

/**
 * 辨識結果的後處理：簡體 → 台灣正體（含台灣用語，例如「软件→軟體」「鼠标→滑鼠」「内存→記憶體」）。
 *
 * 用 opencc4j 的台灣轉換（Apache-2.0，純 Java）。要在背景執行緒呼叫：第一次會載入詞典。
 */
object TaiwanConverter {

    /**
     * opencc4j 照教育部標準把「台」寫成「臺」，但日常用法多半寫「台」。
     * 這裡改回常見寫法；使用者要教育部寫法就把這張表清空。
     */
    private val overrides = listOf(
        "臺灣" to "台灣",
        "臺北" to "台北",
        "臺中" to "台中",
        "臺南" to "台南",
        "臺東" to "台東",
        "臺語" to "台語",
        "臺幣" to "台幣",
    )

    fun convert(text: String): String {
        if (text.isEmpty()) return text
        var result = ZhTwConverterUtil.toTraditional(text)
        for ((from, to) in overrides) {
            if (result.contains(from)) result = result.replace(from, to)
        }
        return result
    }
}

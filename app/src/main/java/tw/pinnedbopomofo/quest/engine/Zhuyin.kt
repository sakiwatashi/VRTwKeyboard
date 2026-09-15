package tw.pinnedbopomofo.quest.engine

object Zhuyin {
    val INITIALS = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ".map { it.toString() }.toSet()
    val MEDIALS = setOf("ㄧ", "ㄨ", "ㄩ")
    val RIMES = "ㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ".map { it.toString() }.toSet()

    /** 一聲在詞庫裡寫成「ˉ」。鍵盤上沒有一聲鍵：不打聲調就比對所有聲調。 */
    val TONES = setOf("ˉ", "ˊ", "ˇ", "ˋ", "˙")

    fun hasTone(syllable: String) = syllable.isNotEmpty() && syllable.last().toString() in TONES

    /**
     * 同一個音節裡符號依聲母、介音、韻母的順序出現。
     * 新按的符號順位在音節最後一個符號之後，才接得上同一個音節；否則是下一個字的開頭。
     */
    fun canExtend(syllable: String, symbol: String): Boolean {
        if (syllable.isEmpty() || hasTone(syllable)) return false
        return order(symbol) > order(syllable.last().toString())
    }

    private fun order(symbol: String) = when (symbol) {
        in INITIALS -> 0
        in MEDIALS -> 1
        in RIMES -> 2
        else -> 3
    }
}

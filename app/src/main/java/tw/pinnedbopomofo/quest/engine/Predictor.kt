package tw.pinnedbopomofo.quest.engine

/**
 * 聯想詞：送出一個字之後，列出最常接在它後面的字或詞。
 * 主要排序來自 Rime Essay 高頻詞，再用教育部台灣詞頻修正簡轉繁語料的偏差。
 */
class Predictor private constructor(private val next: Map<String, List<String>>) {

    fun after(text: String): List<String> {
        val last = text.characters().lastOrNull() ?: return emptyList()
        return next[last].orEmpty()
    }

    companion object {
        private const val LIMIT = 15

        fun load(open: DataOpener): Predictor {
            val essay = readPhrases(open, "high_frequency_phrases.json")
            val taiwan = readPhrases(open, "taiwan_frequency.json")
            val taiwanWords = taiwan.mapTo(HashSet()) { it.first }

            val result = HashMap<String, MutableList<String>>()
            fun add(phrase: String) {
                val characters = phrase.characters()
                val list = result.getOrPut(characters[0]) { mutableListOf() }
                val rest = characters.drop(1).joinToString("")
                if (list.size < LIMIT && rest !in list) list += rest
            }

            // 三字以上的語料詞有不少是台灣少用的說法（市場份額、市盈率），要台灣詞表也有才收。
            // 兩字詞不過濾：「一個」「我的」這類最常用的搭配，1996 年的台灣詞表裡反而沒有。
            for ((phrase, _) in essay.sortedByDescending { it.second }) {
                if (taiwanWords.isEmpty() || phrase.characterCount() == 2 || phrase in taiwanWords) {
                    add(phrase)
                }
            }
            // 語料簡轉繁時「台」幾乎都成了「臺」，「台灣」「台北」只有台灣詞表裡有
            for ((phrase, _) in taiwan.sortedByDescending { it.second }) add(phrase)

            return Predictor(result)
        }

        /** 兩份資料格式相同：buckets → 詞長 → 首字 → [[詞, 權重], ...]。只取二到四字詞。 */
        private fun readPhrases(open: DataOpener, name: String): List<Pair<String, Int>> {
            val buckets = readJson(open, name)?.optJSONObject("buckets") ?: return emptyList()
            val phrases = ArrayList<Pair<String, Int>>()
            for (length in buckets.keys()) {
                val bucket = buckets.optJSONObject(length) ?: continue
                for (key in bucket.keys()) {
                    val rows = bucket.optJSONArray(key) ?: continue
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONArray(i) ?: continue
                        val phrase = row.optString(0)
                        if (phrase.characterCount() in 2..4) phrases += phrase to row.optInt(1)
                    }
                }
            }
            return phrases
        }
    }
}

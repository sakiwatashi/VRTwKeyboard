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

        /** [timer] 只用來記各段時間（讀檔／解析／取出詞／排序建表），不影響結果。 */
        fun load(open: DataOpener, timer: LoadTimer? = null): Predictor {
            val essay = readPhrases(open, "high_frequency_phrases.json", timer)
            val taiwan = readPhrases(open, "taiwan_frequency.json", timer)

            val result = HashMap<String, MutableList<String>>()
            timer.timed("predictor.build") {
                val taiwanWords = taiwan.mapTo(HashSet()) { it.first }
                // 不用 characters()：三十萬個詞各拆一次字元清單很貴，直接取首字與其餘部分
                fun add(phrase: String) {
                    val headLength = Character.charCount(phrase.codePointAt(0))
                    val list = result.getOrPut(phrase.substring(0, headLength)) { mutableListOf() }
                    if (list.size >= LIMIT) return
                    val rest = phrase.substring(headLength)
                    if (rest !in list) list += rest
                }

                // 三字以上的語料詞有不少是台灣少用的說法（市場份額、市盈率），要台灣詞表也有才收。
                // 兩字詞不過濾：「一個」「我的」這類最常用的搭配，1996 年的台灣詞表裡反而沒有。
                val essayKept = essay.filter { (phrase, _) ->
                    taiwanWords.isEmpty() || phrase.characterCount() == 2 || phrase in taiwanWords
                }
                // 先依首字分桶、桶內再由高到低排序：每個字只留 LIMIT 個，桶內順序等同全域排序，
                // 但不必把三十萬筆一起排（頭盔上原本要 1.5 秒，2026-09-16 實測）。
                for (bucket in byFirstCharacter(essayKept)) {
                    for ((phrase, _) in bucket.sortedByDescending { it.second }) add(phrase)
                }
                // 語料簡轉繁時「台」幾乎都成了「臺」，「台灣」「台北」只有台灣詞表裡有
                for (bucket in byFirstCharacter(taiwan)) {
                    for ((phrase, _) in bucket.sortedByDescending { it.second }) add(phrase)
                }
            }

            return Predictor(result)
        }

        /** 依首字分桶；桶內保持原本的先後，排序後就等同全域排序限制在這個字上。 */
        private fun byFirstCharacter(phrases: List<Pair<String, Int>>): Collection<List<Pair<String, Int>>> {
            val buckets = LinkedHashMap<Int, MutableList<Pair<String, Int>>>()
            for (entry in phrases) buckets.getOrPut(entry.first.codePointAt(0)) { ArrayList() } += entry
            return buckets.values
        }

        /** 兩份資料格式相同：buckets → 詞長 → 首字 → [[詞, 權重], ...]。只取二到四字詞。 */
        private fun readPhrases(open: DataOpener, name: String, timer: LoadTimer?): List<Pair<String, Int>> {
            val buckets = readJson(open, name, timer, "predictor")?.optJSONObject("buckets") ?: return emptyList()
            val phrases = ArrayList<Pair<String, Int>>()
            timer.timed("predictor.collect") {
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
            }
            return phrases
        }
    }
}

package tw.pinnedbopomofo.quest.engine

/**
 * 依注音讀音查詞，移植自 pime-bopomofo-core 的 reading_phrase_lexicon.py。
 * 完整讀音以空白分隔音節、每個音節帶聲調（一聲寫成「ˉ」），例如「ㄓㄜˋ ㄗㄨㄛˋ」。
 *
 * 手機式輸入另外允許不完整的音節：省略聲調（ㄉㄚ）或只打開頭（ㄉ）。
 * 這時比對所有符合的完整讀音，同一個詞取最高的權重。
 */
class Lexicon private constructor(
    private val entries: Map<String, List<Row>>,
    private val extra: Map<String, Map<String, Int>>,
    private val taiwan: Map<String, Map<String, Int>>,
    private val polyphones: Map<String, Map<String, Int>>,
    private val demoted: Set<String>,
) {
    private class Row(val phrase: String, val weight: Int)

    /** 排好序的清單與查權重的表必須是同一組數字，所以一起算、一起快取。 */
    private class Cached(val rows: List<Row>, val table: Map<String, Int>)

    private val cache = HashMap<String, Cached>()
    private val patternCache = HashMap<String, Cached>()

    /** 各音節的第一個符號 → 完整讀音。不完整的音節至少打了第一個符號，先用它縮小範圍。 */
    private val byLeadingSymbols: Map<String, List<String>> = (entries.keys + extra.keys)
        .filter { key -> key.split(' ').all { it.isNotEmpty() && it.first().toString() !in Zhuyin.TONES } }
        .groupBy { leadingSymbols(it.split(' ')) }

    fun candidates(readings: List<String>, limit: Int = 20): List<String> {
        if (readings.isEmpty() || limit <= 0) return emptyList()
        val width = readings.size
        val results = ArrayList<String>()
        for (row in lookup(readings).rows) {
            if (row.phrase.characterCount() == width && row.phrase !in results) {
                results += row.phrase
                if (results.size >= limit) break
            }
        }
        return results
    }

    fun weight(readings: List<String>, phrase: String): Int {
        if (phrase.characterCount() != readings.size) return 0
        return lookup(readings).table[phrase] ?: 0
    }

    private fun lookup(readings: List<String>): Cached =
        if (readings.all(Zhuyin::hasTone)) cached(key(readings)) else matched(readings)

    private fun key(readings: List<String>) = readings.joinToString(" ")

    private fun leadingSymbols(syllables: List<String>) = syllables.joinToString(" ") { it.first().toString() }

    /** 帶聲調就要完全相同；沒帶聲調時，讀音去掉聲調後以打的符號開頭即可。 */
    private fun matches(syllable: String, pattern: String): Boolean {
        if (Zhuyin.hasTone(pattern)) return syllable == pattern
        val base = if (Zhuyin.hasTone(syllable)) syllable.dropLast(1) else syllable
        return base.startsWith(pattern)
    }

    private fun matched(patterns: List<String>): Cached {
        if (patterns.any { it.isEmpty() }) return EMPTY
        val cacheKey = key(patterns)
        patternCache[cacheKey]?.let { return it }

        val best = HashMap<String, Int>()
        for (reading in byLeadingSymbols[leadingSymbols(patterns)].orEmpty()) {
            val syllables = reading.split(' ')
            if (syllables.indices.any { !matches(syllables[it], patterns[it]) }) continue
            // 這裡不用 cached()：一次比對可能經過上千個讀音，會把讀音快取整個洗掉
            for (row in build(reading).rows) {
                val current = best[row.phrase]
                if (current == null || row.weight > current) best[row.phrase] = row.weight
            }
        }
        val rows = best.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { Row(it.key, it.value) }

        if (patternCache.size >= ROW_CACHE_LIMIT) patternCache.clear()
        return Cached(rows, best).also { patternCache[cacheKey] = it }
    }

    private fun adjusted(reading: String, phrase: String, raw: Int): Int {
        var weight = maxOf(0, raw)
        if (phrase.characterCount() == 1) {
            polyphones[phrase]?.get(reading)?.let { weight = it }
        }
        // 台灣寫法的表是按 (讀音, 詞) 指定的，比破音字權重更明確，所以放在後面蓋過去
        taiwan[reading]?.get(phrase)?.let { weight = it }
        if (demoted.isNotEmpty() && phrase.characters().any { it in demoted }) {
            // 保底 1：降權是排到後面，不是從詞庫刪掉
            return maxOf(1, weight / VARIANT_DEMOTION_DIVISOR)
        }
        return weight
    }

    private fun cached(key: String): Cached {
        cache[key]?.let { return it }
        val built = build(key)
        if (cache.size >= ROW_CACHE_LIMIT) cache.clear()
        cache[key] = built
        return built
    }

    private fun build(key: String): Cached {
        val rows = ArrayList<Row>()
        var reorder = false
        for (row in entries[key].orEmpty()) {
            val weight = adjusted(key, row.phrase, row.weight)
            if (weight != row.weight) reorder = true
            rows += Row(row.phrase, weight)
        }
        val known = rows.mapTo(HashSet()) { it.phrase }
        for ((phrase, weight) in extra[key].orEmpty()) {
            if (phrase !in known) {
                rows += Row(phrase, weight)
                reorder = true
            }
        }
        val sorted = if (reorder) rows.sortedByDescending { it.weight } else rows

        val table = HashMap<String, Int>()
        for (row in sorted) table.putIfAbsent(row.phrase, row.weight)
        return Cached(sorted, table)
    }

    companion object {
        /** 簡轉繁留下的異體字繼承了常用字的詞頻，高出兩到三個數量級；除以這個數放回罕用的位置。 */
        private const val VARIANT_DEMOTION_DIVISOR = 1000
        private const val ROW_CACHE_LIMIT = 512
        private val EMPTY = Cached(emptyList(), emptyMap())

        fun load(open: DataOpener): Lexicon {
            val entries = HashMap<String, List<Row>>()
            readJson(open, "reading_phrases.json.gz")?.optJSONObject("entries")?.let { json ->
                for (key in json.keys()) {
                    val rows = json.optJSONArray(key) ?: continue
                    val list = ArrayList<Row>(rows.length())
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONArray(i) ?: continue
                        if (row.length() != 2) continue
                        val phrase = row.opt(0) as? String ?: continue
                        val weight = (row.opt(1) as? Number)?.toInt() ?: continue
                        list += Row(phrase, weight)
                    }
                    entries[key] = list
                }
            }

            val extra = loadWeights(open, "extra_phrases.json")
            // 變調唸法補在同一個備援表；手工列的那份優先
            for ((key, words) in loadWeights(open, "tone_sandhi.json")) {
                val target = extra.getOrPut(key) { HashMap() }
                for ((phrase, weight) in words) target.putIfAbsent(phrase, weight)
            }

            return Lexicon(
                entries = entries,
                extra = extra,
                taiwan = loadWeights(open, "taiwan_preferred.json"),
                polyphones = loadPolyphones(open),
                demoted = loadDemotions(open),
            )
        }

        private fun loadWeights(open: DataOpener, name: String): MutableMap<String, MutableMap<String, Int>> {
            val result = HashMap<String, MutableMap<String, Int>>()
            val json = readJson(open, name)?.optJSONObject("entries") ?: return result
            for (key in json.keys()) {
                val words = json.optJSONObject(key) ?: continue
                result[key] = words.keys().asSequence().associateWithTo(HashMap()) { words.optInt(it) }
            }
            return result
        }

        private fun loadPolyphones(open: DataOpener): Map<String, Map<String, Int>> {
            val json = readJson(open, "polyphone_weights.json")?.optJSONObject("characters")
                ?: return emptyMap()
            val result = HashMap<String, Map<String, Int>>()
            for (character in json.keys()) {
                if (character.characterCount() != 1) continue
                val readings = json.optJSONObject(character) ?: continue
                result[character] = readings.keys().asSequence().associateWith { readings.optInt(it) }
            }
            return result
        }

        private fun loadDemotions(open: DataOpener): Set<String> {
            val json = readJson(open, "variant_demotions.json")?.optJSONObject("characters")
                ?: return emptySet()
            return json.keys().asSequence().filter { it.characterCount() == 1 }.toSet()
        }
    }
}

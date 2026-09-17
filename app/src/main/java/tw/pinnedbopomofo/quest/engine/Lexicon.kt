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
    private val byLeadingSymbols: Map<String, List<String>> = buildIndex()

    /**
     * 掃過全部讀音建索引。不切字串：14.7 萬個讀音各 split 一次，在頭盔上要 2.5 秒、佔冷啟動三成
     * （2026-09-16 實測）。改成逐字元掃描，結果與原本的 split + groupBy 相同。
     */
    private fun buildIndex(): Map<String, List<String>> {
        val groups = HashMap<String, MutableList<String>>()
        val seen = HashSet<String>(entries.size + extra.size)
        fun add(reading: String) {
            if (!seen.add(reading)) return
            val key = leadingSymbolsOf(reading) ?: return
            groups.getOrPut(key) { ArrayList() } += reading
        }
        for (reading in entries.keys) add(reading)
        for (reading in extra.keys) add(reading)
        return groups
    }

    /** 取各音節的第一個符號；有空音節或音節以聲調開頭就回傳 null（這種讀音本來就不進索引）。 */
    private fun leadingSymbolsOf(reading: String): String? {
        val leading = StringBuilder()
        var syllableStart = true
        for (character in reading) {
            if (character == ' ') {
                if (syllableStart) return null
                syllableStart = true
                continue
            }
            if (syllableStart) {
                if (character in TONE_CHARACTERS) return null
                if (leading.isNotEmpty()) leading.append(' ')
                leading.append(character)
                syllableStart = false
            }
        }
        return if (syllableStart) null else leading.toString()
    }

    /** 單字 → 可能的讀音，權重高的在前。同音修正用，第一次呼叫才建表。 */
    private val characterReadings: Map<String, List<String>> by lazy {
        val weights = HashMap<String, MutableList<Pair<String, Int>>>()
        for ((reading, rows) in entries) {
            if (reading.contains(' ')) continue
            for (row in rows) {
                if (row.phrase.characterCount() != 1) continue
                weights.getOrPut(row.phrase) { ArrayList() } += reading to row.weight
            }
        }
        weights.mapValues { (_, list) ->
            list.sortedByDescending { it.second }.map { it.first }.distinct()
        }
    }

    fun readingsOf(character: String): List<String> = characterReadings[character].orEmpty()

    /** 這個詞在詞庫裡查得到嗎（任一讀音）。 */
    fun knows(phrase: String, readings: List<String>): Boolean =
        weight(readings, phrase) > 0

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
        /** 聲調符號；用字元比對避免每個音節都產生一個字串。 */
        private val TONE_CHARACTERS = Zhuyin.TONES.joinToString("")
        private val EMPTY = Cached(emptyList(), emptyMap())

        /** [timer] 只用來記各段時間（主詞庫的讀檔／解析／建表、小檔、索引），不影響結果。 */
        fun load(open: DataOpener, timer: LoadTimer? = null): Lexicon {
            val entries = HashMap<String, List<Row>>()
            val phrases = readJson(open, "reading_phrases.json.gz", timer, "phrases")?.optJSONObject("entries")
            timer.timed("phrases.build") {
                phrases?.let { json ->
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
            }

            val extra = loadWeights(open, "extra_phrases.json", timer)
            // 變調唸法補在同一個備援表；手工列的那份優先
            for ((key, words) in loadWeights(open, "tone_sandhi.json", timer)) {
                timer.timed("small.build") {
                    val target = extra.getOrPut(key) { HashMap() }
                    for ((phrase, weight) in words) target.putIfAbsent(phrase, weight)
                }
            }
            val taiwan = loadWeights(open, "taiwan_preferred.json", timer)
            val polyphones = loadPolyphones(open, timer)
            val demoted = loadDemotions(open, timer)

            // 建構時會掃過全部讀音，建「各音節第一個符號」索引
            return timer.timed("index") {
                Lexicon(entries = entries, extra = extra, taiwan = taiwan, polyphones = polyphones, demoted = demoted)
            }
        }

        private fun loadWeights(open: DataOpener, name: String, timer: LoadTimer?): MutableMap<String, MutableMap<String, Int>> {
            val result = HashMap<String, MutableMap<String, Int>>()
            val json = readJson(open, name, timer, "small")?.optJSONObject("entries") ?: return result
            timer.timed("small.build") {
                for (key in json.keys()) {
                    val words = json.optJSONObject(key) ?: continue
                    result[key] = words.keys().asSequence().associateWithTo(HashMap()) { words.optInt(it) }
                }
            }
            return result
        }

        private fun loadPolyphones(open: DataOpener, timer: LoadTimer?): Map<String, Map<String, Int>> {
            val json = readJson(open, "polyphone_weights.json", timer, "small")?.optJSONObject("characters")
                ?: return emptyMap()
            val result = HashMap<String, Map<String, Int>>()
            timer.timed("small.build") {
                for (character in json.keys()) {
                    if (character.characterCount() != 1) continue
                    val readings = json.optJSONObject(character) ?: continue
                    result[character] = readings.keys().asSequence().associateWith { readings.optInt(it) }
                }
            }
            return result
        }

        private fun loadDemotions(open: DataOpener, timer: LoadTimer?): Set<String> {
            val json = readJson(open, "variant_demotions.json", timer, "small")?.optJSONObject("characters")
                ?: return emptySet()
            return timer.timed("small.build") {
                json.keys().asSequence().filter { it.characterCount() == 1 }.toSet()
            }
        }
    }
}

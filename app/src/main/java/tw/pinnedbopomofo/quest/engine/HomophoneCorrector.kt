package tw.pinnedbopomofo.quest.engine

/**
 * 語音辨識輸出的同音修正。
 *
 * 辨識模型是中國語料訓練的，台灣說法不在它的詞彙裡：說「滑鼠」會辨識成同音的「華屬」
 * （2026-09-16 頭盔實測）。作法是拿辨識出來的詞去查我們自己的詞庫：
 * 詞庫裡查不到這個詞，但它的讀音對得上某個存在的詞，就換過去。
 *
 * 只動中文；英文、數字、標點原樣保留。查不到就不動，寧可不改也不要改錯。
 */
class HomophoneCorrector(private val lexicon: Lexicon) {

    fun correct(text: String): String {
        if (text.isEmpty()) return text
        val characters = text.characters()
        val result = StringBuilder(text.length)
        var index = 0
        while (index < characters.size) {
            // 詞庫認得的詞原封不動，並整個跳過：不然窗格會從詞中間切開（「有滑」把「滑鼠」切掉）
            val known = knownLength(characters, index)
            if (known != null) {
                for (offset in 0 until known) result.append(characters[index + offset])
                index += known
                continue
            }
            val replaced = replacementAt(characters, index)
            if (replaced == null) {
                result.append(characters[index])
                index += 1
            } else {
                result.append(replaced.first)
                index += replaced.second
            }
        }
        return result.toString()
    }

    /** 從 [start] 開始、詞庫認得的最長的詞有幾個字；沒有就回 null。 */
    private fun knownLength(characters: List<String>, start: Int): Int? {
        val maxWidth = minOf(MAX_PHRASE, characters.size - start)
        for (width in maxWidth downTo MIN_PHRASE) {
            val span = characters.subList(start, start + width)
            if (span.any { it.readings().isEmpty() }) continue
            val phrase = span.joinToString("")
            for (reading in readingCombinations(span)) {
                if (lexicon.knows(phrase, reading)) return width
            }
        }
        return null
    }

    /** 從 [start] 開始找可以換掉的一段；回傳（換成的詞, 吃掉幾個字）。 */
    private fun replacementAt(characters: List<String>, start: Int): Pair<String, Int>? {
        val maxWidth = minOf(MAX_PHRASE, characters.size - start)
        // 長的詞優先：整段對得上比逐字換更可靠
        for (width in maxWidth downTo MIN_PHRASE) {
            // 窗格不能切進後面那個已知的詞
            if ((1 until width).any { knownLength(characters, start + it) != null }) continue
            val span = characters.subList(start, start + width)
            if (span.any { it.readings().isEmpty() }) continue
            val phrase = span.joinToString("")
            var best: String? = null
            var bestWeight = 0
            for (reading in readingCombinations(span)) {
                val candidate = lexicon.candidates(reading, 1).firstOrNull() ?: continue
                if (candidate == phrase) return null
                val weight = lexicon.weight(reading, candidate)
                if (weight > bestWeight) {
                    best = candidate
                    bestWeight = weight
                }
            }
            if (best != null && bestWeight >= MIN_WEIGHT) return best to width
        }
        return null
    }

    private fun String.readings(): List<String> = lexicon.readingsOf(this).take(MAX_READINGS)

    /** 每個字取前幾個讀音，組出所有可能的整段讀音；數量有上限，避免長詞爆開。 */
    private fun readingCombinations(span: List<String>): List<List<String>> {
        var combinations = listOf(emptyList<String>())
        for (character in span) {
            val readings = character.readings()
            if (readings.isEmpty()) return emptyList()
            val next = ArrayList<List<String>>(minOf(MAX_COMBINATIONS, combinations.size * readings.size))
            for (prefix in combinations) {
                for (reading in readings) {
                    if (next.size >= MAX_COMBINATIONS) return next
                    next += prefix + reading
                }
            }
            combinations = next
        }
        return combinations
    }

    private companion object {
        const val MIN_PHRASE = 2
        const val MAX_PHRASE = 4
        /** 每個字最多試幾個讀音（破音字）。 */
        const val MAX_READINGS = 3
        const val MAX_COMBINATIONS = 24
        /** 換過去的詞至少要有這個權重，太冷僻的就不換。 */
        const val MIN_WEIGHT = 100
    }
}

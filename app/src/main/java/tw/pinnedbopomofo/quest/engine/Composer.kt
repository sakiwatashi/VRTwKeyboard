package tw.pinnedbopomofo.quest.engine

/** width＝這個候選吃掉「還沒選的第一段」開頭幾個音節。 */
data class Candidate(val text: String, val width: Int)

/**
 * 手機式連打：注音一個個按進來，自動切出音節，每按一鍵都重算整句。
 * 音節可以不完整：省略聲調（ㄉㄚ）或只打聲母（ㄉ），查詞時比對所有符合的讀音。
 *
 * 選字是一段一段來的：點候選字只固定開頭那一段，候選換成下一段，全部選完才算完成。
 * 固定下來的字在重算整句時不會被改掉。
 */
class Composer(private val lexicon: Lexicon) {
    /** 每個音節已經按下的注音；沒有聲調的音節還能接著打。 */
    private val syllables = mutableListOf<String>()
    private val chosen = mutableListOf<String>()

    /** 開頭已經選定的音節數。 */
    private var fixedCount = 0

    val isEmpty get() = syllables.isEmpty()
    val size get() = syllables.size

    /** 目前整句的文字：選定的字加上引擎對其餘部分的猜測。 */
    fun text() = chosen.joinToString("")

    /** 使用者按下的全部注音，原樣照順序。 */
    fun typed() = syllables.joinToString("")

    /** 已經選定的字。 */
    fun fixedText() = chosen.subList(0, fixedCount).joinToString("")

    /** 還沒選的部分，引擎目前的猜測。 */
    fun guessText() = chosen.subList(fixedCount, size).joinToString("")

    /** 還沒選的部分按下的注音。 */
    fun pendingTyped() = syllables.subList(fixedCount, size).joinToString("")

    /** 按一個注音符號或聲調。回傳 false 表示這個符號在這裡組不出任何讀音，沒有收下。 */
    fun type(symbol: String): Boolean {
        // 最後一個音節已經選定的話，不能再往裡面加符號或改聲調，只能開始下一個字
        val last = if (fixedCount < size) syllables.lastOrNull() else null
        if (symbol in Zhuyin.TONES) {
            if (last == null) return false
            // 已經有聲調就換成新按的聲調
            val base = if (Zhuyin.hasTone(last)) last.dropLast(1) else last
            return replaceLast(base + symbol)
        }
        if (last != null && Zhuyin.canExtend(last, symbol) && replaceLast(last + symbol)) return true
        return append(symbol)
    }

    /** 刪掉最後按的一個注音；最後面都是選定的字時，把最後一個選定的字變回注音。 */
    fun backspace() {
        if (isEmpty) return
        if (fixedCount == size) {
            fixedCount -= 1
            redecode()
            return
        }
        val last = syllables.last()
        if (last.length == 1) {
            syllables.removeAt(syllables.lastIndex)
            chosen.removeAt(chosen.lastIndex)
        } else {
            val shorter = last.dropLast(1)
            syllables[syllables.lastIndex] = shorter
            chosen[chosen.lastIndex] = firstCandidate(shorter) ?: shorter
        }
        redecode()
    }

    fun clear() {
        syllables.clear()
        chosen.clear()
        fixedCount = 0
    }

    /** 還沒選的那一段：第一個是整段猜測，接著由長到短列出從這一段開頭算起的詞。 */
    fun candidates(limit: Int = 60): List<Candidate> {
        val remaining = size - fixedCount
        if (remaining <= 0) return emptyList()
        val result = LinkedHashMap<String, Candidate>()
        val guess = guessText()
        result[guess] = Candidate(guess, remaining)
        for (width in minOf(remaining, MAX_PHRASE_LENGTH) downTo 1) {
            for (phrase in lexicon.candidates(syllables.subList(fixedCount, fixedCount + width), limit)) {
                if (result.size >= limit) return result.values.toList()
                result.putIfAbsent(phrase, Candidate(phrase, width))
            }
        }
        return result.values.toList()
    }

    /** 固定還沒選的那一段的開頭。回傳 true 表示整句都選完了，可以送出。 */
    fun select(candidate: Candidate): Boolean {
        val characters = candidate.text.characters()
        val width = minOf(candidate.width, size - fixedCount, characters.size)
        for (offset in 0 until width) chosen[fixedCount + offset] = characters[offset]
        fixedCount += width
        redecode()
        return fixedCount == size
    }

    private fun replaceLast(pattern: String): Boolean {
        val first = firstCandidate(pattern) ?: return false
        syllables[syllables.lastIndex] = pattern
        chosen[chosen.lastIndex] = first
        redecode()
        return true
    }

    private fun append(pattern: String): Boolean {
        val first = firstCandidate(pattern) ?: return false
        syllables += pattern
        chosen += first
        redecode()
        return true
    }

    private fun firstCandidate(pattern: String) = lexicon.candidates(listOf(pattern), 1).firstOrNull()

    private fun redecode() {
        if (isEmpty) return
        // 選定的字是使用者明確的選擇：保護起來，重算時不會被較常見的詞吃掉
        val spans = PhraseDecoder.decode(syllables, chosen, List(size) { it < fixedCount }, lexicon)
        for (span in spans) {
            span.text.characters().forEachIndexed { offset, character ->
                chosen[span.start + offset] = character
            }
        }
    }

    companion object {
        private const val MAX_PHRASE_LENGTH = 12
    }
}

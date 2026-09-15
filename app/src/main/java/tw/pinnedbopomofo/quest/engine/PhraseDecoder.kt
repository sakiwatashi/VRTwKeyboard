package tw.pinnedbopomofo.quest.engine

import kotlin.math.ln1p

data class DecodedSpan(val start: Int, val end: Int, val text: String)

/**
 * 整句詞網格，移植自 pime-bopomofo-core 的 phrase_decoder.py。
 * 個人詞庫還沒移植，所以少了 personal 那一層；其餘比較規則相同。
 */
object PhraseDecoder {
    private class Path(
        val covered: Int,
        val compaction: Int,
        val frequency: Double,
        val spans: List<DecodedSpan>,
    ) {
        /** 先比讀音涵蓋，再比長詞比例，最後比詞頻。相等時保留先到的，跟 Python 的嚴格 > 一致。 */
        fun beats(other: Path?): Boolean {
            if (other == null) return true
            if (covered != other.covered) return covered > other.covered
            if (compaction != other.compaction) return compaction > other.compaction
            return frequency > other.frequency
        }
    }

    fun decode(
        readings: List<String>,
        currentText: List<String>,
        protected: List<Boolean>,
        lexicon: Lexicon,
        maxPhraseLength: Int = 12,
    ): List<DecodedSpan> {
        val count = readings.size
        require(count == currentText.size && count == protected.size) {
            "readings, text, and protection mask must align"
        }
        if (count == 0) return emptyList()

        val paths = arrayOfNulls<Path>(count + 1)
        paths[0] = Path(0, 0, 0.0, emptyList())
        for (start in 0 until count) {
            val path = paths[start] ?: continue

            // 單字永遠是退路，所以不需要整句剛好存在於詞庫
            val single = Path(
                path.covered, path.compaction, path.frequency,
                path.spans + DecodedSpan(start, start + 1, currentText[start]),
            )
            if (single.beats(paths[start + 1])) paths[start + 1] = single
            // 鎖定的字是使用者明確的選擇，形成硬邊界
            if (protected[start]) continue

            val maximum = minOf(maxPhraseLength, count - start)
            for (width in 1..maximum) {
                val end = start + width
                if (protected.subList(start, end).any { it }) break
                val spanReadings = readings.subList(start, end)
                for (phrase in lexicon.candidates(spanReadings)) {
                    if (phrase.characterCount() != width) continue
                    val weight = maxOf(0, lexicon.weight(spanReadings, phrase))
                    val candidate = Path(
                        path.covered + width,
                        path.compaction + maxOf(0, width - 1),
                        path.frequency + ln1p(weight.toDouble()),
                        path.spans + DecodedSpan(start, end, phrase),
                    )
                    if (candidate.beats(paths[end])) paths[end] = candidate
                }
            }
        }
        return paths[count]?.spans.orEmpty()
    }
}

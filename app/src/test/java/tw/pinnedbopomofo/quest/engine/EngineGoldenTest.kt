package tw.pinnedbopomofo.quest.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 黃金基準：把整份詞庫查出來的結果壓成雜湊值釘住。
 * 之後為了加速而改寫索引、排序等寫法時，只要選字結果有一點不同，這裡就會變紅。
 */
class EngineGoldenTest {

    @Test
    fun `不完整音節與整句查詢的結果沒有變`() {
        val lexicon = engine.lexicon
        val digest = MessageDigest.getInstance("SHA-256")
        // 只打聲母、打到一半、完整讀音，各種長度都涵蓋
        for (symbol in SYMBOLS) {
            digest.feed("$symbol|" + lexicon.candidates(listOf(symbol), 20).joinToString(","))
        }
        for (first in SYMBOLS) {
            for (second in SYMBOLS) {
                val reading = listOf(first, second)
                digest.feed("$first $second|" + lexicon.candidates(reading, 10).joinToString(","))
            }
        }
        for (reading in FULL_READINGS) {
            val syllables = reading.split(' ')
            digest.feed("$reading|" + lexicon.candidates(syllables, 20).joinToString(",") +
                "|" + syllables.let { lexicon.weight(it, lexicon.candidates(it, 1).firstOrNull() ?: "") })
        }
        assertEquals("查詢結果變了", "0b7d9310c8bfe6ee95cc25c5dce486659a952c6712a1b056497db6841b5725f7", digest.hex())
    }

    @Test
    fun `聯想詞的內容沒有變`() {
        val predictor = engine.predictor
        val digest = MessageDigest.getInstance("SHA-256")
        for (character in COMMON_CHARACTERS.map { it.toString() }) {
            digest.feed("$character|" + predictor.after(character).joinToString(","))
        }
        assertEquals("聯想詞變了", "bda0c6c38a19987988d8f9c07c9617f8e979e9c88e6ecc574828edad1fd0e45b", digest.hex())
    }

    private fun MessageDigest.feed(text: String) = update(text.toByteArray(Charsets.UTF_8))

    private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }

    companion object {
        /** 大千排列上全部的注音符號（不含聲調）。 */
        private val SYMBOLS = ("ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ" +
            "ㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ").map { it.toString() }

        private val FULL_READINGS = listOf(
            "ㄓㄜˋ ㄗㄨㄛˋ", "ㄓㄜˋ ㄗㄨㄛˋ ㄔㄥˊ ㄕˋ", "ㄉㄚˋ ㄐㄧㄚˉ", "ㄊㄞˊ ㄨㄢˉ",
            "ㄐㄧㄣˉ ㄊㄧㄢˉ", "ㄒㄧㄤˇ ㄓˉ ㄉㄠˋ", "ㄍㄨㄥˉ ㄩㄢˊ", "ㄙㄢˋ ㄅㄨˋ",
            "ㄕㄨˉ ㄖㄨˋ ㄈㄚˇ", "ㄧㄣˉ", "ㄨㄛˇ ㄇㄣˊ", "ㄋㄧˇ ㄏㄠˇ",
        )

        private const val COMMON_CHARACTERS =
            "我你他她它們的了是在不有個人這那上下大小中為來去說到就要會可以時年月日台灣國語文字輸入法鍵盤手機電腦網路今天明天昨天早上中午晚安謝謝對不起請問什麼怎麼哪裡為什麼可能應該一定不要沒有很好非常真的假的東西地方時候問題方法開始結束"

        private val engine by lazy {
            val data = System.getenv("ENGINE_TEST_DATA")?.let(::File)
                ?: TestLexicon.directory()
            check(data.isDirectory) { "找不到詞庫資料：${data.canonicalPath}" }
            Engine.load(open = { name -> File(data, name).takeIf { it.isFile }?.inputStream() })
        }
    }
}

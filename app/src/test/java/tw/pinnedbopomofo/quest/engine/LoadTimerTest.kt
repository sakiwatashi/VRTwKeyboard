package tw.pinnedbopomofo.quest.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LoadTimerTest {

    @Test
    fun `載入詞庫時每一段都有記到時間`() {
        val timer = LoadTimer()
        Engine.load(opener(), timer)
        // 少了任何一段，頭盔上的細分記錄就看不出慢在哪裡
        assertEquals(
            setOf(
                "phrases.read", "phrases.parse", "phrases.build",
                "small.read", "small.parse", "small.build",
                "index",
                "predictor.read", "predictor.parse", "predictor.collect", "predictor.build",
            ),
            timer.names,
        )
    }

    @Test
    fun `有計時跟沒計時，查到的候選與聯想詞一樣`() {
        val timed = Engine.load(opener(), LoadTimer())
        val plain = Engine.load(opener())
        val readings = listOf(
            listOf("ㄓㄜˋ", "ㄗㄨㄛˋ"),
            listOf("ㄉㄚ", "ㄐㄧㄚ"),
            listOf("ㄉ"),
            listOf("ㄔㄥˊ", "ㄕˋ"),
        )
        for (reading in readings) {
            assertEquals(plain.lexicon.candidates(reading, 60), timed.lexicon.candidates(reading, 60))
        }
        for (text in listOf("我", "台", "今天")) {
            assertEquals(plain.predictor.after(text), timed.predictor.after(text))
        }
    }

    private fun opener(): DataOpener {
        // 跟 EngineTest 一樣：工作目錄是 app 模組，ENGINE_TEST_DATA 可指向刻意拿掉檔案的副本
        val data = System.getenv("ENGINE_TEST_DATA")?.let(::File)
            ?: TestLexicon.directory()
        check(data.isDirectory) { "找不到詞庫資料：${data.canonicalPath}" }
        return { name -> File(data, name).takeIf { it.isFile }?.inputStream() }
    }
}

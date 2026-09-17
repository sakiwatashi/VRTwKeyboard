package tw.pinnedbopomofo.quest

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedLoaderTest {
    /** 背景執行緒與主執行緒都換成手動排隊，測試決定什麼時候跑，才能重現「載入到一半又有人要」。 */
    private val background = ArrayDeque<Runnable>()
    private val main = ArrayDeque<Runnable>()
    private var loads = 0

    private val loader = SharedLoader(
        load = {
            loads += 1
            "詞庫#$loads"
        },
        startLoad = { background += it },
        deliver = { main += it },
    )

    private fun runAll() {
        while (background.isNotEmpty() || main.isNotEmpty()) {
            background.removeFirstOrNull()?.run()
            main.removeFirstOrNull()?.run()
        }
    }

    @Test
    fun `兩個服務幾乎同時要詞庫，只載入一次、拿到同一份`() {
        val got = mutableListOf<String>()
        loader.request { got += "甲:$it" }
        loader.request { got += "乙:$it" }
        runAll()
        assertEquals(1, loads)
        assertEquals(listOf("甲:詞庫#1", "乙:詞庫#1"), got)
    }

    @Test
    fun `載入完之後再要，直接給同一份、不再載入`() {
        loader.request { }
        runAll()
        val got = mutableListOf<String>()
        loader.request { got += it }
        assertEquals(listOf("詞庫#1"), got)
        runAll()
        assertEquals(1, loads)
    }

    @Test
    fun `取消的請求不會被回呼`() {
        val got = mutableListOf<String>()
        val destroyed = loader.request { got += "已結束的服務" }
        loader.request { got += "新的服務" }
        destroyed.cancel()
        runAll()
        assertEquals(listOf("新的服務"), got)
    }

    @Test
    fun `載入到一半第一個服務結束、第二個服務才來要，仍然只載入一次`() {
        val got = mutableListOf<String>()
        val first = loader.request { got += "第一個" }
        background.removeFirst().run()
        first.cancel()
        loader.request { got += "第二個:$it" }
        runAll()
        assertEquals(1, loads)
        assertEquals(listOf("第二個:詞庫#1"), got)
    }
}

package tw.pinnedbopomofo.quest.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 用 pime-bopomofo-core 的真實詞庫跑，確認移植後的選字跟 Windows 版一致。 */
class EngineTest {

    /** 照鍵盤上的順序一個個按注音。 */
    private fun typed(keys: String) = Composer(engine.lexicon).apply {
        for (key in keys) assertTrue("「$key」應該接得上", type(key.toString()))
    }

    private fun Composer.texts() = candidates().map { it.text }

    @Test
    fun `補上的指示詞加量詞讓整句不會變成同音錯字`() {
        // 上游詞庫沒有「這座」，只有「蔗作」；extra_phrases 沒載入就會打成「蔗作城市」
        assertEquals("這座城市", typed("ㄓㄜˋㄗㄨㄛˋㄔㄥˊㄕˋ").text())
    }

    @Test
    fun `詞庫的完整讀音一聲寫成ˉ`() {
        assertTrue("音" in engine.lexicon.candidates(listOf("ㄧㄣˉ")))
    }

    @Test
    fun `不打聲調也有候選`() {
        val candidates = typed("ㄉㄚ").texts()
        assertTrue("候選：$candidates", "打" in candidates && "大" in candidates)
    }

    @Test
    fun `只打聲母就有候選`() {
        val candidates = typed("ㄉ").texts()
        assertTrue("候選：$candidates", "打" in candidates)
    }

    @Test
    fun `連打不帶聲調的注音也能組成詞`() {
        assertEquals("大家", typed("ㄉㄚㄐㄧㄚ").text())
    }

    @Test
    fun `輸入框顯示按下的注音，轉出的整句排在候選第一個`() {
        val composer = typed("ㄉㄚㄐㄧㄚ")
        assertEquals("ㄉㄚㄐㄧㄚ", composer.typed())
        assertEquals(Candidate("大家", 2), composer.candidates().first())
    }

    @Test
    fun `聲母之後再按聲母就是下一個字`() {
        assertEquals(2, typed("ㄉㄉ").size)
    }

    @Test
    fun `接不上的符號另起一個音節`() {
        // 沒有以「ㄅㄩ」開頭的讀音，ㄩ 自己成為下一個字
        assertEquals(2, typed("ㄅㄩ").size)
    }

    @Test
    fun `倒退鍵一次刪一個注音`() {
        val composer = typed("ㄉㄚˇ")
        composer.backspace()
        assertEquals(1, composer.size)
        composer.backspace()
        composer.backspace()
        assertTrue(composer.isEmpty)
    }

    @Test
    fun `簡轉繁留下的異體字排在常用字後面`() {
        // 「為」本身權重不高，排不到第一（第一是「微」）；要驗的是相對位置。
        // 沒有降權時「爲」(211329) 會排第一，遠在「為」(547) 前面。
        val candidates = engine.lexicon.candidates(listOf("ㄨㄟˊ"), 200)
        assertTrue("為" in candidates && "爲" in candidates)
        assertTrue(
            "為應該排在爲前面：$candidates",
            candidates.indexOf("為") < candidates.indexOf("爲"),
        )
    }

    @Test
    fun `選了開頭的詞之後，候選換成下一段，全部選完才算完成`() {
        val composer = typed("ㄓㄜˋㄗㄨㄛˋㄔㄥˊㄕˋ")
        assertEquals(Candidate("這座城市", 4), composer.candidates().first())

        val prefix = composer.candidates().first { it.width == 2 && it.text == "這座" }
        assertFalse(composer.select(prefix))
        assertEquals("這座", composer.fixedText())
        assertEquals("ㄔㄥˊㄕˋ", composer.pendingTyped())
        assertEquals(Candidate("城市", 2), composer.candidates().first())

        assertTrue(composer.select(composer.candidates().first()))
        assertEquals("這座城市", composer.text())
    }

    @Test
    fun `選定的字不會被後面重算改掉`() {
        // 沒有保護的話，接著打 ㄐㄧㄚ 會把「打」重算成更常見的「大家」
        val composer = typed("ㄉㄚ")
        assertTrue(composer.select(composer.candidates().first { it.text == "打" }))
        for (key in "ㄐㄧㄚ") composer.type(key.toString())
        assertTrue("整句：${composer.text()}", composer.text().startsWith("打"))
    }

    @Test
    fun `全刪清掉選定的字和注音，接著打字從頭開始`() {
        val composer = typed("ㄓㄜˋㄗㄨㄛˋㄔㄥˊㄕˋ")
        composer.select(composer.candidates().first { it.width == 2 && it.text == "這座" })
        composer.clear()
        assertTrue(composer.isEmpty)
        assertEquals("", composer.fixedText())

        for (key in "ㄉㄚ") composer.type(key.toString())
        assertEquals("", composer.fixedText())
        assertEquals("ㄉㄚ", composer.pendingTyped())
        assertEquals(1, composer.size)
    }

    @Test
    fun `倒退鍵把選定的字變回注音`() {
        val composer = typed("ㄉㄚㄐㄧㄚ")
        assertFalse(composer.select(composer.candidates().first { it.width == 1 && it.text == "打" }))
        // 先刪掉還沒選的 ㄐㄧㄚ，再按一次就輪到選定的「打」
        repeat(3) { composer.backspace() }
        assertEquals("打", composer.fixedText())
        composer.backspace()
        assertEquals("", composer.fixedText())
        assertEquals("ㄉㄚ", composer.pendingTyped())
    }

    @Test
    fun `聯想詞列出常接在後面的字`() {
        assertTrue("個" in engine.predictor.after("一"))
    }

    @Test
    fun `聯想詞不出現台灣少用的三字以上說法`() {
        val next = engine.predictor.after("市")
        assertTrue("場" in next)
        assertFalse("聯想詞：$next", "場份額" in next)
    }

    @Test
    fun `台灣詞表補上語料缺的台灣`() {
        // 語料簡轉繁時「台」幾乎都成了「臺」，只靠語料的話「台」後面只有「州」「山」
        assertTrue("聯想詞：${engine.predictor.after("台")}", "灣" in engine.predictor.after("台"))
    }

    companion object {
        private val engine by lazy {
            // 單元測試的工作目錄是 app 模組。ENGINE_TEST_DATA 用來指向刻意拿掉檔案的副本，
            // 確認這些測試在資料缺席時真的會失敗。
            val data = System.getenv("ENGINE_TEST_DATA")?.let(::File)
                ?: File("../../pime-bopomofo-core/bopomofo_core/data")
            check(data.isDirectory) { "找不到詞庫資料：${data.canonicalPath}" }
            Engine.load { name -> File(data, name).takeIf { it.isFile }?.inputStream() }
        }
    }
}

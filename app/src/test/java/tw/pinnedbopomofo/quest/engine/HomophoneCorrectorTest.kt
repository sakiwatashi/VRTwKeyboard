package tw.pinnedbopomofo.quest.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 用真實詞庫測同音修正。 */
class HomophoneCorrectorTest {

    @Test
    fun `台灣用語被聽成同音的怪詞，要換回來`() {
        // 2026-09-16 頭盔實測：說「滑鼠」辨識成「華屬」
        assertEquals("滑鼠", corrector.correct("華屬"))
        assertEquals("這個滑鼠很好用", corrector.correct("這個華屬很好用"))
    }

    @Test
    fun `本來就對的句子不要亂改`() {
        for (text in listOf("今天天氣很好", "這個軟體裡面有滑鼠設定", "我在台北工作", "記憶體不足", "螢幕很亮")) {
            assertEquals(text, corrector.correct(text))
        }
    }

    @Test
    fun `英文數字標點原樣保留`() {
        assertEquals("開啟 keyboard 的 layout", corrector.correct("開啟 keyboard 的 layout"))
        assertEquals("第 3 版，測試。", corrector.correct("第 3 版，測試。"))
        assertEquals("", corrector.correct(""))
    }

    @Test
    fun `人名不要被改掉`() {
        assertEquals("陳彥廷", corrector.correct("陳彥廷"))
        assertEquals("我叫李明哲", corrector.correct("我叫李明哲"))
    }

    @Test
    fun `已知限制：不是詞、但讀音剛好對得上某個詞的怪字串會被換掉`() {
        // 規則就是「詞庫查不到、但讀音對得上」就換。這種字串實際上不會從語音辨識出來，
        // 寫在這裡是誠實記錄目前的行為，不是期待它正確。
        assertEquals("洗洗囍", corrector.correct("囍囍囍"))
    }

    companion object {
        private val corrector by lazy {
            val data = System.getenv("ENGINE_TEST_DATA")?.let(::File)
                ?: TestLexicon.directory()
            check(data.isDirectory) { "找不到詞庫資料：${data.canonicalPath}" }
            val lexicon = Lexicon.load(open = { name -> File(data, name).takeIf { it.isFile }?.inputStream() })
            HomophoneCorrector(lexicon)
        }
    }
}

package tw.pinnedbopomofo.quest.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class TaiwanConverterTest {

    @Test
    fun `簡體轉成台灣正體，連用語也換掉`() {
        assertEquals("這個軟體裡面有滑鼠設定", TaiwanConverter.convert("这个软件里面有鼠标设置"))
        assertEquals("記憶體不足", TaiwanConverter.convert("内存不足"))
        assertEquals("選單", TaiwanConverter.convert("菜单"))
        assertEquals("程式錯誤", TaiwanConverter.convert("程序错误"))
    }

    @Test
    fun `一字多形要照上下文選對字`() {
        assertEquals("裡面", TaiwanConverter.convert("里面"))
        assertEquals("頭髮", TaiwanConverter.convert("头发"))
        assertEquals("乾杯", TaiwanConverter.convert("干杯"))
        assertEquals("著急", TaiwanConverter.convert("着急"))
    }

    @Test
    fun `台灣寫成台不是臺`() {
        // opencc4j 照教育部標準給「臺」，我們改回日常寫法
        assertEquals("台灣", TaiwanConverter.convert("台湾"))
        assertEquals("我在台北工作", TaiwanConverter.convert("我在台北工作"))
    }

    @Test
    fun `英文與已經是正體的字不動`() {
        assertEquals("開啟 keyboard 的 layout", TaiwanConverter.convert("打开 keyboard 的 layout"))
        assertEquals("這個軟體", TaiwanConverter.convert("這個軟體"))
        assertEquals("", TaiwanConverter.convert(""))
    }
}

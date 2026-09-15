package tw.pinnedbopomofo.quest

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorPolicyTest {

    @Test
    fun `一般文字欄位沿用使用者的語言`() {
        assertEquals(KeyboardPage.ZHUYIN, EditorPolicy.initialPage(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun `密碼信箱網址欄位切到英文`() {
        val variations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
        for (variation in variations) {
            assertEquals(
                "variation $variation",
                KeyboardPage.ENGLISH,
                EditorPolicy.initialPage(InputType.TYPE_CLASS_TEXT or variation),
            )
        }
    }

    @Test
    fun `數字與電話欄位切到符號頁`() {
        assertEquals(KeyboardPage.SYMBOLS, EditorPolicy.initialPage(InputType.TYPE_CLASS_NUMBER))
        assertEquals(KeyboardPage.SYMBOLS, EditorPolicy.initialPage(InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun `搜尋欄的換行鍵執行搜尋`() {
        val action = EditorPolicy.enterAction(EditorInfo.IME_ACTION_SEARCH)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, action)
        assertEquals("搜尋", EditorPolicy.enterLabel(action))
    }

    @Test
    fun `多行欄位的換行鍵就是換行`() {
        val options = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertNull(EditorPolicy.enterAction(options))
        assertEquals("⏎", EditorPolicy.enterLabel(null))
    }
}

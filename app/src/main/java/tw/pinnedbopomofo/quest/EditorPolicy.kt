package tw.pinnedbopomofo.quest

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class KeyboardPage { ZHUYIN, ENGLISH, SYMBOLS, EMOJI }

/** 依輸入框的類型決定鍵盤行為，跟手機輸入法的慣例一致。 */
object EditorPolicy {

    /** 一般文字欄位回傳 ZHUYIN，代表「沿用使用者上次的語言」；其他類型強制切頁。 */
    fun initialPage(inputType: Int): KeyboardPage {
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> KeyboardPage.SYMBOLS
            InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI -> KeyboardPage.ENGLISH
                else -> KeyboardPage.ZHUYIN
            }
            else -> KeyboardPage.ZHUYIN
        }
    }

    /** 換行鍵要執行的編輯動作；null＝送出一般的換行。 */
    fun enterAction(imeOptions: Int): Int? {
        // 多行欄位會帶這個旗標：換行鍵就是換行
        if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        return when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            EditorInfo.IME_ACTION_DONE -> action
            else -> null
        }
    }

    fun enterLabel(action: Int?) = when (action) {
        EditorInfo.IME_ACTION_GO -> "前往"
        EditorInfo.IME_ACTION_SEARCH -> "搜尋"
        EditorInfo.IME_ACTION_SEND -> "傳送"
        EditorInfo.IME_ACTION_NEXT -> "下一個"
        EditorInfo.IME_ACTION_PREVIOUS -> "上一個"
        EditorInfo.IME_ACTION_DONE -> "完成"
        // 「換行」兩個字看不出是 Enter；聊天 App 也是靠這個鍵送出
        else -> "⏎"
    }
}

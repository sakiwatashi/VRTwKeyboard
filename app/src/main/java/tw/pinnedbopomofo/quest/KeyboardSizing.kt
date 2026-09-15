package tw.pinnedbopomofo.quest

import kotlin.math.floor

/** 鍵盤各部位的像素尺寸。 */
data class KeyboardSizing(
    val candidateHeight: Int,
    val keyHeight: Int,
    val keyGap: Int,
    val padding: Int,
    val keyTextSp: Float,
    val candidateTextSp: Float,
) {
    /** 按鍵區固定用最多排數算，切到排數較少的英文、符號頁時鍵盤高度才不會跳。 */
    val keyAreaHeight get() = ROWS * (keyHeight + keyGap * 2)

    val totalHeight get() = padding * 2 + candidateHeight + keyAreaHeight

    companion object {
        /** 注音頁四排加功能列。 */
        const val ROWS = 5
        /** 候選字列比按鍵矮一點，把高度讓給按鍵：頭盔上按鍵比候選字更常點。 */
        private const val CANDIDATE_DP = 52
        private const val KEY_DP = 56
        private const val GAP_DP = 3
        private const val PADDING_DP = 4
        private const val TEXT_SP = 22f
        private const val MIN_TEXT_SP = 14f

        /**
         * 手機把整個螢幕高度給輸入法，照原本尺寸排。
         * Horizon OS 把輸入法視窗固定成 780×355 像素，照原本尺寸會把最下面的功能列切掉，
         * 所以放不下時等比縮小。
         */
        fun fit(availableHeightPx: Int, density: Float): KeyboardSizing {
            fun px(dp: Int) = (dp * density).toInt()
            val preferred = KeyboardSizing(
                candidateHeight = px(CANDIDATE_DP),
                keyHeight = px(KEY_DP),
                keyGap = px(GAP_DP),
                padding = px(PADDING_DP),
                keyTextSp = TEXT_SP,
                candidateTextSp = TEXT_SP,
            )
            if (availableHeightPx <= 0 || preferred.totalHeight <= availableHeightPx) return preferred

            val scale = availableHeightPx.toFloat() / preferred.totalHeight
            val gap = maxOf(1, floor(preferred.keyGap * scale).toInt())
            val padding = maxOf(1, floor(preferred.padding * scale).toInt())
            val candidate = floor(preferred.candidateHeight * scale).toInt()
            // 按鍵高度用剩下的空間反推，捨入誤差才不會累積成超出視窗
            val key = (availableHeightPx - padding * 2 - candidate - ROWS * gap * 2) / ROWS
            val text = maxOf(MIN_TEXT_SP, TEXT_SP * scale)
            return KeyboardSizing(candidate, key, gap, padding, text, text)
        }
    }
}

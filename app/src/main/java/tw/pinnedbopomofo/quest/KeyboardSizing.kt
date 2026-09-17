package tw.pinnedbopomofo.quest

import kotlin.math.floor

/** 鍵盤各部位的像素尺寸。[panelWidth] 是面板實際要用的寬度，可能比系統給的視窗窄（置中留白）。 */
data class KeyboardSizing(
    val panelWidth: Int,
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

    /** 一個字鍵的概略寬度：字鍵區 11 欄，右側欄約 1.6 欄。 */
    val keyWidth get() = (((panelWidth - padding * 2) / COLUMNS).toInt() - keyGap * 2).coerceAtLeast(1)

    companion object {
        /** 注音頁四排加功能列。 */
        const val ROWS = 5
        /** 字鍵 11 欄加右側欄 1.6 欄。 */
        private const val COLUMNS = 12.6f
        /** 候選字列比按鍵矮一點，把高度讓給按鍵：頭盔上按鍵比候選字更常點。 */
        private const val CANDIDATE_DP = 52
        private const val KEY_DP = 56
        private const val GAP_DP = 3
        private const val PADDING_DP = 4
        private const val TEXT_SP = 22f
        private const val MIN_TEXT_SP = 14f

        /**
         * 手機把整個螢幕給輸入法，照原本尺寸排。
         *
         * 頭盔給的尺寸不固定：2026-09-15 量到輸入法視窗是 780×355，重開機後（09-16）系統改成回報
         * 整個顯示器 3664×1920，照單全收就會排成一條 3664 像素寬的長條。所以寬高都要自己把關。
         */
        fun fit(availableWidthPx: Int, availableHeightPx: Int, density: Float): KeyboardSizing {
            fun px(dp: Int) = (dp * density).toInt()
            // 系統給多寬都不照單全收：VR 裡用射線掃過一排太寬的鍵盤很累
            val maxWidth = px(MAX_WIDTH_DP)
            val width = if (availableWidthPx > 0) minOf(availableWidthPx, maxWidth) else maxWidth
            val preferred = KeyboardSizing(
                panelWidth = width,
                candidateHeight = px(CANDIDATE_DP),
                keyHeight = px(KEY_DP),
                keyGap = px(GAP_DP),
                padding = px(PADDING_DP),
                keyTextSp = TEXT_SP,
                candidateTextSp = TEXT_SP,
            )
            if (availableHeightPx <= 0 || preferred.totalHeight <= availableHeightPx) return capByWidth(preferred)

            val scale = availableHeightPx.toFloat() / preferred.totalHeight
            val gap = maxOf(1, floor(preferred.keyGap * scale).toInt())
            val padding = maxOf(1, floor(preferred.padding * scale).toInt())
            val candidate = floor(preferred.candidateHeight * scale).toInt()
            // 按鍵高度用剩下的空間反推，捨入誤差才不會累積成超出視窗
            val key = (availableHeightPx - padding * 2 - candidate - ROWS * gap * 2) / ROWS
            val text = maxOf(MIN_TEXT_SP, TEXT_SP * scale)
            return capByWidth(KeyboardSizing(width, candidate, key, gap, padding, text, text))
        }

        /** 面板窄的時候按鍵不要變成又高又細，鍵高最多是鍵寬的 [MAX_KEY_ASPECT] 倍。 */
        private fun capByWidth(sizing: KeyboardSizing): KeyboardSizing {
            val limit = maxOf(1, (sizing.keyWidth * MAX_KEY_ASPECT).toInt())
            return if (sizing.keyHeight <= limit) sizing else sizing.copy(keyHeight = limit)
        }

        /** 面板最寬做到這裡；再寬就置中留白。 */
        private const val MAX_WIDTH_DP = 640
        private const val MAX_KEY_ASPECT = 2.5f
    }
}

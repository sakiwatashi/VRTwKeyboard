package tw.pinnedbopomofo.quest.effects

/**
 * 青玉背景板的金框有多厚：內容要退到框裡面。
 *
 * 比例是量圖量出來的（實心區的邊界），左右上下都不一樣。
 *
 * 獨立成一個物件是為了能測：這裡出錯的後果很具體——內距若是「算完尺寸再加上去」，
 * 內容就會比視窗高，底部整排功能列被切掉，表情頁連切回中文的鍵都按不到。
 * 2026-09-17 在頭盔上就是這樣。
 */
object JadeInsets {

    const val LEFT = 0.060f
    const val TOP = 0.118f
    const val RIGHT = 0.071f
    const val BOTTOM = 0.050f

    data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = left + right
        val height get() = top + bottom
    }

    val NONE = Insets(0, 0, 0, 0)

    fun of(availableWidth: Int, availableHeight: Int): Insets {
        if (availableWidth <= 0 || availableHeight <= 0) return NONE
        return Insets(
            left = (availableWidth * LEFT).toInt(),
            top = (availableHeight * TOP).toInt(),
            right = (availableWidth * RIGHT).toInt(),
            bottom = (availableHeight * BOTTOM).toInt(),
        )
    }
}

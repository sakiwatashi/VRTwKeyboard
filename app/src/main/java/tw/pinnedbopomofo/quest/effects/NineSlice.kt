package tw.pinnedbopomofo.quest.effects

/**
 * 九宮格切圖的算式：把一張圖切成九塊，四角不拉伸、四邊只單方向拉伸、中間兩向拉伸。
 *
 * 為什麼要自己算：Android 內建的 NinePatchDrawable 需要用 `.9.png` 編譯，
 * 但我們的圖是直接生成的 PNG，沒有那圈標記像素。自己切反而可以用比例指定邊界，
 * 換一張圖只要調兩個數字。
 *
 * 分開成純算式是因為會出錯的都在這裡：角落被拉變形、格子之間有縫、
 * 目標比原圖還小的時候左右邊界打架。這些測得出來。
 */
object NineSlice {

    /** 一塊的來源與目標範圍，單位都是像素。 */
    data class Piece(
        val srcLeft: Int, val srcTop: Int, val srcRight: Int, val srcBottom: Int,
        val dstLeft: Float, val dstTop: Float, val dstRight: Float, val dstBottom: Float,
    ) {
        val srcWidth get() = srcRight - srcLeft
        val srcHeight get() = srcBottom - srcTop
        val dstWidth get() = dstRight - dstLeft
        val dstHeight get() = dstBottom - dstTop
    }

    /**
     * [insetX]、[insetY] 是角落佔原圖寬高的比例（0～0.5）。
     *
     * 目標若窄到放不下左右兩個角，角會**等比例縮小**而不是互相重疊——
     * 重疊的話中間那塊會變成負寬度，畫出來是一團亂。
     */
    fun slices(
        bitmapWidth: Int,
        bitmapHeight: Int,
        destWidth: Float,
        destHeight: Float,
        insetX: Float,
        insetY: Float,
    ): List<Piece> {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || destWidth <= 0f || destHeight <= 0f) return emptyList()

        val srcInsetX = (bitmapWidth * insetX.coerceIn(0f, 0.5f)).toInt().coerceAtLeast(1)
        val srcInsetY = (bitmapHeight * insetY.coerceIn(0f, 0.5f)).toInt().coerceAtLeast(1)

        // 目標放不下兩個角就一起縮。X 與 Y 要用**同一個**倍率，
        // 各縮各的會把角落壓扁——雲紋那種裝飾一變形就很明顯。
        //
        // 兩個角加起來也不可以吃掉整個目標：中間那條被擠成零之後，
        // 上緣與下緣的弧線會直接碰在一起，長出原圖根本沒有的尖角。
        // 2026-09-17 候選字玉板就是這樣——先是整條消失，把 inset 調小之後變成尖的。
        // 角落最多只佔 [MAX_INSET_SHARE]，中間永遠留得下來。
        val scale = minOf(
            1f,
            destWidth * MAX_INSET_SHARE / srcInsetX,
            destHeight * MAX_INSET_SHARE / srcInsetY,
        )
        val dstInsetX = srcInsetX * scale
        val dstInsetY = srcInsetY * scale

        val srcColumns = intArrayOf(0, srcInsetX, bitmapWidth - srcInsetX, bitmapWidth)
        val srcRows = intArrayOf(0, srcInsetY, bitmapHeight - srcInsetY, bitmapHeight)
        val dstColumns = floatArrayOf(0f, dstInsetX, destWidth - dstInsetX, destWidth)
        val dstRows = floatArrayOf(0f, dstInsetY, destHeight - dstInsetY, destHeight)

        val pieces = ArrayList<Piece>(9)
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                pieces += Piece(
                    srcLeft = srcColumns[column], srcTop = srcRows[row],
                    srcRight = srcColumns[column + 1], srcBottom = srcRows[row + 1],
                    dstLeft = dstColumns[column], dstTop = dstRows[row],
                    dstRight = dstColumns[column + 1], dstBottom = dstRows[row + 1],
                )
            }
        }
        return pieces
    }

    /** 單邊的角落最多佔目標的幾成。兩邊合計 0.8，中間至少留兩成。 */
    const val MAX_INSET_SHARE = 0.4f

    /** 這一塊是不是角落。角落不該被拉伸。 */
    fun isCorner(index: Int) = index in CORNERS

    private val CORNERS = setOf(0, 2, 6, 8)
}

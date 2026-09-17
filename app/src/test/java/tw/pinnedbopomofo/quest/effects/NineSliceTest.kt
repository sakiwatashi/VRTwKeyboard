package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NineSliceTest {

    private fun slice(destWidth: Float, destHeight: Float) =
        NineSlice.slices(400, 200, destWidth, destHeight, insetX = 0.25f, insetY = 0.25f)

    @Test
    fun `九塊剛好鋪滿目標，中間沒有縫也沒有重疊`() {
        val pieces = slice(800f, 300f)
        assertEquals(9, pieces.size)
        val area = pieces.sumOf { (it.dstWidth * it.dstHeight).toDouble() }
        assertEquals("九塊的面積應該剛好等於目標", 800.0 * 300.0, area, 0.5)
        // 每一排的右邊界要接上下一塊的左邊界
        for (row in 0 until 3) {
            val inRow = pieces.subList(row * 3, row * 3 + 3)
            assertEquals(0f, inRow[0].dstLeft, 0.001f)
            assertEquals(inRow[0].dstRight, inRow[1].dstLeft, 0.001f)
            assertEquals(inRow[1].dstRight, inRow[2].dstLeft, 0.001f)
            assertEquals(800f, inRow[2].dstRight, 0.001f)
        }
    }

    @Test
    fun `放大時四個角不會被拉變形，中間才拉`() {
        val pieces = slice(1600f, 800f)
        for (index in pieces.indices) {
            val piece = pieces[index]
            if (NineSlice.isCorner(index)) {
                assertEquals("角落 $index 的寬被拉了", piece.srcWidth.toFloat(), piece.dstWidth, 0.001f)
                assertEquals("角落 $index 的高被拉了", piece.srcHeight.toFloat(), piece.dstHeight, 0.001f)
            }
        }
        // 中間那塊要被拉開
        val middle = pieces[4]
        assertTrue("中間應該被拉寬：${middle.dstWidth}", middle.dstWidth > middle.srcWidth)
    }

    @Test
    fun `目標比兩個角還窄時，角要一起縮小，不能互相重疊`() {
        // 原圖 400 寬、角各 100，兩個角就 200；目標只有 60
        val pieces = slice(60f, 40f)
        for (piece in pieces) {
            assertTrue("寬度變成負的了：${piece.dstWidth}", piece.dstWidth >= -0.001f)
            assertTrue("高度變成負的了：${piece.dstHeight}", piece.dstHeight >= -0.001f)
        }
        val area = pieces.sumOf { (it.dstWidth * it.dstHeight).toDouble() }
        assertEquals(60.0 * 40.0, area, 0.5)
        // 角落此時應該等比縮小，不是被壓扁
        val corner = pieces[0]
        val srcAspect = corner.srcWidth.toFloat() / corner.srcHeight
        val dstAspect = corner.dstWidth / corner.dstHeight
        assertTrue("角落被壓扁了：$srcAspect vs $dstAspect", abs(srcAspect - dstAspect) < 0.35f)
    }

    @Test
    fun `中間那條永遠留得下來，不會被兩個角擠成零`() {
        // 中間被擠成零的時候，上下緣的弧線會碰在一起，長出原圖沒有的尖角。
        // 400x200 的圖、角落各佔 25%（縱向各 50px）；目標高度 101 時兩個角就吃掉 100。
        for (height in listOf(101f, 100f, 90f, 60f, 20f, 4f)) {
            val pieces = NineSlice.slices(400, 200, 300f, height, insetX = 0.25f, insetY = 0.25f)
            val middleBand = pieces[3].dstHeight
            assertTrue(
                "高度 $height 時中間只剩 $middleBand，玉板會變成尖的",
                middleBand >= height * 0.15f,
            )
        }
    }

    @Test
    fun `來源的九塊也要剛好鋪滿原圖，不會漏掉或重複取樣`() {
        val pieces = slice(500f, 500f)
        val area = pieces.sumOf { (it.srcWidth * it.srcHeight).toDouble() }
        assertEquals(400.0 * 200.0, area, 0.5)
    }

    @Test
    fun `尺寸是零或負的就回空的，不要當掉`() {
        assertTrue(NineSlice.slices(0, 200, 100f, 100f, 0.25f, 0.25f).isEmpty())
        assertTrue(NineSlice.slices(400, 200, 0f, 100f, 0.25f, 0.25f).isEmpty())
        assertTrue(NineSlice.slices(400, 200, 100f, -5f, 0.25f, 0.25f).isEmpty())
    }
}

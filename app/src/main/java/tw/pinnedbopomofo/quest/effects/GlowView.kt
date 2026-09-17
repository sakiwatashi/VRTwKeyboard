package tw.pinnedbopomofo.quest.effects

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * 輝光層：畫在鍵帽**後面**的一層模糊光斑，鍵帽再實心地蓋上去。
 *
 * 這是真的 bloom，不是在鍵帽上加一圈邊：亮光從鍵縫溢出來，所以看起來是鍵帽在發光，
 * 而不是有人幫它描了邊。霓虹那套配色現在只是幾條亮線，加上這一層才會像霓虹燈。
 *
 * 用 [RenderEffect] 做模糊——2026-09-17 在頭盔上確認過輸入法視窗**有**硬體加速，
 * 這個效果才會真的生效。沒有硬體加速的話它不會報錯、只是安靜地沒作用，
 * 所以 [applyBlur] 回傳有沒有真的套上去，讓呼叫端記錄下來。
 */
class GlowView(context: Context) : View(context) {

    private class Spot(val bounds: RectF, val color: Int, val radius: Float)

    private val spots = ArrayList<Spot>()
    // 畫外框不畫實心：模糊之後光暈會貼著鍵帽邊緣散出去，鍵面中央維持乾淨，
    // 注音符號才不會被光洗掉。畫實心的話整顆鍵會糊成一團。
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    /**
     * 套上模糊。[radiusPx] 是光暈的擴散半徑。
     * 回傳 false 代表這台機器做不到，呼叫端應該把輝光關掉而不是留一層沒模糊的色塊。
     */
    fun applyBlur(radiusPx: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.DECAL))
            true
        } catch (error: Throwable) {
            setRenderEffect(null)
            false
        }
    }

    fun clearBlur() = setRenderEffect(null)

    /** 換配色或重排版時重新給一份光斑位置。 */
    fun setSpots(bounds: List<RectF>, colors: List<Int>, radius: Float) {
        spots.clear()
        for (index in bounds.indices) {
            spots += Spot(RectF(bounds[index]), colors.getOrElse(index) { colors.lastOrNull() ?: 0 }, radius)
        }
        invalidate()
    }

    fun clearSpots() {
        spots.clear()
        invalidate()
    }

    /** 光暈外框的粗細。 */
    var strokeWidth = 3f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        paint.strokeWidth = strokeWidth
        for (spot in spots) {
            paint.color = spot.color
            canvas.drawRoundRect(spot.bounds, spot.radius, spot.radius, paint)
        }
    }
}

package tw.pinnedbopomofo.quest

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/**
 * 鍵帽的畫法。全部用程式畫、不用圖檔：APK 不會變大，在 VR 裡拉遠拉近也不會糊。
 *
 * VR 注意事項：細線與密集紋理在頭盔上會閃，所以磚縫、雲紋都畫粗一點、疏一點。
 */
object KeycapArt {

    /**
     * [unit] 是 1dp 換算成的像素；[active] 是 Enter 與選中的鍵，
     * 有些風格的重點鍵是另一種造型（磚塊風格的問號磚、仙俠風格的朱砂印）。
     */
    fun face(
        theme: KeyboardTheme,
        top: Int,
        bottom: Int,
        radius: Float,
        unit: Float,
        active: Boolean,
    ): Drawable = when (theme.face) {
        KeyFace.FLAT -> GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
            .apply { cornerRadius = radius }
        KeyFace.BRICK -> if (active) {
            QuestionBlockFace(top, bottom, theme.detail, unit)
        } else {
            BrickFace(top, bottom, theme.detail, unit)
        }
        KeyFace.BAMBOO -> BambooFace(top, bottom, theme.detail, radius, unit, active)
        KeyFace.NEON -> NeonFace(top, bottom, theme.detail, radius, unit)
        KeyFace.JADE -> JadeFace(top, bottom, theme.detail, radius, unit, active)
        // 平面風格：單色、大圓角，連漸層都不要。乾淨是它唯一的賣點，多一層光就髒了。
        KeyFace.PLAIN -> GradientDrawable()
            .apply {
                cornerRadius = radius
                setColor(top)
            }
    }

    private abstract class Face(protected val unit: Float) : Drawable() {
        protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var alphaValue = 255

        override fun setAlpha(alpha: Int) {
            alphaValue = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity() = PixelFormat.TRANSLUCENT

        protected fun shade(color: Int, extra: Float = 1f): Int {
            val a = (Color.alpha(color) * extra * alphaValue / 255f).toInt().coerceIn(0, 255)
            return (a shl 24) or (color and 0xFFFFFF)
        }

        protected fun outline(canvas: Canvas, area: RectF, color: Int, width: Float) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width
            paint.color = shade(color)
            canvas.drawRect(
                area.left + width / 2, area.top + width / 2,
                area.right - width / 2, area.bottom - width / 2,
                paint,
            )
            paint.style = Paint.Style.FILL
        }
    }

    /**
     * 瑪利歐磚：三排交錯的細磚、粗磚縫、外圍近黑描邊。
     * 遊戲裡的磚是方角，所以不用圓角。
     */
    private class BrickFace(
        private val top: Int,
        private val bottom: Int,
        private val mortar: Int,
        unit: Float,
    ) : Face(unit) {
        override fun draw(canvas: Canvas) {
            val area = RectF(bounds)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = shade(mortar)
            canvas.drawRect(area, paint)

            val edge = 1.5f * unit
            val seam = 1.5f * unit
            val inner = RectF(area.left + edge, area.top + edge, area.right - edge, area.bottom - edge)
            val rowHeight = (inner.height() - seam * (ROWS - 1)) / ROWS
            val brickWidth = inner.width() / 2
            for (row in 0 until ROWS) {
                val y = inner.top + row * (rowHeight + seam)
                // 單數排往左推半塊，磚縫才會交錯
                val offset = if (row % 2 == 1) brickWidth / 2 else 0f
                var x = inner.left - offset
                while (x < inner.right) {
                    val left = maxOf(x, inner.left)
                    val right = minOf(x + brickWidth - seam, inner.right)
                    if (right - left > unit) {
                        val brick = RectF(left, y, right, y + rowHeight)
                        paint.shader = LinearGradient(
                            0f, brick.top, 0f, brick.bottom, shade(top), shade(bottom), Shader.TileMode.CLAMP,
                        )
                        canvas.drawRect(brick, paint)
                        paint.shader = null
                        paint.color = shade(Color.WHITE, 0.18f)
                        canvas.drawRect(brick.left, brick.top, brick.right, brick.top + unit, paint)
                    }
                    x += brickWidth
                }
            }
            outline(canvas, area, OUTLINE, edge)
        }

        private companion object {
            const val ROWS = 3
            val OUTLINE = 0xFF1C0A04.toInt()
        }
    }

    /** 問號磚：金黃底、深棕粗邊、四角鉚釘。鍵面上的字由按鍵自己畫。 */
    private class QuestionBlockFace(
        private val top: Int,
        private val bottom: Int,
        private val border: Int,
        unit: Float,
    ) : Face(unit) {
        override fun draw(canvas: Canvas) {
            val area = RectF(bounds)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, area.top, 0f, area.bottom, shade(top), shade(bottom), Shader.TileMode.CLAMP)
            canvas.drawRect(area, paint)
            paint.shader = null

            val rivet = 2.5f * unit
            val inset = 3f * unit
            paint.color = shade(border)
            for (x in listOf(area.left + inset, area.right - inset - rivet)) {
                for (y in listOf(area.top + inset, area.bottom - inset - rivet)) {
                    canvas.drawRect(x, y, x + rivet, y + rivet, paint)
                }
            }
            outline(canvas, area, border, 2f * unit)
        }
    }

    /**
     * 竹簡：直向竹片、竹片之間留深色縫、上下兩道繩結。
     * 重點鍵（Enter）改成朱漆木牌：紅底加米色細框，跟竹片分得開。
     */
    private class BambooFace(
        private val top: Int,
        private val bottom: Int,
        private val cord: Int,
        private val radius: Float,
        unit: Float,
        private val lacquer: Boolean,
    ) : Face(unit) {
        override fun draw(canvas: Canvas) {
            val area = RectF(bounds)
            if (lacquer) {
                drawLacquerTag(canvas, area)
                return
            }
            // 竹片之間的縫看得到底下的深色
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = shade(cord, 0.9f)
            canvas.drawRoundRect(area, radius / 2, radius / 2, paint)

            val gap = 1.5f * unit
            val slipWidth = (area.width() - gap * (SLIPS + 1)) / SLIPS
            for (index in 0 until SLIPS) {
                val left = area.left + gap + index * (slipWidth + gap)
                val slip = RectF(left, area.top + gap, left + slipWidth, area.bottom - gap)
                paint.shader = LinearGradient(
                    slip.left, 0f, slip.right, 0f, shade(top), shade(bottom), Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(slip, unit, unit, paint)
                // 竹纖維：每片一條淡直紋
                paint.shader = null
                paint.color = shade(cord, 0.16f)
                val grain = slip.left + slipWidth * 0.62f
                canvas.drawRect(grain, slip.top + 2 * unit, grain + unit * 0.6f, slip.bottom - 2 * unit, paint)
            }
            drawCords(canvas, area)
        }

        /** 上下各一道繩結，橫過所有竹片。 */
        private fun drawCords(canvas: Canvas, area: RectF) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            for (ratio in listOf(0.24f, 0.76f)) {
                val y = area.top + area.height() * ratio
                val thickness = 1.8f * unit
                paint.color = shade(cord, 0.85f)
                canvas.drawRect(area.left, y - thickness / 2, area.right, y + thickness / 2, paint)
                // 繩子上緣一條亮邊，看得出是繞上去的
                paint.color = shade(Color.WHITE, 0.12f)
                canvas.drawRect(area.left, y - thickness / 2, area.right, y - thickness / 2 + unit * 0.5f, paint)
            }
        }

        /** 朱漆木牌：紅底、米色細框。 */
        private fun drawLacquerTag(canvas: Canvas, area: RectF) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, area.top, 0f, area.bottom, shade(top), shade(bottom), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(area, radius / 2, radius / 2, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = unit
            paint.color = shade(0xFFF0E0C0.toInt(), 0.6f)
            val inset = 2f * unit
            canvas.drawRoundRect(
                RectF(area.left + inset, area.top + inset, area.right - inset, area.bottom - inset),
                radius / 3, radius / 3, paint,
            )
            paint.style = Paint.Style.FILL
        }

        private companion object {
            /** 一個鍵上有幾片竹簡。 */
            const val SLIPS = 3
        }
    }

    /**
     * 青玉片：半透明玉色鍵面、上緣一道透光、金線收邊。
     *
     * 玉的質感來自「光從邊緣透進來」而不是「表面反光」，所以亮的地方在四周內側
     * 而不是正上方——這跟塑膠鍵帽正好相反，畫反了就變成糖果。
     *
     * 重點鍵（Enter）換成金片：金底、深色玉紋收邊。
     */
    private class JadeFace(
        private val top: Int,
        private val bottom: Int,
        private val gold: Int,
        private val radius: Float,
        unit: Float,
        private val golden: Boolean,
    ) : Face(unit) {
        override fun draw(canvas: Canvas) {
            val area = RectF(bounds)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f, area.top, 0f, area.bottom, shade(top), shade(bottom), Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(area, radius, radius, paint)
            paint.shader = null

            // 邊緣透光：沿著內緣描一圈很淡的白，玉才會有厚度
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * unit
            paint.color = shade(Color.WHITE, 0.22f)
            val glow = 2f * unit
            canvas.drawRoundRect(
                RectF(area.left + glow, area.top + glow, area.right - glow, area.bottom - glow),
                radius * 0.8f, radius * 0.8f, paint,
            )

            // 上緣一道更亮的透光，像光從上面打進玉裡
            paint.strokeWidth = unit
            paint.color = shade(Color.WHITE, 0.4f)
            val inset = 3.5f * unit
            canvas.drawArc(
                RectF(area.left + inset, area.top + inset, area.right - inset, area.bottom - inset),
                200f, 140f, false, paint,
            )

            // 金線收邊
            paint.strokeWidth = unit
            paint.color = shade(gold, if (golden) 0.95f else 0.7f)
            val rim = unit / 2
            canvas.drawRoundRect(
                RectF(area.left + rim, area.top + rim, area.right - rim, area.bottom - rim),
                radius, radius, paint,
            )
            paint.style = Paint.Style.FILL
        }
    }

    /** 霓虹：深色鍵面加外發光邊框，用三層由淡到濃的邊模擬光暈。 */
    private class NeonFace(
        private val top: Int,
        private val bottom: Int,
        private val glow: Int,
        private val radius: Float,
        unit: Float,
    ) : Face(unit) {
        override fun draw(canvas: Canvas) {
            val area = RectF(bounds)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, area.top, 0f, area.bottom, shade(top), shade(bottom), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(area, radius, radius, paint)

            paint.shader = null
            paint.style = Paint.Style.STROKE
            for ((width, strength) in listOf(3f to 0.18f, 2f to 0.35f, 1f to 0.85f)) {
                paint.strokeWidth = width * unit
                paint.color = shade(glow, strength)
                val inset = width * unit / 2
                canvas.drawRoundRect(
                    RectF(area.left + inset, area.top + inset, area.right - inset, area.bottom - inset),
                    radius, radius, paint,
                )
            }
            paint.style = Paint.Style.FILL
        }
    }
}

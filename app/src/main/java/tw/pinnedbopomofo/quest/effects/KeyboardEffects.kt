package tw.pinnedbopomofo.quest.effects

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 鍵盤特效。
 *
 * 2026-09-17 在使用者的 Quest 3S 上實測（見 [EffectProbe]）：輸入法面板**有**硬體加速、
 * 實測 90 fps、兩秒內掉 0 幀、AGSL 編得過。所以連續特效是可行的，不必退回「按一下演一次」。
 *
 * 但那次量的是一條 2 dp 的細線，只證明管線跑得動；整面鍵盤的負載要用真的特效再量一次。
 * 所以每個特效都能單獨關掉，關掉就完全不排任何動畫。
 */
object KeyboardEffects {

    /**
     * 按下時鍵帽往後倒幾度。
     *
     * 一開始設 12 度，2026-09-17 使用者在頭盔上回報「不知道傾斜是啥效果」——
     * 太小了，在 VR 的視距下根本看不出來。改成 24 度。
     */
    const val TILT_DEGREES = 24f

    /** 透視的相機距離（dp）。越小透視越誇張；VR 裡不要太誇張。 */
    private const val CAMERA_DISTANCE_DP = 900f

    private const val TILT_DOWN_MS = 70L
    private const val TILT_UP_MS = 180L

    /**
     * 按下的漣漪。用系統的 [RippleDrawable]：它跑在 RenderThread 上，
     * 不佔我們的主執行緒，也不用自己畫每一幀。
     *
     * [content] 是原本的鍵帽，漣漪疊在它上面。
     */
    fun ripple(content: Drawable, color: Int, radius: Float): Drawable {
        val mask = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(color), content, mask)
    }

    /**
     * 按下時鍵帽真的往後傾：[View.rotationX] 加上透視，不是把圖壓扁。
     * 支點放在底邊，看起來就像鍵帽被按進面板裡。
     */
    fun tilt(view: View, pressed: Boolean, density: Float) {
        view.cameraDistance = CAMERA_DISTANCE_DP * density
        view.pivotX = view.width / 2f
        view.pivotY = view.height.toFloat()
        view.animate().cancel()
        if (pressed) {
            view.animate()
                .rotationX(TILT_DEGREES)
                .setDuration(TILT_DOWN_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            // 放開時彈回來一點點過頭，手感才活
            view.animate()
                .rotationX(0f)
                .setDuration(TILT_UP_MS)
                .setInterpolator(OvershootInterpolator(2.2f))
                .start()
        }
    }

    /** 把傾斜還原，換配色重建鍵盤時用。 */
    fun clearTilt(view: View) {
        view.animate().cancel()
        view.rotationX = 0f
    }

    /**
     * 流光：一道斜光帶定時掃過整個鍵盤。
     *
     * 疊在按鍵上面、不吃觸控（呼叫端要設 `isClickable = false`）。
     * 只有在掃的那 0.9 秒才會要求重畫，停頓的 5 秒完全不排幀，待機時不燒電。
     */
    class SheenView(context: Context, private val color: Int) : View(context) {

        private val clock = Sheen()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var startedAt = 0L
        private var running = false
        /** 光帶寬度佔面板寬度的比例。 */
        private val bandRatio = 0.18f

        init {
            setWillNotDraw(false)
        }

        fun start() {
            if (running) return
            running = true
            startedAt = SystemClock.elapsedRealtime()
            postInvalidateOnAnimation()
        }

        fun stop() {
            running = false
            animate().cancel()
        }

        override fun onDraw(canvas: Canvas) {
            if (!running || width == 0) return
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val position = clock.positionAt(elapsed)
            if (position == null) {
                // 停頓中：不畫，也不要每幀都醒來
                postInvalidateDelayed(PAUSE_POLL_MS)
                return
            }
            val centerX = clock.centerX(position, width.toFloat())
            val half = width * bandRatio / 2f
            // 斜的光帶：上緣比下緣往右一點，看起來像從側邊掃過
            val slant = height * 0.6f
            paint.shader = LinearGradient(
                centerX - half, 0f, centerX + half + slant, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            postInvalidateOnAnimation()
        }

        private companion object {
            /** 停頓期間多久醒來看一次；不用每幀。 */
            const val PAUSE_POLL_MS = 100L
        }
    }
}

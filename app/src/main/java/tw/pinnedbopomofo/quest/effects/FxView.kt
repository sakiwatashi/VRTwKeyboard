package tw.pinnedbopomofo.quest.effects

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.max

/**
 * 蓋在鍵盤上面的特效層：火花、能量環、飛字、語音聲波全部畫在這一張畫布上。
 *
 * 為什麼全部擠在一個 View：每顆鍵一個動畫 View 的話，連打時會同時有二三十個
 * View 在排動畫、各自觸發 layout。一張覆蓋層只重畫自己一層，鍵帽本身完全不動。
 *
 * **沒有東西在動的時候完全不排幀**——不是畫空白，是根本不要求重畫。
 * 待機時這一層等於不存在。
 */
class FxView(context: Context) : View(context) {

    private val particles = Particles()
    private val shocks = Shocks()
    private val flyers = ArrayList<Flyer>()
    private val flyerPaints = ArrayList<Paint>()

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var lastFrameNanos = 0L
    private var running = false

    /** 語音聲波：> 0 就畫。由外面每收到一框音訊餵一次。 */
    /** 煙霧繚繞：開著就一直飄，是唯一會持續排幀的特效。 */
    private var mist: Mist? = null
    private var mistColor = Color.WHITE

    private var voiceLevel = 0f
    private var voiceActive = false
    private var voiceColor = Color.WHITE
    private var wavePhase = 0f

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    fun burst(x: Float, y: Float, color: Int, count: Int = 14, speed: Float = 260f) {
        particles.burst(x, y, count, speed, color)
        wake()
    }

    fun shock(x: Float, y: Float) {
        shocks.add(x, y)
        wake()
    }

    fun fly(text: String, fromX: Float, fromY: Float, toX: Float, toY: Float, color: Int, textSizePx: Float) {
        flyers += Flyer(text, fromX, fromY, toX, toY)
        flyerPaints += Paint(glyph).apply {
            this.color = color
            textSize = textSizePx
            setShadowLayer(textSizePx * 0.5f, 0f, 0f, color)
        }
        wake()
    }

    fun mist(on: Boolean, color: Int) {
        mistColor = color
        if (on) {
            if (mist == null) mist = Mist()
            wake()
        } else {
            mist = null
            invalidate()
        }
    }

    /** [level] 是 0～1 的音量，跟音量條用的是同一個值。 */
    fun voice(on: Boolean, level: Float, color: Int) {
        voiceActive = on
        voiceLevel = level
        voiceColor = color
        if (on) wake() else invalidate()
    }

    /** 換配色或收鍵盤時清乾淨，免得殘留的火花蓋在新畫面上。 */
    fun reset() {
        mist = null
        particles.clear()
        shocks.clear()
        flyers.clear()
        flyerPaints.clear()
        voiceActive = false
        running = false
        invalidate()
    }

    private val busy get() = particles.busy || shocks.busy || flyers.isNotEmpty() || voiceActive || mist != null

    private fun wake() {
        if (running) return
        running = true
        lastFrameNanos = System.nanoTime()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (!busy) {
            running = false
            return
        }
        val now = System.nanoTime()
        // 第一幀的 dt 會很大（從 wake 到真的畫可能隔了好幾毫秒），夾住免得粒子瞬移
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = now

        particles.update(dt)
        shocks.update(dt)
        updateFlyers(dt)
        mist?.let {
            it.update(dt)
            // 霧畫在最底下：它是背景，不該蓋住火花與飛字
            drawMist(canvas, it)
        }

        drawShocks(canvas)
        drawParticles(canvas)
        drawFlyers(canvas)
        if (voiceActive) drawVoice(canvas, dt)

        if (busy) postInvalidateOnAnimation() else running = false
    }

    private fun updateFlyers(dt: Float) {
        var index = flyers.size - 1
        while (index >= 0) {
            if (!flyers[index].update(dt)) {
                flyers.removeAt(index)
                flyerPaints.removeAt(index)
            }
            index -= 1
        }
    }

    /**
     * 霧：每團畫成一個柔邊橢圓。用徑向漸層讓邊緣化開，
     * 硬邊的橢圓在頭盔上會看得出是個蛋。
     */
    private fun drawMist(canvas: Canvas, mist: Mist) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (wisp in mist.items) {
            val cx = wisp.x * w
            val cy = wisp.yAt(mist.seconds) * h
            val rx = wisp.width * w / 2
            val ry = wisp.height * h / 2
            if (rx <= 0f || ry <= 0f) continue
            dot.shader = RadialGradient(
                cx, cy, maxOf(rx, ry),
                intArrayOf(
                    (((wisp.alpha * 255).toInt() shl 24) or (mistColor and 0xFFFFFF)),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(1f, ry / maxOf(rx, ry))
            canvas.translate(-cx, -cy)
            canvas.drawCircle(cx, cy, maxOf(rx, ry), dot)
            canvas.restore()
        }
        dot.shader = null
    }

    private fun drawParticles(canvas: Canvas) {
        for (spark in particles.items) {
            val life = spark.life.coerceIn(0f, 1f)
            dot.color = spark.color
            dot.alpha = (life * 255).toInt()
            canvas.drawCircle(spark.x, spark.y, 1.5f + life * 2.2f, dot)
        }
    }

    private fun drawShocks(canvas: Canvas) {
        for (item in shocks.items) {
            val life = item.life.coerceIn(0f, 1f)
            ring.color = voiceColor
            ring.alpha = (life * 140).toInt()
            ring.strokeWidth = 2f + life * 5f
            canvas.drawCircle(item.x, item.y, item.radius, ring)
        }
    }

    private fun drawFlyers(canvas: Canvas) {
        for (index in flyers.indices) {
            val flyer = flyers[index]
            val paint = flyerPaints[index]
            paint.alpha = (flyer.alpha.coerceIn(0f, 1f) * 255).toInt()
            canvas.save()
            canvas.translate(flyer.x, flyer.y)
            canvas.scale(flyer.scale, flyer.scale)
            // drawText 的 y 是基線，往下推一點才會視覺置中
            canvas.drawText(flyer.text, 0f, paint.textSize * 0.36f, paint)
            canvas.restore()
        }
    }

    /**
     * 語音聲波：一條橫過鍵盤的波，振幅跟著音量走。
     * 音量來自 Endpointer 每 20 毫秒算出來的那個值，不是假的。
     */
    private fun drawVoice(canvas: Canvas, dt: Float) {
        wavePhase += dt * WAVE_SPEED
        val midY = height * 0.5f
        val amplitude = max(WAVE_MIN_AMPLITUDE, voiceLevel * height * 0.34f)
        wave.color = voiceColor
        wave.alpha = WAVE_ALPHA
        wave.strokeWidth = 3f
        var x = 0f
        var previousY = midY
        val step = WAVE_STEP
        while (x <= width) {
            // 兩個頻率疊起來，看起來才像聲音不像時鐘
            val phase = x * WAVE_FREQUENCY + wavePhase
            val value = (Math.sin(phase.toDouble()) * 0.7 + Math.sin(phase * 2.3 + 1.0) * 0.3).toFloat()
            // 兩端收窄，波形不會被畫面邊緣切斷
            val taper = Math.sin(Math.PI * x / max(width, 1)).toFloat()
            val y = midY + value * amplitude * taper
            if (x > 0f) canvas.drawLine(x - step, previousY, x, y, wave)
            previousY = y
            x += step
        }
    }

    private companion object {
        const val WAVE_SPEED = 9f
        const val WAVE_FREQUENCY = 0.055f
        const val WAVE_STEP = 6f
        const val WAVE_MIN_AMPLITUDE = 3f
        const val WAVE_ALPHA = 200
    }
}

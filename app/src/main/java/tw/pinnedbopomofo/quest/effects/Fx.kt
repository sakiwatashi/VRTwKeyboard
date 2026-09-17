package tw.pinnedbopomofo.quest.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 特效的模擬本體：只算位置與生命，不碰 Canvas。
 *
 * 分開的理由跟 [Sheen] 一樣——畫布的東西在單元測試裡跑不了，
 * 但「粒子會不會噴出來、會不會消失、飛字會不會到站」這些才是會出錯的地方。
 *
 * 單位：座標是像素，時間是秒。
 */

/**
 * 按鍵爆出的火花。
 *
 * [max] 是硬上限：連打的時候不能讓粒子無限累積，寧可少噴幾顆也不要掉幀。
 */
class Particles(
    private val gravity: Float = 1400f,
    private val fade: Float = 2.6f,
    val max: Int = 180,
    private val random: Random = Random.Default,
) {
    class Spark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val color: Int,
    )

    private val sparks = ArrayList<Spark>()
    val items: List<Spark> get() = sparks
    val size get() = sparks.size
    val busy get() = sparks.isNotEmpty()

    /** 從 ([x], [y]) 均勻噴出 [count] 顆，方向平均分佈再加一點亂數，才不會像時鐘。 */
    fun burst(x: Float, y: Float, count: Int, speed: Float, color: Int) {
        val room = max - sparks.size
        if (room <= 0) return
        val actual = minOf(count, room)
        for (i in 0 until actual) {
            val angle = (2 * PI * i / actual + random.nextDouble(-0.35, 0.35)).toFloat()
            val magnitude = speed * (0.55f + random.nextFloat() * 0.85f)
            sparks += Spark(
                x = x,
                y = y,
                vx = cos(angle) * magnitude,
                vy = sin(angle) * magnitude - speed * 0.3f,
                life = 1f,
                color = color,
            )
        }
    }

    fun update(dt: Float) {
        var index = sparks.size - 1
        while (index >= 0) {
            val spark = sparks[index]
            spark.life -= dt * fade
            if (spark.life <= 0f) {
                // 用最後一顆填掉，省得整個陣列往前搬
                sparks[index] = sparks[sparks.size - 1]
                sparks.removeAt(sparks.size - 1)
            } else {
                spark.vy += gravity * dt
                spark.x += spark.vx * dt
                spark.y += spark.vy * dt
            }
            index -= 1
        }
    }

    fun clear() = sparks.clear()
}

/** 從按下的位置擴散出去的能量環。 */
class Shocks(
    private val speed: Float = 900f,
    private val fade: Float = 1.7f,
    private val max: Int = 4,
) {
    class Ring(val x: Float, val y: Float, var radius: Float, var life: Float)

    private val rings = ArrayList<Ring>()
    val items: List<Ring> get() = rings
    val busy get() = rings.isNotEmpty()

    fun add(x: Float, y: Float) {
        // 連打時只留最新的幾圈，畫面才不會糊成一片
        while (rings.size >= max) rings.removeAt(0)
        rings += Ring(x, y, 0f, 1f)
    }

    fun update(dt: Float) {
        var index = rings.size - 1
        while (index >= 0) {
            val ring = rings[index]
            ring.life -= dt * fade
            ring.radius += speed * dt
            if (ring.life <= 0f) rings.removeAt(index)
            index -= 1
        }
    }

    fun clear() = rings.clear()
}

/**
 * 按下的字從鍵帽飛進輸入框。
 *
 * 走一條拋物線而不是直線：直線看起來像被瞬移過去，弧線才看得出是「飛」。
 */
class Flyer(
    val text: String,
    private val fromX: Float,
    private val fromY: Float,
    private val toX: Float,
    private val toY: Float,
    private val durationMs: Float = 320f,
    private val arc: Float = 34f,
) {
    private var elapsedMs = 0f

    val progress get() = (elapsedMs / durationMs).coerceIn(0f, 1f)
    val done get() = elapsedMs >= durationMs

    /** 回傳是否還活著。 */
    fun update(dt: Float): Boolean {
        elapsedMs += dt * 1000f
        return !done
    }

    val x get() = fromX + (toX - fromX) * eased
    /** 中途抬高一個弧度，兩端都回到原本的高度。 */
    val y get() = fromY + (toY - fromY) * eased - arc * sin(PI * eased.toDouble()).toFloat()

    /** 起飛時放大、到站前縮小，像被吸進去。 */
    val scale get() = 1f + 0.3f * sin(PI * progress.toDouble()).toFloat() - 0.25f * progress

    /** 最後三分之一才開始淡出，不然整趟都是半透明的看不清楚。 */
    val alpha get() = if (progress < 0.66f) 1f else 1f - (progress - 0.66f) / 0.34f

    /** 先快後慢：離開鍵帽要有勁道。 */
    private val eased get() = 1f - (1f - progress) * (1f - progress)
}

package tw.pinnedbopomofo.quest.effects

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.Display
import android.view.View
import java.util.Locale

/**
 * 「這塊面板做得動什麼特效」的實測探針，寫法跟 [tw.pinnedbopomofo.quest.voice.MicProbe] 一樣：
 * 只量、只記數字，不改鍵盤行為。
 *
 * 要量的三件事，文件和模擬器都看不出來，只能在頭盔上跑：
 *
 * 1. **輸入法視窗有沒有硬體加速。**沒有的話 `setRenderEffect`、`elevation` 陰影會安靜地
 *    什麼都不做——不當機、不報錯、就是沒效果。這是最容易被誤判成「做了但看不出來」的一項。
 * 2. **實際重畫得了幾 fps。**面板是合成器裡的一層，不保證跟著螢幕更新率走。
 *    低於 30 就別做連續特效，只能做按一下演一次的短動畫。
 * 3. **AGSL runtime shader 能不能編譯。**Android 13 才有，Horizon OS 是分支，要試才知道。
 *
 * 面板尺寸那次的教訓：任何平台能力都當成「這次觀察到的值」，改版或重開機後值得重測。
 */
object EffectProbe {

    const val DEFAULT_MEASURE_MS = 2_000

    /** 最小可用的 AGSL：把來源原封不動畫出來。編得過就代表這條路是通的。 */
    private const val SHADER_SOURCE = """
        uniform shader src;
        half4 main(float2 p) { return src.eval(p); }
    """

    data class Report(
        val hardwareAccelerated: Boolean?,
        val refreshRateHz: Float,
        val modes: String,
        val renderEffect: String,
        val runtimeShader: String,
        val frames: String,
    ) {
        override fun toString() = buildString {
            append("hwAccel=")
            append(hardwareAccelerated?.toString() ?: "未知")
            append(String.format(Locale.US, " refreshHz=%.1f", refreshRateHz))
            append(" modes=").append(modes)
            append(" renderEffect=").append(renderEffect)
            append(" agsl=").append(runtimeShader)
            append(" ").append(frames)
        }
    }

    /** 螢幕更新率；拿不到就回 0，呼叫端自己決定要不要信。 */
    fun refreshRate(display: Display?): Float = display?.refreshRate ?: 0f

    fun modes(display: Display?): String {
        val modes = display?.supportedModes ?: return "未知"
        return modes.joinToString("/") { String.format(Locale.US, "%.0f", it.refreshRate) }
    }

    /** 試著建一個模糊效果；失敗的原因原樣記下來，不要吞掉。 */
    fun renderEffectSupport(): String = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "API 太舊（需要 31）"
        else -> try {
            RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
            "可建立"
        } catch (error: Throwable) {
            "失敗：${error.javaClass.simpleName} ${error.message}"
        }
    }

    /** 編譯一支最小的 AGSL。編得過不代表畫得出來，但編不過就一定不行。 */
    fun runtimeShaderSupport(): String = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "API 太舊（需要 33）"
        else -> try {
            RuntimeShader(SHADER_SOURCE)
            "可編譯"
        } catch (error: Throwable) {
            "失敗：${error.javaClass.simpleName} ${error.message}"
        }
    }

    /**
     * 量真正的重畫速度：放一個會自己一直要求重畫的 View 進去，數它的 onDraw。
     *
     * 數 Choreographer 的回呼不算數——那是 app 自己的 vsync，就算面板根本沒被合成也會照跑。
     * 只有 onDraw 真的被呼叫，才代表這塊面板真的在重畫。
     */
    class ProbeView(context: Context, targetHz: Float) : View(context) {
        init {
            // View 預設可能被當成不用畫；明講我們要 onDraw
            setWillNotDraw(false)
        }

        private val stats = FrameStats(targetHz)
        private var running = false
        private var deadlineNanos = 0L
        private var onDone: ((FrameStats, Boolean) -> Unit)? = null
        /** onDraw 當下 canvas 有沒有硬體加速：最權威的那個答案。 */
        private var accelerated: Boolean? = null

        fun start(millis: Int, done: (FrameStats, Boolean) -> Unit) {
            onDone = done
            running = true
            deadlineNanos = System.nanoTime() + millis * 1_000_000L
            invalidate()
        }

        fun stop() {
            running = false
            onDone = null
        }

        override fun onDraw(canvas: Canvas) {
            if (!running) return
            if (accelerated == null) accelerated = canvas.isHardwareAccelerated
            val now = System.nanoTime()
            stats.add(now)
            if (now >= deadlineNanos) {
                running = false
                val done = onDone
                onDone = null
                // 不要在 onDraw 裡改動畫面，排到下一輪再回報
                post { done?.invoke(stats, accelerated == true) }
            } else {
                invalidate()
            }
        }
    }

    /** 不用畫面就能問到的能力；frames 要另外用 [ProbeView] 量。 */
    fun capabilities(display: Display?, hardwareAccelerated: Boolean?, frames: String) = Report(
        hardwareAccelerated = hardwareAccelerated,
        refreshRateHz = refreshRate(display),
        modes = modes(display),
        renderEffect = renderEffectSupport(),
        runtimeShader = runtimeShaderSupport(),
        frames = frames,
    )

    /** 給量到的數字一句白話結論，免得我自己看數字又看錯。 */
    fun verdict(hardwareAccelerated: Boolean?, fps: Double, dropped: Int): String = when {
        hardwareAccelerated == false -> "沒有硬體加速：模糊、陰影、shader 都不用做，只能做位移與顏色動畫"
        fps < 20 -> "重畫太慢（${fps.toInt()} fps）：連續特效不可行，只能做按一下演一次的短動畫"
        fps < 45 -> "重畫勉強（${fps.toInt()} fps）：短動畫可以，粒子與拖尾會頓"
        dropped > fps / 4 -> "掉幀偏多（${dropped} 幀）：特效要保守，一次只動一顆鍵"
        else -> "重畫順暢（${fps.toInt()} fps）：連續特效可行"
    }
}

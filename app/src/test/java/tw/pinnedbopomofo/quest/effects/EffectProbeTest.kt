package tw.pinnedbopomofo.quest.effects

import org.junit.Assert.assertTrue
import org.junit.Test

class EffectProbeTest {

    @Test
    fun `沒有硬體加速時，結論要直接說 shader 與陰影不用做`() {
        // 這一項最會被誤判：沒有硬體加速時 setRenderEffect 不會報錯，只是沒效果
        val verdict = EffectProbe.verdict(hardwareAccelerated = false, fps = 72.0, dropped = 0)
        assertTrue(verdict, verdict.contains("沒有硬體加速"))
        assertTrue(verdict, verdict.contains("shader"))
    }

    @Test
    fun `重畫太慢就不要做連續特效`() {
        val verdict = EffectProbe.verdict(hardwareAccelerated = true, fps = 12.0, dropped = 3)
        assertTrue(verdict, verdict.contains("連續特效不可行"))
    }

    @Test
    fun `順暢時才說連續特效可行`() {
        val verdict = EffectProbe.verdict(hardwareAccelerated = true, fps = 72.0, dropped = 1)
        assertTrue(verdict, verdict.contains("可行"))
        assertTrue(verdict, !verdict.contains("不可行"))
    }

    @Test
    fun `fps 夠但一直掉幀，要提醒保守一點`() {
        val verdict = EffectProbe.verdict(hardwareAccelerated = true, fps = 72.0, dropped = 30)
        assertTrue(verdict, verdict.contains("保守"))
    }
}

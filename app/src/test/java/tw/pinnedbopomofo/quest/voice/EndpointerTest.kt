package tw.pinnedbopomofo.quest.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointerTest {
    /** 依序餵進去，回傳每個非 NONE 事件發生在第幾毫秒。 */
    private fun run(endpointer: Endpointer, vararg parts: List<ShortArray>): List<Pair<Endpointer.Event, Int>> {
        val events = ArrayList<Pair<Endpointer.Event, Int>>()
        var ms = 0
        for (frame in parts.flatMap { it }) {
            ms += Endpointer.FRAME_MS
            val event = endpointer.accept(frame)
            if (event != Endpointer.Event.NONE) events += event to ms
        }
        return events
    }

    @Test
    fun `安靜、說一秒、再安靜：先開始再結束，結束在說完後約 0點9 秒`() {
        val endpointer = Endpointer()
        val events = run(endpointer, Signals.quiet(500), Signals.voice(1000), Signals.quiet(1500))
        assertEquals(listOf(Endpointer.Event.STARTED, Endpointer.Event.ENDED), events.map { it.first })
        // 開口在 500 ms，連續有聲 120 ms 才算開始
        assertEquals(620, events[0].second)
        // 說完在 1500 ms，安靜 900 ms 才算結束
        assertEquals(2400, events[1].second)
        assertFalse(endpointer.speaking)
    }

    @Test
    fun `一直沒開口，等到上限回報沒聽到說話，不會誤判開始`() {
        val events = run(Endpointer(waitMs = 3000), Signals.quiet(4000))
        // 回報一次就停：一次語音輸入只聽一段
        assertEquals(listOf(Endpointer.Event.NO_SPEECH), events.map { it.first })
        assertEquals(3200, events.first().second)
    }

    @Test
    fun `持續的風扇噪音不算說話，噪音上面有人講話才算`() {
        val fan = Signals.noise(3000, amplitude = 1500)
        assertTrue(run(Endpointer(waitMs = 10_000), fan).isEmpty())

        val endpointer = Endpointer()
        val events = run(endpointer, Signals.noise(500, amplitude = 1500), Signals.voice(800, noiseAmplitude = 1500))
        assertEquals(listOf(Endpointer.Event.STARTED), events.map { it.first })
    }

    @Test
    fun `一直講不停，到上限就結束`() {
        val events = run(Endpointer(maxSpeechMs = 2000), Signals.quiet(300), Signals.voice(3000))
        assertEquals(listOf(Endpointer.Event.STARTED, Endpointer.Event.TOO_LONG), events.map { it.first })
    }

    @Test
    fun `診斷數字對得上：安靜校正時噪音底很低、說話峰值高過門檻、開口時間正確`() {
        val endpointer = Endpointer()
        run(endpointer, Signals.quiet(500), Signals.voice(1000), Signals.quiet(1500))
        val numbers = Regex("""(\w+)=(-?[\d.]+)""").findAll(endpointer.summary())
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        assertTrue("校正噪音底 ${numbers["calDb"]} 應該很低", numbers["calDb"]!! < -40)
        assertTrue("峰值 ${numbers["peakDb"]} 應該高過門檻 ${numbers["thrDb"]}", numbers["peakDb"]!! > numbers["thrDb"]!!)
        assertEquals(620.0, numbers["startAtMs"]!!, 0.0)
    }

    @Test
    fun `按下就馬上說話：校正被說話污染時不採信，仍然偵測得到開口`() {
        // 2026-09-16 使用者回報「靈敏度時好時壞」。原因：校正那 200 ms 把說話聲當成環境噪音，
        // 門檻被拉到說話音量的四倍，整段都觸發不了；而且沒在說話時噪音底會往目前音量靠，
        // 持續講話就讓它一直卡在高點，等待期過完都回不來。
        // 2026-09-18 修正：改用校正窗內的「平均/最小」比值判斷有沒有被說話污染。
        //
        // 這裡用 Signals.speech（有音節起伏）而不是 Signals.voice（固定音量正弦波）：
        // 固定音量的訊號在能量上跟風扇噪音完全無法區分，那不是真人說話的樣子。
        val endpointer = Endpointer()
        val events = run(endpointer, Signals.speech(2000, peakAmplitude = 656), Signals.quiet(1500))
        assertTrue(
            "按下就馬上講話也要聽得到：$events　${endpointer.summary()}",
            events.any { it.first == Endpointer.Event.STARTED },
        )
    }

    @Test
    fun `正常音量說話要聽得到：用頭盔實測的數字重現`() {
        // 2026-09-16 頭盔實測：安靜時噪音底 −76 dB、說話峰值只有 −37 dB，
        // 但門檻被絕對下限壓在 −50 dB，477 框只有 53 框過門檻，拖到 5.5 秒才觸發。
        val ambient = 45      // 約 −62 dBFS 的環境底噪
        val speech = 656      // 約 −37 dBFS 的說話音量
        val endpointer = Endpointer()
        val events = run(
            endpointer,
            Signals.noise(500, amplitude = ambient),
            Signals.speech(1500, peakAmplitude = speech, noiseAmplitude = ambient),
            Signals.noise(1200, amplitude = ambient),
        )
        assertEquals(
            "這種音量應該聽得到：${endpointer.summary()}",
            listOf(Endpointer.Event.STARTED, Endpointer.Event.ENDED),
            events.map { it.first },
        )
        assertTrue("開口判定太慢：${events.first().second} ms", events.first().second < 900)
    }

    @Test
    fun `錄音剛開始的空白框不算環境噪音`() {
        // 2026-09-18 頭盔實測：voice done ... calDb=-115.4 noiseDb=-76.6
        // −115 dB 不是「很安靜的房間」，是 AudioRecord 還沒開始吐資料的空白框。
        // 真正的環境噪音是 −76.6。拿空白框當基準等於每次校正都白量。
        val endpointer = Endpointer()
        val blank = List(10) { ShortArray(Endpointer.FRAME_SAMPLES) }
        run(
            endpointer,
            blank,
            Signals.noise(500, amplitude = 45),
            Signals.speech(1500, peakAmplitude = 656, noiseAmplitude = 45),
            Signals.noise(1200, amplitude = 45),
        )
        val cal = Regex("""calDb=(-?[\d.]+)""").find(endpointer.summary())!!.groupValues[1].toDouble()
        assertTrue("校正該量到真正的環境噪音，不是空白框：${endpointer.summary()}", cal > -90)
    }

    @Test
    fun `音量條：安靜接近 0，大聲接近 1`() {
        val endpointer = Endpointer()
        endpointer.accept(Signals.quiet(20, amplitude = 5).single())
        assertTrue(endpointer.level < 0.1f)
        endpointer.accept(Signals.voice(20, amplitude = 30000).single())
        assertTrue(endpointer.level > 0.9f)
    }
}

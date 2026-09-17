package tw.pinnedbopomofo.quest.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionTest {
    private val background = ArrayDeque<Runnable>()
    private val main = ArrayDeque<Runnable>()
    private val states = ArrayList<VoiceState>()

    /** 假的串流引擎：每餵一框多吐一個字，finish 給完整句子。 */
    private class FakeEngine(private val failOnAccept: Boolean = false) : SpeechEngine {
        var started = 0
        var cancelled = 0
        var samplesFed = 0

        override fun startUtterance(): SpeechEngine.Utterance {
            started += 1
            return object : SpeechEngine.Utterance {
                private var words = 0
                override fun accept(samples: FloatArray, sampleRate: Int): String {
                    if (failOnAccept) error("模型壞了")
                    samplesFed += samples.size
                    words += 1
                    return SENTENCE.take(minOf(words / 20, SENTENCE.length))
                }

                override fun finish() = " $SENTENCE "
                override fun cancel() {
                    cancelled += 1
                }
            }
        }

        companion object {
            const val SENTENCE = "今天天氣很好"
        }
    }

    private fun session(engine: SpeechEngine?, convert: (String) -> String = { it }) = VoiceSession(
        engine = engine,
        recognize = { background += it },
        deliver = { main += it },
        onState = { states += it },
        convert = convert,
    )

    private fun runMain() {
        while (main.isNotEmpty()) main.removeFirst().run()
    }

    private fun runAll() {
        while (background.isNotEmpty() || main.isNotEmpty()) {
            background.removeFirstOrNull()?.run()
            runMain()
        }
    }

    private fun speak(session: VoiceSession) =
        (Signals.quiet(500) + Signals.voice(1000) + Signals.quiet(1200)).forEach(session::accept)

    @Test
    fun `講話過程中會邊出字，說完拿到完整句子`() {
        val engine = FakeEngine()
        val session = session(engine)
        speak(session)
        runAll()
        val partials = states.filterIsInstance<VoiceState.Partial>().map { it.text }
        assertTrue("沒有中途結果：$states", partials.isNotEmpty())
        assertTrue("中途結果要越來越長：$partials", partials == partials.sortedBy { it.length })
        val done = states.last() as VoiceState.Done
        assertEquals("今天天氣很好", done.text)
        assertEquals(1, engine.started)
    }

    @Test
    fun `辨識不在錄音執行緒上跑`() {
        val engine = FakeEngine()
        val session = session(engine)
        speak(session)
        assertEquals("錄音執行緒上不該碰引擎", 0, engine.started)
        runAll()
        assertTrue(engine.started > 0)
    }

    @Test
    fun `字頭不會被切掉：開口前的緩衝也要餵給引擎`() {
        val engine = FakeEngine()
        val session = session(engine)
        speak(session)
        runAll()
        // 開口 500 ms、判斷說完 2400 ms，共 1900 ms，再加上開口前的緩衝
        val fedMs = engine.samplesFed * 1000 / Endpointer.SAMPLE_RATE
        assertTrue("只餵了 $fedMs ms", fedMs >= 2000)
    }

    @Test
    fun `沒安裝模型：告訴使用者聽到了幾秒，不會當機`() {
        val session = session(engine = null)
        (Signals.quiet(500) + Signals.voice(1500) + Signals.quiet(1200)).forEach(session::accept)
        runAll()
        val heard = states.last() as VoiceState.Heard
        assertTrue("聽到 ${heard.speechMs} ms", heard.speechMs in 1500..3500)
        assertTrue(session.finished)
    }

    @Test
    fun `還沒開口就按停止：沒聽到說話，也不叫辨識引擎`() {
        val engine = FakeEngine()
        val session = session(engine)
        Signals.quiet(800).forEach(session::accept)
        session.stop()
        runAll()
        assertEquals(VoiceState.NoSpeech, states.last())
        assertEquals(0, engine.started)
    }

    @Test
    fun `講到一半收起鍵盤：引擎資源要釋放，結果不送回來`() {
        val engine = FakeEngine()
        val session = session(engine)
        // 沒有結尾的安靜，所以這段語音還沒結束、引擎還開著
        (Signals.quiet(500) + Signals.voice(1000)).forEach(session::accept)
        runAll()
        states.clear()
        session.cancel()
        runAll()
        assertEquals("沒有釋放引擎", 1, engine.cancelled)
        assertTrue("取消後還收到 $states", states.isEmpty())
    }

    @Test
    fun `取消時把還沒辨識的音框丟掉，不要白算`() {
        val engine = FakeEngine()
        val session = session(engine)
        speak(session)
        // 辨識工作還排在佇列裡就取消
        session.cancel()
        runAll()
        assertEquals("取消後還在辨識", 0, engine.samplesFed)
        assertTrue("取消後還收到 $states", states.none { it is VoiceState.Partial || it is VoiceState.Done })
    }

    @Test
    fun `後處理（簡轉繁）要套用在中途與最終結果上`() {
        val threads = mutableSetOf<String>()
        val session = session(FakeEngine(), convert = { text ->
            threads += Thread.currentThread().name
            text.replace("天", "天*")
        })
        speak(session)
        runAll()
        val partials = states.filterIsInstance<VoiceState.Partial>().map { it.text }
        assertTrue("中途結果沒有轉換：$partials", partials.all { !it.contains("天") || it.contains("天*") })
        assertEquals("今天*天*氣很好", (states.last() as VoiceState.Done).text)
    }

    @Test
    fun `辨識引擎出錯：回報失敗原因`() {
        val session = session(FakeEngine(failOnAccept = true))
        speak(session)
        runAll()
        assertTrue("最後狀態是 ${states.last()}", states.any { it is VoiceState.Failed })
        assertEquals("模型壞了", (states.first { it is VoiceState.Failed } as VoiceState.Failed).reason)
    }
}

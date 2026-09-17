package tw.pinnedbopomofo.quest.voice

/**
 * 離線語音辨識引擎。一段語音對應一個 [Utterance]，所有方法都在辨識執行緒上呼叫，不在錄音或主執行緒。
 *
 * 串流式：邊餵邊出字，[accept] 回傳目前為止的文字。非串流的引擎照樣可以實作，
 * 只要 [accept] 一律回傳空字串、等 [finish] 才給結果。
 */
interface SpeechEngine {
    fun startUtterance(): Utterance

    interface Utterance {
        /** 餵一段取樣（-1..1）；回傳目前為止辨識出的文字，還沒有就回空字串。 */
        fun accept(samples: FloatArray, sampleRate: Int): String

        /** 說完了：回傳最終文字並釋放資源。 */
        fun finish(): String

        /** 丟棄這一段並釋放資源。 */
        fun cancel()
    }
}

sealed interface VoiceState {
    /** 正在聽；[speaking] 表示音量判斷認為已經開口。 */
    data class Listening(val level: Float, val speaking: Boolean) : VoiceState

    /** 邊講邊出字的中途結果。 */
    data class Partial(val text: String) : VoiceState

    data class Recognizing(val speechMs: Int) : VoiceState

    data class Done(val text: String, val speechMs: Int) : VoiceState

    /** 聽到說話，但沒有安裝辨識模型，所以沒有轉成文字。 */
    data class Heard(val speechMs: Int) : VoiceState

    data object NoSpeech : VoiceState

    data class Failed(val reason: String) : VoiceState
}

/**
 * 一次語音輸入：收麥克風音框 → 判斷說完了沒 → 串流餵給辨識引擎 → 結果送回主執行緒。
 *
 * [accept] 在錄音執行緒呼叫，[stop] 與 [cancel] 在主執行緒呼叫。
 * 辨識工作排到 [recognize]，同一時間只會有一個排空工作在跑，所以引擎只被單一執行緒碰。
 * 狀態經 [deliver] 送回主執行緒；取消之後就不再回呼。聲音只在記憶體裡流過，不寫檔。
 */
class VoiceSession(
    private val engine: SpeechEngine?,
    private val recognize: (Runnable) -> Unit,
    private val deliver: (Runnable) -> Unit,
    private val onState: (VoiceState) -> Unit,
    /** 辨識結果的後處理（簡轉繁）；在辨識執行緒上跑，不佔主執行緒。 */
    private val convert: (String) -> String = { it },
    private val endpointer: Endpointer = Endpointer(),
) {
    private sealed interface Work {
        class Samples(val frames: List<ShortArray>) : Work
        class Finish(val speechMs: Int) : Work
        data object Cancel : Work
    }

    private val lock = Any()

    /** 開口前的最後幾框：音量判斷要連續有聲一小段才算開始，字頭在那之前。 */
    private val preRoll = ArrayDeque<ShortArray>()
    private val pending = ArrayDeque<Work>()
    private var draining = false
    private var utterance: SpeechEngine.Utterance? = null
    private var partial = ""
    private var sampleCount = 0
    private var framesSinceUpdate = 0

    @Volatile
    private var cancelled = false

    var finished = false
        private set

    @Synchronized
    fun accept(frame: ShortArray) {
        if (finished) return
        val event = endpointer.accept(frame)
        val copy = frame.copyOf()
        when {
            event == Endpointer.Event.STARTED -> {
                val onset = preRoll.toList() + copy
                preRoll.clear()
                send(onset)
            }
            endpointer.speaking || event == Endpointer.Event.ENDED || event == Endpointer.Event.TOO_LONG -> send(listOf(copy))
            else -> {
                preRoll += copy
                while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            }
        }
        when (event) {
            Endpointer.Event.ENDED, Endpointer.Event.TOO_LONG, Endpointer.Event.NO_SPEECH -> finish()
            Endpointer.Event.STARTED, Endpointer.Event.NONE -> {
                // 每框都更新會讓鍵盤一秒重畫 50 次；音量條每 100 ms 更新一次就夠
                framesSinceUpdate += 1
                if (event == Endpointer.Event.STARTED || framesSinceUpdate >= UPDATE_EVERY_FRAMES) {
                    framesSinceUpdate = 0
                    post(VoiceState.Listening(endpointer.level, endpointer.speaking))
                }
            }
        }
    }

    /** 使用者再按一次麥克風：已經開口就送去辨識，還沒開口就當作沒聽到。 */
    @Synchronized
    fun stop() {
        if (!finished) finish()
    }

    /** 收起鍵盤、離開輸入框：丟掉這次，進行中的辨識也不送回結果。 */
    @Synchronized
    fun cancel() {
        finished = true
        cancelled = true
        preRoll.clear()
        sampleCount = 0
        // 還沒辨識的音框直接丟掉：使用者已經放棄了，不用白算
        enqueue(Work.Cancel, dropPending = true)
    }

    private fun send(frames: List<ShortArray>) {
        sampleCount += frames.sumOf { it.size }
        if (engine != null) enqueue(Work.Samples(frames))
    }

    private fun finish() {
        finished = true
        preRoll.clear()
        val speechMs = sampleCount * 1000 / Endpointer.SAMPLE_RATE
        sampleCount = 0
        if (speechMs <= 0) {
            post(VoiceState.NoSpeech)
            return
        }
        if (engine == null) {
            post(VoiceState.Heard(speechMs))
            return
        }
        post(VoiceState.Recognizing(speechMs))
        enqueue(Work.Finish(speechMs))
    }

    private fun enqueue(work: Work, dropPending: Boolean = false) {
        val start = synchronized(lock) {
            if (dropPending) pending.clear()
            pending += work
            if (draining) false else { draining = true; true }
        }
        if (start) recognize { drain() }
    }

    /** 一次把排隊的工作做完；同一時間只有一個排空工作，引擎因此只被單一執行緒碰。 */
    private fun drain() {
        while (true) {
            val work = synchronized(lock) {
                val next = pending.removeFirstOrNull()
                if (next == null) draining = false
                next
            } ?: return
            handle(work)
        }
    }

    private fun handle(work: Work) {
        val engine = engine ?: return
        try {
            when (work) {
                is Work.Samples -> {
                    val current = utterance ?: engine.startUtterance().also { utterance = it }
                    for (frame in work.frames) {
                        val text = convert(current.accept(toFloat(frame), Endpointer.SAMPLE_RATE))
                        if (text.isNotEmpty() && text != partial) {
                            partial = text
                            post(VoiceState.Partial(text))
                        }
                    }
                }
                is Work.Finish -> {
                    val text = convert(utterance?.finish().orEmpty())
                    utterance = null
                    post(VoiceState.Done(text.trim(), work.speechMs))
                }
                Work.Cancel -> {
                    utterance?.cancel()
                    utterance = null
                }
            }
        } catch (e: Exception) {
            utterance = null
            post(VoiceState.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun toFloat(frame: ShortArray): FloatArray {
        val samples = FloatArray(frame.size)
        for (index in frame.indices) samples[index] = frame[index] / 32768f
        return samples
    }

    private fun post(state: VoiceState) = deliver {
        if (!cancelled) onState(state)
    }

    companion object {
        private const val PRE_ROLL_FRAMES = 15
        private const val UPDATE_EVERY_FRAMES = 5
    }
}

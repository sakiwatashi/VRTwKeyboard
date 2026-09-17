package tw.pinnedbopomofo.quest

/**
 * 同一個程序只載入一次，之後要的人共用同一份。
 *
 * 系統可能很快建立兩個輸入法服務實體（2026-09-16 頭盔實測：onCreate 相隔 26 ms），
 * 各自載入詞庫會讓兩份同時搶 CPU，冷啟動拉長到約 9 秒。
 *
 * [request] 與 [Request.cancel] 都要在同一條執行緒（主執行緒）呼叫；
 * [load] 在 [startLoad] 給的背景執行緒跑，結果經 [deliver] 送回主執行緒再回呼。
 */
class SharedLoader<T : Any>(
    private val load: () -> T,
    private val startLoad: (Runnable) -> Unit,
    private val deliver: (Runnable) -> Unit,
) {
    inner class Request internal constructor(internal val onReady: (T) -> Unit) {
        /** 服務結束時取消：載入完成時才不會回頭碰已經結束的服務。 */
        fun cancel() {
            waiting.remove(this)
        }
    }

    private var value: T? = null
    private var loading = false
    private val waiting = LinkedHashSet<Request>()

    /** 已經載入完就立刻回呼；否則等載入完成。 */
    fun request(onReady: (T) -> Unit): Request {
        val request = Request(onReady)
        value?.let {
            onReady(it)
            return request
        }
        waiting += request
        if (!loading) {
            loading = true
            startLoad {
                val result = load()
                deliver {
                    value = result
                    loading = false
                    val ready = waiting.toList()
                    waiting.clear()
                    ready.forEach { it.onReady(result) }
                }
            }
        }
        return request
    }
}

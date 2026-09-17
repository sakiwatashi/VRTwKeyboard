package tw.pinnedbopomofo.quest.engine

/** 載入詞庫時各段花的時間，同名的段落累加（例如好幾份小檔的解析）。只給效能記錄用，不影響載入結果。 */
class LoadTimer {
    private val spans = LinkedHashMap<String, Long>()

    val names: Set<String> get() = spans.keys

    fun add(name: String, nanos: Long) {
        spans[name] = (spans[name] ?: 0L) + nanos
    }

    inline fun <T> measure(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            add(name, System.nanoTime() - start)
        }
    }

    /** 依第一次記到的順序，名稱 → 毫秒。 */
    fun millis(): Map<String, Double> = spans.mapValues { it.value / 1_000_000.0 }
}

internal inline fun <T> LoadTimer?.timed(name: String, block: () -> T): T =
    if (this == null) block() else measure(name, block)

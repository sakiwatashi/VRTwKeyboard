package tw.pinnedbopomofo.quest.net

/**
 * 版本字串比較。GitHub Release 的 tag 可能寫成 `v0.2`、`0.2.1`、`quest-keyboard-v0.3`，
 * 所以只取數字段落來比。
 *
 * 分開成純函式的理由：版本比較錯掉的症狀很難查——要嘛一直說有新版
 * （使用者每次開都被提示），要嘛明明有新版卻永遠不提示（使用者完全不知道）。
 * 兩種都不會當機，只會安靜地錯。
 */
object Version {

    /** 從 tag 抽出數字段落。抽不到就回空清單。 */
    fun parse(tag: String?): List<Int> {
        if (tag.isNullOrBlank()) return emptyList()
        return Regex("""\d+""").findAll(tag).map { it.value.toIntOrNull() ?: 0 }.toList()
    }

    /**
     * [remote] 比 [local] 新就回 true。
     *
     * 段數不同時短的補 0：`0.2` 與 `0.2.0` 視為相同，`0.2.1` 比 `0.2` 新。
     * 任一邊解析不出數字就回 false——寧可不提示，也不要對著看不懂的 tag 亂提示。
     */
    fun isNewer(remote: String?, local: String?): Boolean {
        val a = parse(remote)
        val b = parse(local)
        if (a.isEmpty() || b.isEmpty()) return false
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}

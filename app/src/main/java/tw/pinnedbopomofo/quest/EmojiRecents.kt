package tw.pinnedbopomofo.quest

/** 最近用過的表情，最新的排最前面。表情可能由好幾個字元組成（👨‍👩‍👧），所以整串當一個項目。 */
class EmojiRecents(initial: List<String> = emptyList(), private val capacity: Int = CAPACITY) {
    private val items = ArrayList(initial.filter { it.isNotEmpty() }.distinct().take(capacity))

    val list: List<String> get() = items

    fun use(emoji: String) {
        items.remove(emoji)
        items.add(0, emoji)
        while (items.size > capacity) items.removeAt(items.lastIndex)
    }

    /** 表情裡不會有換行，拿換行當分隔。 */
    fun serialize() = items.joinToString("\n")

    companion object {
        const val CAPACITY = 30

        fun parse(text: String?) = EmojiRecents(text?.split("\n").orEmpty())
    }
}

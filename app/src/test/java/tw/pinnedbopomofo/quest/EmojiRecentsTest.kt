package tw.pinnedbopomofo.quest

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiRecentsTest {

    @Test
    fun `再用一次的表情移到最前面，不會重複`() {
        val recents = EmojiRecents()
        recents.use("😀")
        recents.use("👍")
        recents.use("😀")
        assertEquals(listOf("😀", "👍"), recents.list)
    }

    @Test
    fun `最多記住三十個，最舊的先丟`() {
        val recents = EmojiRecents()
        val all = EmojiCatalog.categories.first().emojis.take(EmojiRecents.CAPACITY + 5)
        all.forEach(recents::use)
        assertEquals(EmojiRecents.CAPACITY, recents.list.size)
        assertEquals(all.last(), recents.list.first())
        assertEquals(all.reversed().take(EmojiRecents.CAPACITY), recents.list)
    }

    @Test
    fun `存檔再讀回來內容一樣，多字元組成的表情也完整`() {
        val recents = EmojiRecents()
        listOf("❤️", "👨‍👩‍👧", "🖐️").forEach(recents::use)
        assertEquals(recents.list, EmojiRecents.parse(recents.serialize()).list)
        assertEquals(emptyList<String>(), EmojiRecents.parse(null).list)
    }
}

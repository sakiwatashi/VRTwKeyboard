package tw.pinnedbopomofo.quest.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * 快照（EngineSnapshot）必須跟 JSON 詞庫逐字等價：App 啟動時讀的是快照，測試與資料來源卻是 JSON。
 *
 * 改了 assets 裡的 JSON 或 Lexicon／Predictor 的載入邏輯之後，第一支測試會紅，
 * 照失敗訊息重新產生快照即可。
 */
class EngineSnapshotTest {

    @Test
    fun `assets 裡的快照跟 JSON 詞庫同步`() {
        val expected = EngineSnapshot.encode(fromJson)
        val file = File(assetsDirectory(), SNAPSHOT)
        if (System.getenv("REGENERATE_ENGINE_SNAPSHOT") == "1") {
            file.outputStream().use { EngineSnapshot.write(fromJson, it) }
            println("已重新產生 ${file.canonicalPath}（${file.length()} bytes）")
        }
        check(file.isFile) { "找不到快照：${file.canonicalPath}。$REGENERATE" }
        val committed = GZIPInputStream(file.inputStream()).use { it.readBytes() }
        assertArrayEquals("快照跟 JSON 不同步。$REGENERATE", expected, committed)
    }

    @Test
    fun `每一個讀音查到的候選與權重都跟 JSON 版相同`() {
        val json = fromJson.lexicon
        val snapshot = fromSnapshot.lexicon
        val readings = json.entries.keys + json.extra.keys
        assertTrue("讀音太少，詞庫沒載入：${readings.size}", readings.size > 100_000)
        for (reading in readings) {
            val syllables = reading.split(' ')
            val expected = json.candidates(syllables, Int.MAX_VALUE)
            assertEquals(reading, expected, snapshot.candidates(syllables, Int.MAX_VALUE))
            for (phrase in expected) {
                assertEquals("$reading $phrase", json.weight(syllables, phrase), snapshot.weight(syllables, phrase))
            }
        }
    }

    @Test
    fun `不完整音節的查詢跟 JSON 版相同`() {
        val json = fromJson.lexicon
        val snapshot = fromSnapshot.lexicon
        assertEquals(json.byLeadingSymbols.keys, snapshot.byLeadingSymbols.keys)
        // 索引的每一組「各音節第一個符號」本身就是一個只打聲母的查詢
        for (key in json.byLeadingSymbols.keys) {
            val patterns = key.split(' ')
            assertEquals(key, json.candidates(patterns, 50), snapshot.candidates(patterns, 50))
        }
        for (reading in listOf(listOf("ㄉㄚ", "ㄐㄧㄚ"), listOf("ㄓㄜ", "ㄗㄨㄛ"), listOf("ㄊㄞ", "ㄅ"))) {
            assertEquals(reading.toString(), json.candidates(reading, 50), snapshot.candidates(reading, 50))
        }
    }

    @Test
    fun `同音修正用的單字讀音順序相同`() {
        val characters = fromJson.lexicon.entries.values.asSequence()
            .flatten().map { it.phrase }.filter { it.characterCount() == 1 }.toSet()
        assertTrue(characters.size > 5_000)
        for (character in characters) {
            assertEquals(character, fromJson.lexicon.readingsOf(character), fromSnapshot.lexicon.readingsOf(character))
        }
    }

    @Test
    fun `聯想詞表完全相同`() {
        assertTrue(fromJson.predictor.next.size > 1_000)
        assertEquals(fromJson.predictor.next, fromSnapshot.predictor.next)
    }

    @Test
    fun `壞掉的快照回傳 null，讓 App 退回 JSON`() {
        val good = gzip(EngineSnapshot.encode(fromJson))
        assertNotNull("完好的快照要讀得出來，否則下面幾項什麼都沒驗", EngineSnapshot.read(opener(good), SNAPSHOT))

        val raw = EngineSnapshot.encode(fromJson)
        // 截斷在中間、只少結尾一個位元組、版本號不對、不是快照、空檔、缺檔
        assertNull(EngineSnapshot.read(opener(gzip(raw.copyOf(raw.size / 2))), SNAPSHOT))
        assertNull(EngineSnapshot.read(opener(gzip(raw.copyOf(raw.size - 1))), SNAPSHOT))
        assertNull(EngineSnapshot.read(opener(gzip(raw.copyOf().also { it[8] = 99 })), SNAPSHOT))
        assertNull(EngineSnapshot.read(opener("{\"entries\":{}}".toByteArray()), SNAPSHOT))
        assertNull(EngineSnapshot.read(opener(ByteArray(0)), SNAPSHOT))
        assertNull(EngineSnapshot.read({ null }, SNAPSHOT))
    }

    @Test
    fun `讀快照時有記到兩段時間`() {
        val timer = LoadTimer()
        assertNotNull(EngineSnapshot.read(opener(gzip(EngineSnapshot.encode(fromJson))), SNAPSHOT, timer))
        assertEquals(setOf("snapshot.read", "snapshot.decode"), timer.names)
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun opener(bytes: ByteArray): DataOpener = { name -> if (name == SNAPSHOT) ByteArrayInputStream(bytes) else null }

    companion object {
        private const val SNAPSHOT = "engine_snapshot.bin.gz"
        private const val REGENERATE =
            "重新產生：\$env:REGENERATE_ENGINE_SNAPSHOT='1'; gradle :app:testDebugUnitTest --tests '*EngineSnapshotTest*' --rerun-tasks"

        /** 快照一定跟 App 的 assets 放在一起；ENGINE_TEST_DATA 可能指向刻意缺檔的副本，這裡不用它。 */
        private fun assetsDirectory(): File =
            listOf("src/main/assets", "app/src/main/assets").map(::File).firstOrNull { it.isDirectory }
                ?: error("找不到 app/src/main/assets")

        private val fromJson: Engine by lazy {
            val data = assetsDirectory()
            Engine.load({ name -> File(data, name).takeIf { it.isFile }?.inputStream() })
        }

        /** 從記憶體裡剛編出來的快照讀回來，不依賴 assets 裡那份是否已更新。 */
        private val fromSnapshot: Engine by lazy { EngineSnapshot.decode(EngineSnapshot.encode(fromJson)) }
    }
}

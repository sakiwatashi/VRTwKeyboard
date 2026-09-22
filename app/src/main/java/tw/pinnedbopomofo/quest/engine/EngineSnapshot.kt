package tw.pinnedbopomofo.quest.engine

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 把「已經建好的」詞庫與聯想詞表存成二進位快照，啟動時直接讀回來，不必再解析 JSON。
 *
 * 頭盔實測（2026-09-16，docs/perf-*.log）冷啟動 6.7 秒裡，org.json 解析、轉成查詢表、
 * 建首符號索引、聯想詞的整條流程合計約 4.6 秒；快照裡存的就是這些步驟的結果。
 *
 * 快照由單元測試從 assets 的 JSON 產生（見 EngineSnapshotTest），JSON 仍是唯一的資料來源；
 * 兩者不同步時那支測試會紅。
 *
 * **寫出的順序就是記憶體裡各表的走訪順序，不排序。** 同分的候選與同音修正的讀音順序
 * 取決於 HashMap 的走訪順序（見 Lexicon.build 與 characterReadings），
 * 照原順序寫、用同樣大小的表讀回來，才能保證查詢結果跟從 JSON 載入的逐字相同。
 */
object EngineSnapshot {
    /** 改了格式就加一；讀到別的版本一律當作沒有快照，退回 JSON。 */
    private const val FORMAT_VERSION = 1
    private val MAGIC = "VRTWSNAP".toByteArray(Charsets.US_ASCII)
    private val TRAILER = "END!".toByteArray(Charsets.US_ASCII)

    /** 未壓縮的快照內容。測試拿它比對，壓縮後的位元組會因 zlib 版本不同而不同。 */
    fun encode(engine: Engine): ByteArray {
        val out = Writer()
        out.bytes(MAGIC)
        out.int(FORMAT_VERSION)

        val lexicon = engine.lexicon
        out.count(lexicon.entries.size)
        for ((reading, rows) in lexicon.entries) {
            out.string(reading)
            out.count(rows.size)
            for (row in rows) {
                out.string(row.phrase)
                out.int(row.weight)
            }
        }
        out.nested(lexicon.extra)
        out.nested(lexicon.taiwan)
        out.nested(lexicon.polyphones)
        out.count(lexicon.demoted.size)
        for (character in lexicon.demoted) out.string(character)

        // 索引裡的讀音全都是 entries 或 extra 的鍵，存編號就好，讀回來不必再解碼一次字串
        val ids = HashMap<String, Int>()
        for (reading in lexicon.entries.keys) ids.putIfAbsent(reading, ids.size)
        val extraOnly = lexicon.extra.keys.filter { it !in ids }
        for (reading in extraOnly) ids[reading] = ids.size
        out.count(extraOnly.size)
        for (reading in extraOnly) out.string(reading)
        out.count(lexicon.byLeadingSymbols.size)
        for ((key, readings) in lexicon.byLeadingSymbols) {
            out.string(key)
            out.count(readings.size)
            for (reading in readings) out.count(ids.getValue(reading))
        }

        val next = engine.predictor.next
        out.count(next.size)
        for ((head, rests) in next) {
            out.string(head)
            out.count(rests.size)
            for (rest in rests) out.string(rest)
        }

        out.bytes(TRAILER)
        return out.toByteArray()
    }

    /** 寫成 gzip，放進 assets 的就是這個。 */
    fun write(engine: Engine, output: OutputStream) {
        GZIPOutputStream(output).use { it.write(encode(engine)) }
    }

    /**
     * 讀快照。缺檔、壞檔、版本不符、截斷都回傳 null，由呼叫端退回 JSON：
     * 快照只是加速，不能讓輸入法因此起不來。
     */
    fun read(open: DataOpener, name: String, timer: LoadTimer? = null): Engine? = try {
        val bytes = timer.timed("snapshot.read") {
            open(name)?.let { stream ->
                val buffered = BufferedInputStream(stream)
                buffered.mark(2)
                val gzipped = buffered.read() == 0x1f && buffered.read() == 0x8b
                buffered.reset()
                (if (gzipped) GZIPInputStream(buffered, 1 shl 16) else buffered).use { it.readBytes() }
            }
        }
        bytes?.let { timer.timed("snapshot.decode") { decode(it) } }
    } catch (e: Exception) {
        null
    }

    /** 格式不對就丟例外；[read] 會把它變成 null。 */
    internal fun decode(bytes: ByteArray): Engine {
        val input = Reader(bytes)
        check(input.matches(MAGIC)) { "不是詞庫快照" }
        check(input.int() == FORMAT_VERSION) { "快照格式版本不符" }

        val readingCount = input.count()
        val readings = arrayOfNulls<String>(readingCount)
        val entries = HashMap<String, List<Lexicon.Row>>(capacityFor(readingCount))
        for (i in 0 until readingCount) {
            val reading = input.string()
            val rowCount = input.count()
            val rows = ArrayList<Lexicon.Row>(rowCount)
            repeat(rowCount) { rows += Lexicon.Row(input.string(), input.int()) }
            readings[i] = reading
            entries[reading] = rows
        }
        val extra = input.nested()
        val taiwan = input.nested()
        val polyphones = input.nested()
        val demotedCount = input.count()
        val demoted = HashSet<String>(capacityFor(demotedCount))
        repeat(demotedCount) { demoted += input.string() }

        val extraOnlyCount = input.count()
        val allReadings = readings.copyOf(readingCount + extraOnlyCount)
        for (i in 0 until extraOnlyCount) allReadings[readingCount + i] = input.string()
        val groupCount = input.count()
        val index = HashMap<String, List<String>>(capacityFor(groupCount))
        repeat(groupCount) {
            val key = input.string()
            val size = input.count()
            val group = ArrayList<String>(size)
            repeat(size) { group += allReadings[input.count()]!! }
            index[key] = group
        }

        val headCount = input.count()
        val next = HashMap<String, List<String>>(capacityFor(headCount))
        repeat(headCount) {
            val head = input.string()
            val size = input.count()
            val rests = ArrayList<String>(size)
            repeat(size) { rests += input.string() }
            next[head] = rests
        }

        check(input.matches(TRAILER) && input.atEnd()) { "快照結尾不對，可能被截斷" }
        val lexicon = Lexicon(entries, extra, taiwan, polyphones, demoted, index)
        return Engine(lexicon, Predictor(next))
    }

    /** 讓 HashMap 一次配好、不必邊放邊擴表；結果跟從空表長到這個大小相同。 */
    private fun capacityFor(size: Int) = (size / 0.75f).toInt() + 1

    private class Writer {
        private val buffer = ByteArrayOutputStream(1 shl 22)

        fun bytes(value: ByteArray) = buffer.write(value)

        /** 非負整數，varint。 */
        fun count(value: Int) {
            require(value >= 0)
            var rest = value
            while (rest >= 0x80) {
                buffer.write((rest and 0x7f) or 0x80)
                rest = rest ushr 7
            }
            buffer.write(rest)
        }

        /** 有號整數（權重可能是負的），zigzag 後當 varint 寫。 */
        fun int(value: Int) {
            val zigzag = (value shl 1) xor (value shr 31)
            var rest = zigzag
            while (rest and 0x7f.inv() != 0) {
                buffer.write((rest and 0x7f) or 0x80)
                rest = rest ushr 7
            }
            buffer.write(rest)
        }

        fun string(value: String) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            count(encoded.size)
            buffer.write(encoded)
        }

        /** 內層的走訪順序也照原樣寫，見檔頭說明。 */
        fun nested(map: Map<String, Map<String, Int>>) {
            count(map.size)
            for ((key, inner) in map) {
                string(key)
                count(inner.size)
                for ((word, weight) in inner) {
                    string(word)
                    int(weight)
                }
            }
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    private class Reader(private val bytes: ByteArray) {
        private var position = 0

        fun atEnd() = position == bytes.size

        fun matches(expected: ByteArray): Boolean {
            if (position + expected.size > bytes.size) return false
            for (i in expected.indices) if (bytes[position + i] != expected[i]) return false
            position += expected.size
            return true
        }

        private fun varint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                check(shift < 35) { "varint 太長" }
                val byte = bytes[position++].toInt()
                result = result or ((byte and 0x7f) shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
        }

        fun count(): Int = varint().also { check(it >= 0) { "數量是負的" } }

        fun int(): Int {
            val zigzag = varint()
            return (zigzag ushr 1) xor -(zigzag and 1)
        }

        fun string(): String {
            val length = count()
            check(position + length <= bytes.size) { "字串超出檔尾" }
            return String(bytes, position, length, Charsets.UTF_8).also { position += length }
        }

        /** 外層用 HashMap（只拿來查）；內層要保留順序，用 LinkedHashMap。 */
        fun nested(): Map<String, Map<String, Int>> {
            val size = count()
            val map = HashMap<String, Map<String, Int>>(capacityFor(size))
            repeat(size) {
                val key = string()
                val innerSize = count()
                val inner = LinkedHashMap<String, Int>(capacityFor(innerSize))
                repeat(innerSize) { inner[string()] = int() }
                map[key] = inner
            }
            return map
        }
    }
}

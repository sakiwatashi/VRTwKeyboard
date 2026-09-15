package tw.pinnedbopomofo.quest.engine

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** 依檔名開啟詞庫資料；App 裡是 assets，測試裡是 pime-bopomofo-core 的資料夾。缺檔回傳 null。 */
typealias DataOpener = (String) -> InputStream?

/**
 * 缺檔或壞檔都當作沒有這份資料：排序用的資料不值得讓輸入法起不來。
 *
 * 是否 gzip 看檔頭而不是副檔名：打包進 APK 時 assets 的 .gz 會被去掉副檔名，
 * 同一份資料在測試裡叫 reading_phrases.json.gz，在 App 裡卻叫 reading_phrases.json。
 */
internal fun readJson(open: DataOpener, name: String): JSONObject? = try {
    open(name)?.let { stream ->
        val buffered = BufferedInputStream(stream)
        buffered.mark(2)
        val gzipped = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val input = if (gzipped) GZIPInputStream(buffered) else buffered
        JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
} catch (e: Exception) {
    null
}

/** 以 Unicode 字元切開。詞庫裡有擴充區漢字，用 String.length 會把一個字算成兩個。 */
internal fun String.characters(): List<String> =
    codePoints().toArray().map { String(Character.toChars(it)) }

internal fun String.characterCount() = codePointCount(0, length)

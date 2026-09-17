package tw.pinnedbopomofo.quest.net

import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 下載語音模型與 APK 更新。
 *
 * 這是這個 App **唯一**用到網路的地方。輸入法服務本身不碰它——
 * 下載只發生在使用者從設定畫面按下去的時候。
 *
 * 每個檔案都比對釘死的 SHA256；不符就刪掉，不留半個壞檔在那裡假裝安裝好了。
 */
object Downloader {

    private const val TAG = "ZhuyinIme"
    private const val BUFFER = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000

    sealed interface Result {
        data object Done : Result
        data class Failed(val reason: String) : Result
        data object Cancelled : Result
    }

    /** 呼叫端用來中止；設成 true 之後下一個緩衝區就會停。 */
    class Cancel {
        @Volatile
        var requested = false
    }

    /**
     * 把 [asset] 下載到 [target]。已經存在且雜湊相符就直接回 [Result.Done]，不重下。
     *
     * [onProgress] 在下載執行緒上被呼叫，呼叫端自己切回 UI 執行緒。
     */
    fun fetch(
        asset: Asset,
        target: File,
        cancel: Cancel = Cancel(),
        onProgress: (Progress) -> Unit = {},
    ): Result {
        target.parentFile?.mkdirs()

        when (val plan = Resume.plan(if (target.isFile) target.length() else 0L, asset.bytes)) {
            is Resume.Plan.Verify -> {
                onProgress(Progress(asset.bytes, asset.bytes))
                return if (matches(target, asset.sha256)) {
                    Log.d(TAG, "download ${asset.name} 已存在且雜湊相符")
                    Result.Done
                } else {
                    Log.d(TAG, "download ${asset.name} 大小對但雜湊不符，重下")
                    target.delete()
                    fetch(asset, target, cancel, onProgress)
                }
            }
            is Resume.Plan.Restart -> target.delete()
            is Resume.Plan.Continue -> if (plan.offset > 0L) {
                Log.d(TAG, "download ${asset.name} 從 ${plan.offset} 續傳")
            }
        }

        val offset = if (target.isFile) target.length() else 0L
        val connection = try {
            open(asset.url, Resume.rangeHeader(offset))
        } catch (error: Exception) {
            return Result.Failed("連線失敗：${error.javaClass.simpleName}")
        }

        try {
            val code = connection.responseCode
            // 206 = 續傳成功；200 = 伺服器不支援 Range，整個重來
            val appending = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !appending) {
                return Result.Failed("HTTP $code")
            }
            if (!appending && offset > 0L) {
                Log.d(TAG, "download ${asset.name} 伺服器不支援續傳，從頭下載")
                target.delete()
            }

            val already = if (appending) offset else 0L
            val total = if (asset.bytes > 0L) asset.bytes else already + connection.contentLengthLong
            var done = already
            onProgress(Progress(done, total))

            connection.inputStream.use { input ->
                java.io.FileOutputStream(target, appending).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        if (cancel.requested) return Result.Cancelled
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(Progress(done, total))
                    }
                }
            }
        } catch (error: Exception) {
            // 不刪檔：下次可以續傳
            return Result.Failed("下載中斷：${error.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }

        if (!matches(target, asset.sha256)) {
            val actual = sha256(target)
            Log.d(TAG, "download ${asset.name} 雜湊不符 預期=${asset.sha256} 實得=$actual")
            target.delete()
            return Result.Failed("${asset.name} 的檔案校驗失敗，已刪除")
        }
        Log.d(TAG, "download ${asset.name} 完成 ${target.length()} bytes")
        return Result.Done
    }

    private fun open(url: String, range: String?): HttpURLConnection {
        var current = URL(url)
        var redirects = 0
        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                range?.let { setRequestProperty("Range", it) }
            }
            val code = connection.responseCode
            // HttpURLConnection 不會自動跟隨 https→https 以外的跳轉，而 Hugging Face
            // 與 GitHub Releases 都會轉到 CDN，所以自己處理。
            if (code !in listOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null || ++redirects > MAX_REDIRECTS) {
                throw IllegalStateException("轉址次數過多或缺少 Location")
            }
            current = URL(current, location)
        }
    }

    fun matches(file: File, expected: String) = file.isFile && sha256(file).equals(expected, ignoreCase = true)

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val MAX_REDIRECTS = 5
}

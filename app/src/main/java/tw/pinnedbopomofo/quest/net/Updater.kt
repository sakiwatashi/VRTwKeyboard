package tw.pinnedbopomofo.quest.net

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 從 GitHub Releases 檢查並安裝新版。
 *
 * 安裝**不是**靜默的：下載完之後把 APK 交給系統的安裝器，
 * 由使用者在頭盔上自己按確認。這個 App 沒有、也不該有靜默安裝的權限。
 */
object Updater {

    private const val TAG = "ZhuyinIme"
    const val REPOSITORY = "sakiwatashi/VRTwKeyboard"
    private const val LATEST = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val TIMEOUT_MS = 15_000

    data class Release(val tag: String, val apkUrl: String, val bytes: Long, val notes: String)

    sealed interface Check {
        data class Available(val release: Release) : Check
        data object UpToDate : Check
        data class Failed(val reason: String) : Check
    }

    /**
     * 問 GitHub 最新的 release。在背景執行緒呼叫。
     *
     * 刻意手寫極簡的取值而不是引入 JSON 函式庫：只要三個欄位，為此多一個相依不划算。
     * 取不到就回 [Check.Failed]，不會拿半套資料去下載。
     */
    fun check(currentTag: String?): Check {
        val body = try {
            fetchText(LATEST)
        } catch (error: Exception) {
            return Check.Failed("連不上 GitHub：${error.javaClass.simpleName}")
        }
        val tag = extract(body, "tag_name") ?: return Check.Failed("看不懂 GitHub 的回應")
        if (!Version.isNewer(tag, currentTag)) return Check.UpToDate

        val apk = extractApkAsset(body) ?: return Check.Failed("$tag 沒有附 APK")
        return Check.Available(
            Release(tag = tag, apkUrl = apk.first, bytes = apk.second, notes = extract(body, "body") ?: ""),
        )
    }

    /** 把下載好的 APK 交給系統安裝器。使用者仍要在頭盔上按確認。 */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.isFile) return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.d(TAG, "update 叫不起安裝器：${error.javaClass.simpleName} ${error.message}")
            false
        }
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub 會擋沒有 User-Agent 的請求
            setRequestProperty("User-Agent", "VRTwKeyboard")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 取出 `"key": "value"`，並還原跳脫字元。
     *
     * 正則用 [Regex] 的字面量拼出來，避免在字串裡數反斜線數錯。
     */
    internal fun extract(json: String, key: String): String? {
        val quoted = Regex.escape(key)
        val pattern = Regex("\"" + quoted + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1]
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /**
     * 從 assets 陣列裡找副檔名是 `.apk` 的那一個，回傳（下載網址, 大小）。
     *
     * 一定要挑 `.apk`：release 常常同時附 checksums.txt 之類的檔案，
     * 拿錯的話下載回來的東西根本不能安裝。
     */
    internal fun extractApkAsset(json: String): Pair<String, Long>? {
        val blocks = Regex("\\{[^{}]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\\}")
        for (match in blocks.findAll(json)) {
            val url = match.groupValues[1]
            if (!url.endsWith(".apk", ignoreCase = true)) continue
            val size = Regex("\"size\"\\s*:\\s*(\\d+)")
                .find(match.value)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?: 0L
            return url to size
        }
        return null
    }
}

package tw.pinnedbopomofo.quest.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用 GitHub **真實**回應的形狀測解析，而不是我自己編的樣本。
 *
 * 樣本取自 2026-09-17 的 `api.github.com/repos/sakiwatashi/VRTwKeyboard/releases/latest`
 * （只留下我們會用到的欄位，其餘刪掉）。
 *
 * 為什麼值得多這一支：[UpdaterTest] 的樣本是我照想像寫的，
 * 真實回應有幾個我沒想到的地方——`body` 裡有大量 `\n` 與中文全形標點，
 * `assets` 物件比我假設的多好幾個欄位。手寫的正則最容易死在這種地方。
 *
 * 這不是網路測試：JSON 是固定的字串，離線也跑得過。
 */
class UpdaterRealResponseTest {

    private val real = """
        {
          "tag_name": "v0.2.0",
          "name": "v0.2.0 — 頭盔內下載、青玉配色、八種特效",
          "body": "給 Meta Quest 的臺灣注音輸入法。\n\n## 安裝\n\n需要一台開啟開發者模式的 Quest 與一條 USB 線。\n\n1. 手機的 Meta Horizon App →「裝置」→「開發者模式」打開\n\n## 驗證\n\n```\nSHA256  68c093a4bf20681c3e662582bb68ac8286f440dc48617a5dd5a6cec08716b476\n```\n",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "url": "https://api.github.com/repos/sakiwatashi/VRTwKeyboard/releases/assets/1",
              "id": 1,
              "name": "VRTwKeyboard-v0.2.0.apk",
              "label": null,
              "state": "uploaded",
              "content_type": "application/vnd.android.package-archive",
              "size": 41685714,
              "download_count": 0,
              "browser_download_url": "https://github.com/sakiwatashi/VRTwKeyboard/releases/download/v0.2.0/VRTwKeyboard-v0.2.0.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `真實回應取得 tag`() {
        assertEquals("v0.2.0", Updater.extract(real, "tag_name"))
    }

    @Test
    fun `真實回應取得 APK 網址與大小`() {
        val asset = Updater.extractApkAsset(real)
        assertEquals(
            "https://github.com/sakiwatashi/VRTwKeyboard/releases/download/v0.2.0/VRTwKeyboard-v0.2.0.apk",
            asset?.first,
        )
        assertEquals(41685714L, asset?.second)
    }

    @Test
    fun `asset 物件有很多其他欄位時仍然只取 size，不會誤抓 id 或 download_count`() {
        // 真實的 asset 有 id=1、download_count=0，兩個都是數字；
        // 抓錯欄位的話下載進度會顯示成 1 bytes 之類的荒謬數字。
        assertEquals(41685714L, Updater.extractApkAsset(real)?.second)
    }

    @Test
    fun `body 裡的換行與反引號不會讓解析壞掉`() {
        val notes = Updater.extract(real, "body")!!
        assertTrue("應該還原成真的換行", notes.contains("\n"))
        assertTrue("內容應該完整", notes.contains("SHA256"))
        assertTrue("中文全形標點要保留", notes.contains("「裝置」"))
    }

    @Test
    fun `同版本不會提示更新，舊版才會`() {
        val tag = Updater.extract(real, "tag_name")
        assertFalse("裝的就是 0.2.0，不該說有新版", Version.isNewer(tag, "0.2.0"))
        assertTrue("裝的是 0.1.0，該說有新版", Version.isNewer(tag, "0.1.0"))
    }
}

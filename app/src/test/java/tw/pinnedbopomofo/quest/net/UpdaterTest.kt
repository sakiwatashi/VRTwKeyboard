package tw.pinnedbopomofo.quest.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GitHub 回應的解析。用手寫的極簡取值而不是 JSON 函式庫，所以更要測：
 * 取錯欄位的後果是下載到不是 APK 的檔案，或是把 release notes 當成版本號。
 */
class UpdaterTest {

    private val sample = """
        {
          "tag_name": "quest-keyboard-v0.2",
          "name": "v0.2",
          "body": "修好了輸入框的字看不見\r\n還有煙霧太慢",
          "assets": [
            {
              "name": "checksums.txt",
              "size": 321,
              "browser_download_url": "https://github.com/x/y/releases/download/v0.2/checksums.txt"
            },
            {
              "name": "VRTwKeyboard-v0.2.apk",
              "size": 39123456,
              "browser_download_url": "https://github.com/x/y/releases/download/v0.2/VRTwKeyboard-v0.2.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `取得 tag`() {
        assertEquals("quest-keyboard-v0.2", Updater.extract(sample, "tag_name"))
    }

    @Test
    fun `release notes 的跳脫字元會還原成換行`() {
        val notes = Updater.extract(sample, "body")
        assertEquals("修好了輸入框的字看不見\n還有煙霧太慢", notes)
    }

    @Test
    fun `只挑副檔名是 apk 的那一個，不會拿到 checksums`() {
        val (url, size) = Updater.extractApkAsset(sample)!!
        assertEquals("https://github.com/x/y/releases/download/v0.2/VRTwKeyboard-v0.2.apk", url)
        assertEquals(39123456L, size)
    }

    @Test
    fun `沒有附 APK 就回 null，不要拿別的檔充數`() {
        val noApk = """
            {
              "tag_name": "v0.3",
              "assets": [
                { "name": "notes.txt", "size": 12,
                  "browser_download_url": "https://github.com/x/y/releases/download/v0.3/notes.txt" }
              ]
            }
        """.trimIndent()
        assertNull(Updater.extractApkAsset(noApk))
    }

    @Test
    fun `完全沒有 assets 也不會炸`() {
        assertNull(Updater.extractApkAsset("""{"tag_name":"v0.3","assets":[]}"""))
        assertNull(Updater.extract("""{"assets":[]}""", "tag_name"))
    }
}

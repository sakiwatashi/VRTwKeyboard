package tw.pinnedbopomofo.quest.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 模型清單有兩份：App 裡的 [VoiceModels.PARAFORMER_ASSETS]，
 * 以及電腦端的 `tools/deploy.ps1`。兩份都寫著同樣的 revision、大小與 SHA256。
 *
 * 只改一邊是很容易犯的錯，而且後果很難追：電腦推進去的檔案跟 App 期待的雜湊不同，
 * App 會判定「檔案損毀」並刪掉使用者剛等了十幾分鐘下載完的東西。
 * 這支測試把兩份綁在一起。
 */
class VoiceModelsAssetsTest {

    private val script: String by lazy {
        val candidates = listOf("tools/deploy.ps1", "../tools/deploy.ps1")
        val file = candidates.map(::File).firstOrNull { it.isFile }
        checkNotNull(file) { "找不到 deploy.ps1，找過：$candidates" }
        file.readText()
    }

    @Test
    fun `App 與部署腳本用的是同一個 revision`() {
        assertTrue(
            "deploy.ps1 裡沒有 ${VoiceModels.REVISION}",
            script.contains(VoiceModels.REVISION),
        )
    }

    @Test
    fun `每個檔的大小與 SHA256 兩邊一致`() {
        for (asset in VoiceModels.PARAFORMER_ASSETS) {
            assertTrue("deploy.ps1 少了 ${asset.name}", script.contains(asset.name))
            assertTrue(
                "${asset.name} 的 SHA256 兩邊不一致：App 是 ${asset.sha256}",
                script.contains(asset.sha256),
            )
            assertTrue(
                "${asset.name} 的大小兩邊不一致：App 是 ${asset.bytes}",
                script.contains(asset.bytes.toString()),
            )
        }
    }

    @Test
    fun `檔名清單與實際要下載的一致，不會下載了卻不檢查`() {
        assertEquals(
            VoiceModels.PARAFORMER_FILES.sorted(),
            VoiceModels.PARAFORMER_ASSETS.map { it.name }.sorted(),
        )
    }

    @Test
    fun `合計大小約 226 MB`() {
        val mb = VoiceModels.totalBytes / (1024.0 * 1024)
        assertTrue("合計 %.1f MB，跟文件寫的 226 MB 對不上".format(mb), mb in 220.0..232.0)
    }

    @Test
    fun `網址都指向釘住的 revision，沒有人用 main 或 master`() {
        for (asset in VoiceModels.PARAFORMER_ASSETS) {
            assertTrue(
                "${asset.name} 的網址沒有釘 revision：${asset.url}",
                asset.url.contains(VoiceModels.REVISION),
            )
            assertTrue(
                "${asset.name} 的網址指向可變的分支：${asset.url}",
                !asset.url.contains("/main/") && !asset.url.contains("/master/"),
            )
        }
    }
}

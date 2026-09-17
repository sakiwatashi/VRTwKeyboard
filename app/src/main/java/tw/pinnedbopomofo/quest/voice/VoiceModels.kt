package tw.pinnedbopomofo.quest.voice

import java.io.File

/**
 * 語音模型的位置。
 *
 * 模型約 226 MiB，不打包進 APK。**App 刻意沒有 `INTERNET` 權限**，所以不會自己下載——
 * 輸入法看得到使用者打的每一個字，讓它在技術上就沒有送出去的能力，比口頭保證有用。
 * 模型由電腦端的 `tools/deploy.ps1` 下載（含 SHA256 驗證）再 adb push 到
 * `/sdcard/Android/data/tw.pinnedbopomofo.quest/files/asr/paraformer-bilingual-zh-en/`。
 *
 * 要改成 App 自行下載的話，得先加 `INTERNET` 權限——那是個產品決定，不是技術細節。
 *
 * 來源、revision 與 SHA256 見 `docs/licenses/asr-paraformer.md`。
 */
object VoiceModels {
    const val PARAFORMER = "asr/paraformer-bilingual-zh-en"
    val PARAFORMER_FILES = listOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt")

    /** 檔案齊全才回傳資料夾；缺任何一個就當作沒安裝。 */
    fun paraformer(filesDir: File?): File? {
        if (filesDir == null) return null
        val dir = File(filesDir, PARAFORMER)
        return dir.takeIf { PARAFORMER_FILES.all { name -> File(it, name).isFile } }
    }
}

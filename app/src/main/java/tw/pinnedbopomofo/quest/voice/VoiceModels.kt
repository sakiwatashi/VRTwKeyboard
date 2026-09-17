package tw.pinnedbopomofo.quest.voice

import java.io.File

/**
 * 語音模型的位置。模型約 226 MiB，不打包進 APK，App 也沒有網路權限：
 * 開發時用 adb push 放到 App 專屬的外部儲存資料夾
 * `/sdcard/Android/data/tw.pinnedbopomofo.quest/files/asr/paraformer-bilingual-zh-en/`。
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

package tw.pinnedbopomofo.quest.voice

import tw.pinnedbopomofo.quest.net.Asset
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

    /**
     * Hugging Face 上釘住的 revision。換版本時網址與 SHA256 要一起換，不能只改其中一個。
     * `tools/deploy.ps1` 裡有同一份清單——兩邊的數值必須一致，[PARAFORMER_ASSETS] 的
     * 單元測試會擋住「只改了一邊」。
     */
    const val REVISION = "8e40c43232a1c5c66c82111efc5820d3accca11b"

    private const val BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve"

    /** 下載清單：網址、預期大小、預期 SHA256。小的排前面，先讓使用者看到進度在動。 */
    val PARAFORMER_ASSETS = listOf(
        Asset(
            name = "tokens.txt",
            url = "$BASE/$REVISION/tokens.txt",
            bytes = 75_756L,
            sha256 = "59aba8873a2ed1e122c25fee421e25f283b63290efbde85c1f01a853d83cb6e6",
        ),
        Asset(
            name = "decoder.int8.onnx",
            url = "$BASE/$REVISION/decoder.int8.onnx",
            bytes = 71_664_561L,
            sha256 = "f3cca9f77bb9d93c8fcbfb63ae617b6b1ee96818df3aa3b151c40658fe38594f",
        ),
        Asset(
            name = "encoder.int8.onnx",
            url = "$BASE/$REVISION/encoder.int8.onnx",
            bytes = 165_462_184L,
            sha256 = "81a70226a8934e6ed92aa1d4fc486b428b5398e2f2619ed4897b7294cab90e9a",
        ),
    )

    /** 三個檔合計的位元組數，拿來顯示「要下載多少」。 */
    val totalBytes: Long get() = PARAFORMER_ASSETS.sumOf { it.bytes }

    /** 模型該放的資料夾（不管在不在）。 */
    fun directory(filesDir: File?): File? = filesDir?.let { File(it, PARAFORMER) }

    /** 還缺哪些檔（大小不符或雜湊不符都算缺）。 */
    fun missing(filesDir: File?): List<Asset> {
        val dir = directory(filesDir) ?: return PARAFORMER_ASSETS
        return PARAFORMER_ASSETS.filter { asset ->
            val file = File(dir, asset.name)
            !file.isFile || file.length() != asset.bytes
        }
    }

    /** 檔案齊全才回傳資料夾；缺任何一個就當作沒安裝。 */
    fun paraformer(filesDir: File?): File? {
        if (filesDir == null) return null
        val dir = File(filesDir, PARAFORMER)
        return dir.takeIf { PARAFORMER_FILES.all { name -> File(it, name).isFile } }
    }
}

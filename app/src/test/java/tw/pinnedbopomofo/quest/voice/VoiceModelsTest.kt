package tw.pinnedbopomofo.quest.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceModelsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `模型檔案齊全才算安裝好`() {
        val files = folder.root
        val dir = File(files, VoiceModels.PARAFORMER).apply { mkdirs() }
        VoiceModels.PARAFORMER_FILES.forEach { File(dir, it).writeText("x") }
        assertEquals(dir, VoiceModels.paraformer(files))
    }

    @Test
    fun `少一個檔案或資料夾不存在，都當作沒安裝`() {
        val files = folder.root
        assertNull(VoiceModels.paraformer(files))
        assertNull(VoiceModels.paraformer(null))
        val dir = File(files, VoiceModels.PARAFORMER).apply { mkdirs() }
        File(dir, "encoder.int8.onnx").writeText("x")
        assertNull(VoiceModels.paraformer(files))
    }
}

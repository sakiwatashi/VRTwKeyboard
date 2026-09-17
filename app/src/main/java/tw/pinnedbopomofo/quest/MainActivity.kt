package tw.pinnedbopomofo.quest

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tw.pinnedbopomofo.quest.net.Asset
import tw.pinnedbopomofo.quest.net.Downloader
import tw.pinnedbopomofo.quest.net.Progress
import tw.pinnedbopomofo.quest.net.Updater
import tw.pinnedbopomofo.quest.voice.MicProbe
import tw.pinnedbopomofo.quest.voice.VoiceModels
import java.io.File

/**
 * 設定與診斷畫面。
 *
 * 除了原本的「這個系統版本讓不讓第三方輸入法跑」之外，這裡也是**唯一**會用到網路的地方：
 * 在頭盔內下載語音模型、檢查 GitHub 上有沒有新版。輸入法服務本身不碰網路。
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var micResult: TextView
    private lateinit var modelStatus: TextView
    private lateinit var modelButton: Button
    private lateinit var updateStatusView: TextView
    private lateinit var updateButton: Button

    /** 下載中的工作；同一時間只允許一個。 */
    private var job: Thread? = null
    private var cancel = Downloader.Cancel()

    /** 檢查到的新版；按第二次才真的下載。 */
    private var pending: Updater.Release? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 18f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "開啟鍵盤設定"
            setOnClickListener {
                // Horizon OS 把 ACTION_INPUT_METHOD_SETTINGS 轉給 Meta 自己的設定（優先權 2），
                // 那裡沒有第三方鍵盤的開關；Android 原生的「螢幕鍵盤」頁還在（優先權 1），要指名開啟。
                val native = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings\$AvailableVirtualKeyboardActivity",
                )
                try {
                    startActivity(native)
                } catch (e: RuntimeException) {
                    // 沒有這一頁（ActivityNotFoundException）或被擋（SecurityException）時退回一般做法
                    try {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    } catch (e: ActivityNotFoundException) {
                        status.append("\n\n系統沒有鍵盤設定畫面")
                    }
                }
            }
        })

        root.addView(Button(this).apply {
            text = "切換輸入法"
            setOnClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        })

        // 輸入法服務本身跳不出權限對話框，只能在這裡先要
        root.addView(Button(this).apply {
            text = "允許使用麥克風"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            }
        })

        micResult = TextView(this).apply {
            text = "麥克風測試：還沒測"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(Button(this).apply {
            text = "麥克風測試（四種來源各錄 3 秒）"
            setOnClickListener { runMicProbe() }
        })
        root.addView(micResult)

        // ── 語音模型：在頭盔內下載，不必接電腦 ──
        modelButton = Button(this).apply {
            text = "下載語音模型"
            setOnClickListener { onModelButton() }
        }
        modelStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(modelButton)
        root.addView(modelStatus)

        // ── 檢查更新 ──
        updateButton = Button(this).apply {
            text = "檢查更新"
            setOnClickListener { onUpdateButton() }
        }
        updateStatusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(updateButton)
        root.addView(updateStatusView)

        root.addView(EditText(this).apply {
            hint = "點這裡測試注音輸入"
            textSize = 22f
        })

        // 下面兩欄用來確認鍵盤會依欄位類型調整
        root.addView(EditText(this).apply {
            hint = "搜尋欄：換行鍵應顯示「搜尋」"
            textSize = 18f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        })
        root.addView(EditText(this).apply {
            hint = "密碼欄：應自動切到英文"
            textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        })

        // Quest 的 App 視窗只有 500×800，內容一多就會把最下面的欄位壓扁
        setContentView(ScrollView(this).apply { addView(root) })
    }

    // ── 語音模型 ────────────────────────────────────────────────

    private fun refreshModelStatus() {
        if (job != null) return
        val missing = VoiceModels.missing(getExternalFilesDir(null))
        if (missing.isEmpty()) {
            modelStatus.text = "語音模型：已安裝（${Progress.readable(VoiceModels.totalBytes)}）"
            modelButton.text = "重新下載模型"
        } else {
            val bytes = missing.sumOf { it.bytes }
            modelStatus.text = "語音模型：缺 ${missing.size} 個檔，需要下載 ${Progress.readable(bytes)}"
            modelButton.text = "下載語音模型"
        }
    }

    private fun onModelButton() {
        if (job != null) {
            cancel.requested = true
            modelStatus.text = "正在取消…"
            return
        }
        val dir = VoiceModels.directory(getExternalFilesDir(null))
        if (dir == null) {
            modelStatus.text = "找不到可寫入的資料夾"
            return
        }
        cancel = Downloader.Cancel()
        modelButton.text = "取消下載"
        val assets = VoiceModels.PARAFORMER_ASSETS
        val total = VoiceModels.totalBytes
        job = Thread({
            var finished = 0L
            var failure: String? = null
            for (asset in assets) {
                val result = Downloader.fetch(asset, File(dir, asset.name), cancel) { progress ->
                    val overall = finished + progress.done
                    runOnUiThread {
                        modelStatus.text = "下載 ${asset.name}：${Progress.readable(overall)}" +
                            " / ${Progress.readable(total)}（${Progress(overall, total).percent}%）"
                    }
                }
                when (result) {
                    is Downloader.Result.Cancelled -> {
                        failure = "已取消。下次可以從斷點接著下載。"
                    }
                    is Downloader.Result.Failed -> {
                        failure = result.reason
                    }
                    is Downloader.Result.Done -> Unit
                }
                if (failure != null) break
                finished += asset.bytes
            }
            runOnUiThread {
                job = null
                val reason = failure
                if (reason != null) {
                    modelStatus.text = reason
                    modelButton.text = "繼續下載"
                } else {
                    refreshModelStatus()
                    modelStatus.append("　下載完成，可以用語音輸入了。")
                }
            }
        }, "model-download").also { it.start() }
    }

    // ── 檢查更新 ────────────────────────────────────────────────

    private fun currentTag(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (error: PackageManager.NameNotFoundException) {
        ""
    }

    /**
     * 能不能叫起系統安裝器。
     *
     * `REQUEST_INSTALL_PACKAGES` 寫在 Manifest 裡**不等於**拿得到：Android 8 之後
     * 它要使用者在系統設定裡逐個 App 開啟「安裝未知應用程式」。
     * 2026-09-17 實機確認 `granted=false`——不先檢查的話，使用者會下載完 40 MB 才失敗。
     */
    private fun canInstall(): Boolean = packageManager.canRequestPackageInstalls()

    private fun openInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:$packageName"),
        )
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            updateStatusView.text = "這台裝置沒有「安裝未知應用程式」的設定頁，只能用電腦更新。"
        }
    }

    private fun onUpdateButton() {
        if (job != null) {
            updateStatusView.text = "請先等語音模型下載完，或按取消。"
            return
        }
        if (pending != null && !canInstall()) {
            // 下載前就擋下來，不要讓人白等
            updateStatusView.text = "要先允許這個 App 安裝應用程式，才能在頭盔內更新。"
            openInstallPermission()
            return
        }
        val ready = pending
        if (ready != null) {
            downloadAndInstall(ready)
            return
        }
        updateButton.isEnabled = false
        updateStatusView.text = "檢查中…"
        Thread({
            val result = Updater.check(currentTag())
            runOnUiThread {
                updateButton.isEnabled = true
                when (result) {
                    is Updater.Check.UpToDate -> {
                        updateStatusView.text = "已是最新版（${currentTag()}）"
                        updateButton.text = "檢查更新"
                    }
                    is Updater.Check.Failed -> {
                        updateStatusView.text = "檢查失敗：${result.reason}"
                        updateButton.text = "重新檢查"
                    }
                    is Updater.Check.Available -> {
                        pending = result.release
                        val size = Progress.readable(result.release.bytes)
                        updateStatusView.text = if (canInstall()) {
                            "有新版 ${result.release.tag}（$size）"
                        } else {
                            "有新版 ${result.release.tag}（$size）。" +
                                "按下去會先請你允許這個 App 安裝應用程式。"
                        }
                        updateButton.text = "下載並安裝 ${result.release.tag}"
                    }
                }
            }
        }, "update-check").also { it.start() }
    }

    private fun downloadAndInstall(release: Updater.Release) {
        val apk = File(File(filesDir, "updates"), "VRTwKeyboard-${release.tag}.apk")
        cancel = Downloader.Cancel()
        updateButton.isEnabled = false
        // GitHub 沒有給 APK 的雜湊，只能用它回報的大小當校驗
        val asset = Asset(name = apk.name, url = release.apkUrl, bytes = release.bytes, sha256 = "")
        job = Thread({
            val result = Downloader.fetch(asset, apk, cancel) { progress ->
                runOnUiThread {
                    updateStatusView.text = "下載 ${release.tag}：${Progress.readable(progress.done)}" +
                        " / ${Progress.readable(release.bytes)}"
                }
            }
            runOnUiThread {
                job = null
                updateButton.isEnabled = true
                if (result is Downloader.Result.Done) {
                    updateStatusView.text = "下載完成，請在接下來的畫面確認安裝。"
                    if (!Updater.install(this, apk)) {
                        updateStatusView.text =
                            "叫不起系統安裝器。可能要先允許這個 App 安裝未知來源的應用程式。"
                    }
                } else {
                    updateStatusView.text = "更新失敗：$result"
                    updateButton.text = "重試"
                }
            }
        }, "update-download").also { it.start() }
    }

    /**
     * 每種錄音來源各錄 3 秒，量音量。診斷「說話收不到」：
     * 2026-09-16 頭盔上用 VOICE_RECOGNITION 量到說話峰值只有 −36 dB。
     */
    private fun runMicProbe() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micResult.text = "請先允許使用麥克風"
            return
        }
        micResult.text = "測試中：請持續說話 12 秒…"
        Thread({
            val lines = MicProbe.SOURCES.map { (name, source) ->
                val result = MicProbe.measure(source, name, PROBE_MILLIS)
                Log.d(PERF_TAG, "perf micProbe $result")
                result.toString()
            }
            runOnUiThread { micResult.text = lines.joinToString(separator = System.lineSeparator()) }
        }, "mic-probe").start()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshModelStatus()
    }

    override fun onDestroy() {
        // 離開畫面就停掉下載：在背景硬跑一個 226 MB 的下載不是使用者期待的行為
        cancel.requested = true
        super.onDestroy()
    }

    // 從設定或切換視窗回來時 onResume 不一定觸發，拿回焦點時再更新一次
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (e: SecurityException) {
            null
        }
        val selected = current?.startsWith("$packageName/") == true
        val microphone = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        status.text = buildString {
            append("版本 ${currentTag()}\n")
            append("Android SDK ${Build.VERSION.SDK_INT}\n")
            append("系統組建 ${Build.DISPLAY}\n")
            append("已啟用：${yesNo(enabled)}\n")
            append("目前選用：${yesNo(selected)}（$current）\n")
            append("麥克風權限：${yesNo(microphone)}\n")
            append("系統語音辨識服務：${yesNo(SpeechRecognizer.isRecognitionAvailable(this@MainActivity))}")
        }
    }

    private fun yesNo(value: Boolean) = if (value) "是" else "否"

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        private const val REQUEST_MICROPHONE = 1
        private const val PERF_TAG = "ZhuyinPerf"
        /** 每種來源錄多久：夠長才能講一句話量到峰值。 */
        private const val PROBE_MILLIS = 3000
    }
}

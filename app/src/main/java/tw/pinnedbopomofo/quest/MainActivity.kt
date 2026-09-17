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
import tw.pinnedbopomofo.quest.voice.MicProbe

/**
 * 診斷畫面：在頭盔上回答「這個系統版本讓不讓第三方輸入法跑」與「系統有沒有語音辨識服務」。
 * 已啟用＝鍵盤設定裡看得到並能勾選；目前選用＝切換成功。
 */
class MainActivity : Activity() {
    private lateinit var status: TextView

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
            append("Android SDK ${Build.VERSION.SDK_INT}\n")
            append("系統組建 ${Build.DISPLAY}\n")
            append("已啟用：${yesNo(enabled)}\n")
            append("目前選用：${yesNo(selected)}（$current）\n")
            append("麥克風權限：${yesNo(microphone)}\n")
            append("系統語音辨識服務：${yesNo(SpeechRecognizer.isRecognitionAvailable(this@MainActivity))}")
        }
    }

    private lateinit var micResult: TextView

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

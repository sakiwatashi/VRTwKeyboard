package tw.pinnedbopomofo.quest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tw.pinnedbopomofo.quest.effects.EffectProbe
import tw.pinnedbopomofo.quest.effects.FxKind
import tw.pinnedbopomofo.quest.effects.JadeArt
import tw.pinnedbopomofo.quest.effects.JadeInsets
import tw.pinnedbopomofo.quest.effects.FxView
import tw.pinnedbopomofo.quest.effects.GlowView
import tw.pinnedbopomofo.quest.effects.KeyboardEffects
import tw.pinnedbopomofo.quest.engine.Composer
import tw.pinnedbopomofo.quest.engine.Engine
import tw.pinnedbopomofo.quest.engine.HomophoneCorrector
import tw.pinnedbopomofo.quest.engine.Lexicon
import tw.pinnedbopomofo.quest.engine.LoadTimer
import tw.pinnedbopomofo.quest.engine.Predictor
import tw.pinnedbopomofo.quest.engine.Zhuyin
import tw.pinnedbopomofo.quest.voice.AudioCapture
import tw.pinnedbopomofo.quest.voice.Endpointer
import tw.pinnedbopomofo.quest.voice.SherpaParaformerEngine
import tw.pinnedbopomofo.quest.voice.TaiwanConverter
import tw.pinnedbopomofo.quest.voice.VoiceModels
import tw.pinnedbopomofo.quest.voice.VoiceSession
import tw.pinnedbopomofo.quest.voice.VoiceState
import java.io.File
import java.io.IOException
import java.util.Locale

class ZhuyinImeService : InputMethodService() {
    private enum class Shift { OFF, ONCE, LOCKED }

    /** 鍵帽種類：一般字鍵、功能鍵、重點（Enter、目前選中的項目）。 */
    private enum class Cap { NORMAL, SPECIAL, ACTIVE }

    /** 候選字列與展開格子共用的一個選項。 */
    private class Choice(val text: String, val highlight: Boolean, val onPick: () -> Unit)

    private val main = Handler(Looper.getMainLooper())

    private var engine: Engine? = null
    private var composer: Composer? = null

    private var page = KeyboardPage.ZHUYIN
    /** 一般文字欄位沿用的語言：使用者切到英文後，換一個欄位還是英文。 */
    private var lastLanguage = KeyboardPage.ZHUYIN
    private var shift = Shift.OFF
    private var enterAction: Int? = null
    private var predictions: List<String> = emptyList()
    private var notice: String? = null
    /** 候選字展開成整頁格子，取代按鍵區。 */
    private var expanded = false
    /** 「切換」鍵打開的選單：換輸入法、配色、透明度。 */
    private var menuOpen = false
    /** 表情頁目前的分頁：0 是常用，其餘對應 EmojiCatalog 的分類。 */
    private var emojiTab = 1
    private var recents = EmojiRecents()
    private var theme = KeyboardThemes.default
    private var opacity = PanelOpacity.MIST
    private var sizing = KeyboardSizing.fit(0, 0, 1f)
    // 青玉的內容內距，在 onCreateInputView 算好，排版與扣尺寸用同一份
    private var jadeInsetLeft = 0
    private var jadeInsetTop = 0
    private var jadeInsetRight = 0
    private var jadeInsetBottom = 0
    /** 上一次重算輝光與背景板時的尺寸與配色；一樣就不用再算。 */
    private var lastArtSignature = ""
    /** 候選列本體與它的玉板底：切到選單頁時要把玉板收起來。 */
    private var stripRow: LinearLayout? = null
    private var stripArt: Drawable? = null

    // 效能記錄（tools/measure-perf.ps1 讀）：一次按鍵裡引擎、輸入框各花多少，剩下的算畫面
    private var engineNanos = 0L
    private var editorNanos = 0L
    private var candidateCount = 0

    /** 系統可能建立不只一個服務實體，記錄裡用它分辨是哪一個。 */
    private val instanceId = Integer.toHexString(System.identityHashCode(this))
    private var engineRequest: SharedLoader<Engine>.Request? = null

    // 語音輸入（見 docs/VOICE_INPUT_PLAN.md）：還沒接上辨識引擎，先錄音並判斷有沒有說話
    private var voice: VoiceSession? = null
    private var capture: AudioCapture? = null
    /** 聆聽中的音量與是否開口；null 表示沒在聽。 */
    private var voiceListening: VoiceState.Listening? = null
    /** 這次聆聽的最大音量：全程 0 代表麥克風被系統靜音，不是使用者沒講話。 */
    private var voicePeak = 0f
    /** 這次聆聽的說話偵測狀態，結束時寫進記錄診斷靈敏度。 */
    private var voiceEndpointer: Endpointer? = null
    /** 邊講邊出的中途文字。 */
    private var voicePartial = ""
    /** 載入好的辨識引擎；模型約 226 MiB，載一次留著重複用。 */
    private var speechEngine: SherpaParaformerEngine? = null
    private var engineLoading = false

    private lateinit var compositionBox: LinearLayout
    private lateinit var composedLine: TextView
    private lateinit var typedLine: TextView
    private lateinit var candidateRow: LinearLayout
    private lateinit var clearButton: Button
    private lateinit var expandButton: Button
    private lateinit var body: LinearLayout
    private lateinit var keyArea: LinearLayout
    private lateinit var sideColumn: LinearLayout
    private lateinit var gridScroll: ScrollView
    private lateinit var grid: LinearLayout
    private lateinit var menuPanel: LinearLayout
    private lateinit var menuScroll: ScrollView
    /** 特效能力探針的結果；只顯示在選單裡與寫進 logcat，不影響打字。 */
    private var effectReport = "還沒測"
    private var effectReportView: TextView? = null
    private var effectProbeView: EffectProbe.ProbeView? = null
    /** 每個特效各自能開關：負載要一項一項量，不能一起開了才發現會頓。 */
    private val effectsOn = FxKind.recommended.toMutableSet()
    private var sheenView: KeyboardEffects.SheenView? = null
    /** 青玉的外框圖。畫在鍵帽後面，只有青玉會出現。 */
    private var panelArtView: ImageView? = null
    private var fxView: FxView? = null
    private var glowView: GlowView? = null
    /** 選單現在分頁，一次只顯示一段，不然一頁塞滿按鈕。 */
    private var menuTab = MenuTab.APPEARANCE

    private enum class MenuTab(val label: String) { APPEARANCE("外觀"), EFFECTS("特效"), DIAGNOSTICS("診斷") }

    private fun fxOn(kind: FxKind) = kind in effectsOn

    /** 符號頁與表情頁沒有自己的語言，跟著切過來之前的那一頁。 */
    private val language get() =
        if (page == KeyboardPage.SYMBOLS || page == KeyboardPage.EMOJI) lastLanguage else page

    /** 鍵帽上的字比一般文字更亮，加上字影，才不會跟鍵面融在一起。 */
    private val labelColor get() = KeyboardThemes.labelColor(theme)
    /** 畫在面板上的字：淺鍵帽配深面板的配色（竹簡、青玉）不能用鍵帽的字色。 */
    private val panelTextColor get() = KeyboardThemes.panelTextColor(theme)
    private val dimText get() = KeyboardThemes.withAlpha(panelTextColor, DIM_TEXT_ALPHA)
    private val keyDepth get() = dp(KEY_DEPTH_DP)

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        recents = EmojiRecents.parse(prefs.getString(KEY_EMOJI_RECENTS, null))
        theme = KeyboardThemes.named(prefs.getString(KEY_THEME, null))
        opacity = PanelOpacity.entries.firstOrNull { it.name == prefs.getString(KEY_OPACITY, null) } ?: PanelOpacity.MIST
        // 放大視窗的實驗已經拿掉，清掉它留在設定檔裡的舊值
        if (prefs.contains(LEGACY_WINDOW_SCALE)) prefs.edit().remove(LEGACY_WINDOW_SCALE).apply()

        // 詞庫在背景執行緒解析，不能卡住主執行緒；載入前照樣能按鍵。整個程序共用一份，見 SharedLoader
        val createdAt = SystemClock.elapsedRealtime()
        Log.d(TAG, "service onCreate id=$instanceId")
        engineRequest = sharedEngine(applicationContext).request { loaded ->
            engine = loaded
            composer = Composer(loaded.lexicon)
            refresh()
            Log.d(PERF_TAG, "perf ready sinceCreateMs=${SystemClock.elapsedRealtime() - createdAt} id=$instanceId")
        }
    }

    override fun onDestroy() {
        cancelVoice()
        speechEngine?.release()
        speechEngine = null
        engineRequest?.cancel()
        engineRequest = null
        Log.d(TAG, "service onDestroy id=$instanceId")
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        // 系統給的尺寸不固定：頭盔上量過 780×355，重開機後變成整個顯示器 3664×1920（2026-09-16），
        // 手機則是整個螢幕。寬高都交給 KeyboardSizing 把關，面板窄於視窗時置中。
        val bounds = window.window?.windowManager?.maximumWindowMetrics?.bounds
        val available = bounds?.height() ?: 0
        val availableWidth = bounds?.width() ?: 0
        // 青玉的背景板有金框，內容要退到框裡面。這個內距必須**先從可用尺寸扣掉**，
        // 不能算完尺寸再加上去——2026-09-17 就是那樣寫的，內容變成 403px 塞進 351px 的視窗，
        // 底部整排功能列被切掉，表情頁連「注」鍵都按不到。
        val jade = theme.face == KeyFace.JADE
        val insets = if (jade) JadeInsets.of(availableWidth, available) else JadeInsets.NONE
        jadeInsetLeft = insets.left
        jadeInsetRight = insets.right
        jadeInsetTop = insets.top
        jadeInsetBottom = insets.bottom
        val insetWidth = insets.width
        val insetHeight = insets.height
        sizing = KeyboardSizing.fit(
            (availableWidth - insetWidth).coerceAtLeast(1),
            (available - insetHeight).coerceAtLeast(1),
            resources.displayMetrics.density,
        )
        Log.d(TAG, "input window width=$availableWidth height=$available inset=${insetWidth}x$insetHeight density=${resources.displayMetrics.density} sizing=$sizing theme=${theme.name} opacity=$opacity")

        // Quest 沒有視窗模糊，但視窗可以半透明（2026-09 實測）：視窗本身全透明，圓角面板自己帶半透明底色
        window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 青玉的底色由背景板那張圖負責，面板本身就不要再鋪一層色
            background = if (theme.face == KeyFace.JADE) {
                null
            } else {
                rounded(KeyboardThemes.withAlpha(theme.tint, opacity.alpha), PANEL_RADIUS_DP)
            }
            if (theme.face == KeyFace.JADE) {
                // 退到背景板的金框裡面。用的是 onCreateInputView 已經從可用高度扣掉的那一份，
                // 兩邊算法不一致的話內容就會溢出視窗。
                setPadding(jadeInsetLeft, jadeInsetTop, jadeInsetRight, jadeInsetBottom)
            } else {
                setPadding(sizing.padding, sizing.padding, sizing.padding, sizing.padding)
            }
        }
        root.addView(buildStrip(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sizing.candidateHeight))

        // 左邊是字鍵，右邊一欄放倒退、Enter、切換、收起：跟 Quest 上 YouTube 自己的鍵盤一樣，常按的大鍵固定在右手邊
        body = horizontalRow()
        keyArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = sizing.keyAreaHeight
        }
        sideColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(keyArea, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, MAIN_WEIGHT))
        body.addView(sideColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, SIDE_WEIGHT))
        root.addView(body)

        // 展開的候選字格子、切換選單都跟按鍵區一樣高，三者輪流顯示
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gridScroll = ScrollView(this).apply {
            visibility = View.GONE
            addView(grid)
        }
        root.addView(gridScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sizing.keyAreaHeight))
        menuPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // 選單內容比按鍵區高：不放進 ScrollView 的話，超過的部分會被安靜地切掉，
        // 2026-09-17 在頭盔上「透明度」與新加的特效測試整段看不到就是這樣來的。
        menuScroll = ScrollView(this).apply {
            visibility = View.GONE
            addView(menuPanel)
        }
        root.addView(menuScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sizing.keyAreaHeight))

        buildKeys()
        refresh(touchEditor = false)
        // Quest 上抓不到輸入法視窗的畫面結構，只能靠記錄確認最下面一排和右側欄有沒有被切掉。
        // 監聽器掛在 root 自己身上：換配色重建時舊 root 連同監聽器一起回收。掛在 viewTreeObserver 會併進
        // 整個視窗的監聽清單、永遠不會移除，每重建一次就多一份舊鍵盤留在記憶體裡（2026-09-16 頭盔上 Views 753）。
        val rootId = Integer.toHexString(System.identityHashCode(root))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // 美術層要先更新：下面那個「按鍵區沒顯示就不檢查」的提前返回是給切邊檢查用的，
            // 但換配色是從選單裡按的，那時候按鍵區是隱藏的——背景板因此永遠載不到。
            // 2026-09-17 實機上「青玉跟預覽完全不一樣」就是這樣來的：只有鍵帽與候選條載入了。
            refreshArt()
            if (body.visibility != View.VISIBLE) return@addOnLayoutChangeListener
            val lastRow = keyArea.getChildAt(keyArea.childCount - 1) ?: return@addOnLayoutChangeListener
            val lastSide = sideColumn.getChildAt(sideColumn.childCount - 1) ?: return@addOnLayoutChangeListener
            val rowBottom = body.top + keyArea.top + lastRow.bottom
            val sideBottom = body.top + sideColumn.top + lastSide.bottom
            val sideRight = body.left + sideColumn.right
            val clipped = rowBottom > root.height || sideBottom > root.height || sideRight > root.width
            Log.d(TAG, "layout root=${root.width}x${root.height} lastRowBottom=$rowBottom sideBottom=$sideBottom sideRight=$sideRight clipped=$clipped id=$rootId")
        }
        // 面板可能比系統給的視窗窄：放進容器置中，兩側留白
        return FrameLayout(this).apply {
            // 外框排在最前面 = 疊在最底下，鍵帽會蓋在它上面
            panelArtView = ImageView(this@ZhuyinImeService).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                isClickable = false
                isFocusable = false
            }
            addView(
                panelArtView,
                FrameLayout.LayoutParams(
                    sizing.panelWidth,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ),
            )
            addView(
                root,
                FrameLayout.LayoutParams(
                    sizing.panelWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ),
            )
            // 特效疊在鍵盤上面，由下到上：輝光、流光、火花。三層都不吃觸控。
            val overlay = FrameLayout.LayoutParams(
                sizing.panelWidth,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            )
            glowView = GlowView(this@ZhuyinImeService).apply {
                strokeWidth = dp(GLOW_STROKE_DP).toFloat()
                visibility = if (fxOn(FxKind.BLOOM)) View.VISIBLE else View.GONE
            }
            addView(glowView, FrameLayout.LayoutParams(overlay))

            sheenView = KeyboardEffects.SheenView(this@ZhuyinImeService, KeyboardThemes.withAlpha(WHITE, SHEEN_PEAK_ALPHA)).apply {
                isClickable = false
                isFocusable = false
                visibility = if (fxOn(FxKind.SHEEN)) View.VISIBLE else View.GONE
                if (fxOn(FxKind.SHEEN)) start()
            }
            addView(sheenView, FrameLayout.LayoutParams(overlay))

            fxView = FxView(this@ZhuyinImeService)
            addView(fxView, FrameLayout.LayoutParams(overlay))
        }
    }

    /** 最上面一行：左邊是組字框（選定的字＋猜測，下面小字是還沒選的注音），中間候選字，右邊全刪與展開。 */
    private fun buildStrip(): View {
        stripArt = null
        val strip = horizontalRow().apply {
            gravity = Gravity.CENTER_VERTICAL
            // 青玉：候選字列鋪一條玉板。兩端是山水、中間留白，字才不會壓在山上。
            if (theme.face == KeyFace.JADE) {
                val bitmap = JadeArt.load(resources, R.drawable.jade_candidates, sizing.panelWidth)
                if (bitmap != null) {
                    stripArt = JadeArt.NineSliceDrawable(
                        bitmap, JADE_CANDIDATES_INSET_X, JADE_CANDIDATES_INSET_Y,
                    )
                    background = stripArt
                    setPadding(dp(JADE_CANDIDATES_PAD_DP), 0, dp(JADE_CANDIDATES_PAD_DP), 0)
                }
            }
        }
        stripRow = strip
        composedLine = TextView(this).apply {
            textSize = sizing.candidateTextSp * 0.85f
            // 組字框也在面板上，不是鍵帽上
            setTextColor(panelTextColor)
            maxLines = 1
            // 句子太長時保留最後面：正在打的地方在句尾
            ellipsize = TextUtils.TruncateAt.START
            maxWidth = dp(COMPOSITION_MAX_DP)
        }
        typedLine = TextView(this).apply {
            textSize = sizing.candidateTextSp * 0.6f
            setTextColor(dimText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            maxWidth = dp(COMPOSITION_MAX_DP)
        }
        compositionBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            // 青玉的候選列本身就是一塊玉板，再加深色底會髒
            background = if (theme.face == KeyFace.JADE) {
                null
            } else {
                rounded(KeyboardThemes.withAlpha(BLACK, COMPOSITION_ALPHA))
            }
            addView(composedLine)
            addView(typedLine)
        }
        strip.addView(compositionBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        ).apply { setMargins(sizing.keyGap, sizing.keyGap, sizing.keyGap, sizing.keyGap) })

        candidateRow = horizontalRow()
        strip.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(candidateRow)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )

        // 全刪會丟掉整串注音，放開才算：射線掃過去不會誤刪
        clearButton = capButton("✕", Cap.SPECIAL) { onClearComposition() }
        strip.addView(clearButton, stripButtonParams())
        expandButton = capButton("⌄", Cap.SPECIAL) {
            expanded = !expanded
            refresh(touchEditor = false)
        }
        strip.addView(expandButton, stripButtonParams())
        return strip
    }

    /**
     * 橫排一律關掉文字基準線對齊：按下時鍵帽的字會往下移，對齊基準線會把同一排其他鍵一起往下推，
     * 看起來像整排都被按下去。
     */
    private fun horizontalRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
    }

    private fun stripButtonParams() = LinearLayout.LayoutParams(
        (sizing.keyHeight * 1.2f).toInt(),
        LinearLayout.LayoutParams.MATCH_PARENT,
    ).apply { setMargins(sizing.keyGap, sizing.keyGap, sizing.keyGap, sizing.keyGap) }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        reset()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val forced = EditorPolicy.initialPage(info.inputType)
        page = if (forced == KeyboardPage.ZHUYIN) lastLanguage else forced
        shift = Shift.OFF
        enterAction = EditorPolicy.enterAction(info.imeOptions)
        expanded = false
        menuOpen = false
        buildKeys()
        refresh(touchEditor = false)
    }

    // 輸入框已經離開，不能再對它 setComposingText
    override fun onFinishInput() {
        cancelVoice()
        super.onFinishInput()
        reset()
    }

    // 鍵盤收起來就不能再錄音：使用者看不到聆聽中的提示
    override fun onFinishInputView(finishingInput: Boolean) {
        cancelVoice()
        super.onFinishInputView(finishingInput)
    }

    private fun reset() {
        composer?.clear()
        predictions = emptyList()
        notice = null
        expanded = false
        menuOpen = false
        refresh(touchEditor = false)
    }

    // ── 效能記錄 ──────────────────────────────────────

    private inline fun <T> engineWork(block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            engineNanos += System.nanoTime() - start
        }
    }

    private inline fun <T> editorWork(block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            editorNanos += System.nanoTime() - start
        }
    }

    /**
     * 量一次按鍵或選字：處理本身（引擎＋輸入框＋其餘的畫面更新），以及到新畫面在主執行緒排版繪製完。
     * 只記按鍵種類，不記按了哪個注音或選了哪個字：記錄不應該變成打字內容的側錄。
     */
    private fun measured(kind: String, action: () -> Unit) {
        engineNanos = 0L
        editorNanos = 0L
        val start = System.nanoTime()
        action()
        val handled = System.nanoTime() - start
        val engine = engineNanos
        val editor = editorNanos
        val syllables = composer?.size ?: 0
        val candidates = candidateCount
        // 畫面回呼在下一格開始時跑；在裡面 post 的訊息會等這一格的排版與繪製做完才輪到
        Choreographer.getInstance().postFrameCallback {
            main.post {
                Log.d(
                    PERF_TAG,
                    "perf key kind=$kind syllables=$syllables candidates=$candidates totalMs=${ms(handled)} " +
                        "engineMs=${ms(engine)} editorMs=${ms(editor)} uiMs=${ms(handled - engine - editor)} " +
                        "frameMs=${ms(System.nanoTime() - start)}",
                )
            }
        }
    }

    // ── 按鍵 ──────────────────────────────────────────

    private fun onZhuyin(symbol: String) {
        predictions = emptyList()
        val composer = composer ?: run {
            notice = "詞庫載入中…"
            refresh()
            return
        }
        // 前面沒有字時按聲調沒有意義，安靜忽略；其他符號接不上才提示
        notice = if (engineWork { composer.type(symbol) } || symbol in Zhuyin.TONES) null else "沒有以「$symbol」開頭的讀音"
        refresh()
    }

    private fun onSpace() {
        if (hasComposition()) commitComposition() else commit(" ")
    }

    /** 丟掉整串還沒送出的注音與選定的字；輸入框裡已經送出的文字不動。 */
    private fun onClearComposition() {
        composer?.clear()
        notice = null
        expanded = false
        refresh()
    }

    private fun onBackspace() {
        notice = null
        val composer = composer
        if (composer != null && !composer.isEmpty) {
            engineWork { composer.backspace() }
            refresh()
        } else {
            if (predictions.isNotEmpty()) {
                predictions = emptyList()
                refresh()
            }
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun onEnter() {
        val action = enterAction
        when {
            hasComposition() -> commitComposition()
            action != null -> currentInputConnection?.performEditorAction(action)
            else -> {
                predictions = emptyList()
                refresh()
                // 聊天類 App 多半靠 Enter 按鍵事件送出訊息，所以送按鍵而不是插入換行字元
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        }
    }

    /** 英文字母與符號：組字中的注音先照目前的整句送出。 */
    private fun onLiteral(text: String) {
        if (hasComposition()) commitComposition()
        commit(text)
        if (shift == Shift.ONCE) {
            shift = Shift.OFF
            buildKeys()
        }
    }

    private fun onShift() {
        shift = when (shift) {
            Shift.OFF -> Shift.ONCE
            Shift.ONCE -> Shift.LOCKED
            Shift.LOCKED -> Shift.OFF
        }
        buildKeys()
    }

    private fun onMic() {
        voice?.let { session ->
            // 聆聽中再按一次：說完了，拿去處理
            session.stop()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notice = "請先打開「智慧優先注音 Quest 原型」App，允許使用麥克風"
            refresh()
            return
        }
        if (hasComposition()) commitComposition()
        startVoice()
    }

    private fun startVoice() {
        val modelDir = VoiceModels.paraformer(getExternalFilesDir(null))
        if (modelDir != null && speechEngine == null) {
            // 模型有裝但還沒載入：先在背景載，載完自動開始聽
            loadSpeechEngine(modelDir)
            return
        }
        beginListening()
    }

    /** 背景載入辨識模型；主執行緒不能碰，模型約 226 MiB，載入要好幾秒。 */
    private fun loadSpeechEngine(modelDir: File) {
        if (engineLoading) return
        engineLoading = true
        notice = "語音模型載入中…"
        refresh(touchEditor = false)
        val startedAt = SystemClock.elapsedRealtime()
        Thread({
            val loaded = try {
                SherpaParaformerEngine.load(modelDir)
            } catch (e: Throwable) {
                Log.d(TAG, "voice engine load failed: ${e.message}")
                null
            }
            main.post {
                engineLoading = false
                speechEngine = loaded
                Log.d(PERF_TAG, "perf voiceEngineLoad ms=${SystemClock.elapsedRealtime() - startedAt} ok=${loaded != null}")
                if (loaded == null) {
                    notice = "語音模型載入失敗，請看記錄"
                    refresh(touchEditor = false)
                } else {
                    beginListening()
                }
            }
        }, "voice-engine-load").start()
    }

    private fun beginListening() {
        // 在主執行緒先抓好詞庫，辨識執行緒上只用它，不去碰會變動的欄位
        val corrector = engine?.lexicon?.let { HomophoneCorrector(it) }
        val endpointer = Endpointer()
        voiceEndpointer = endpointer
        val session = VoiceSession(
            // 還沒安裝模型時是 null：只錄音、只回報聽到幾秒
            engine = speechEngine,
            recognize = { Thread(it, "voice-recognize").start() },
            deliver = { main.post(it) },
            onState = ::onVoiceState,
            // 模型輸出簡體：先轉台灣正體，再用詞庫修掉同音誤判（「華屬」→「滑鼠」）
            convert = { text ->
                val traditional = TaiwanConverter.convert(text)
                corrector?.correct(traditional) ?: traditional
            },
            endpointer = endpointer,
        )
        val recorder = AudioCapture(
            context = this,
            onFrame = session::accept,
            onError = { reason -> main.post { if (voice === session) endVoice("錄音失敗：$reason") } },
        )
        voice = session
        capture = recorder
        voicePeak = 0f
        voicePartial = ""
        voiceListening = VoiceState.Listening(0f, speaking = false)
        notice = null
        recorder.start()?.let { reason ->
            Log.d(TAG, "voice start failed: $reason")
            endVoice(reason)
            return
        }
        Log.d(TAG, "voice start engineReady=${speechEngine != null}")
        refresh(touchEditor = false)
    }

    private fun onVoiceState(state: VoiceState) {
        if (voice == null) return
        when (state) {
            is VoiceState.Listening -> {
                voiceListening = state
                voicePeak = maxOf(voicePeak, state.level)
                // 聲波的振幅就是音量條用的那個值：Endpointer 每 20 毫秒算一次，不是假的動畫
                if (fxOn(FxKind.VOICE)) fxView?.voice(on = true, level = state.level, color = theme.accent)
                refresh(touchEditor = false)
                return
            }
            is VoiceState.Partial -> {
                voicePartial = state.text
                refresh(touchEditor = false)
                return
            }
            is VoiceState.Recognizing -> {
                capture?.stop()
                capture = null
                voiceListening = null
                fxView?.voice(on = false, level = 0f, color = theme.accent)
                notice = "辨識中…"
                Log.d(TAG, "voice recognizing speechMs=${state.speechMs} peak=$voicePeak")
                refresh(touchEditor = false)
                return
            }
            is VoiceState.Done -> {
                // 只記長度，不記辨識出的內容
                Log.d(TAG, "voice done speechMs=${state.speechMs} chars=${state.text.length} ${voiceEndpointer?.summary()}")
                endVoice(if (state.text.isEmpty()) "沒有辨識出文字" else null)
                if (state.text.isNotEmpty()) commit(state.text)
            }
            is VoiceState.Heard -> {
                Log.d(TAG, "voice heard speechMs=${state.speechMs} peak=$voicePeak ${voiceEndpointer?.summary()}")
                endVoice(
                    String.format(Locale.US, "聽到約 %.1f 秒的說話；語音模型還沒接上，所以沒有轉成文字", state.speechMs / 1000f),
                )
            }
            VoiceState.NoSpeech -> {
                Log.d(TAG, "voice noSpeech peak=$voicePeak ${voiceEndpointer?.summary()}")
                endVoice(if (voicePeak <= 0f) "麥克風收到的全是靜音，可能被系統擋下" else "沒有聽到說話")
            }
            is VoiceState.Failed -> {
                Log.d(TAG, "voice failed: ${state.reason}")
                endVoice("語音辨識失敗：${state.reason}")
            }
        }
    }

    /** 結束這次語音輸入，停止錄音；[message] 顯示在候選列。 */
    private fun endVoice(message: String?) {
        capture?.stop()
        capture = null
        voice = null
        voiceListening = null
        voicePartial = ""
        fxView?.voice(on = false, level = 0f, color = theme.accent)
        notice = message
        refresh(touchEditor = false)
    }

    /** 收起鍵盤、離開輸入框、服務結束：直接丟掉，不送結果。 */
    private fun cancelVoice() {
        val session = voice ?: return
        session.cancel()
        capture?.stop()
        capture = null
        voice = null
        voiceListening = null
        voicePartial = ""
        Log.d(TAG, "voice cancelled")
    }

    /** 組字框下排的小字：音量條與提示；中途文字在上排用重點色顯示。 */
    private fun voiceLabel(state: VoiceState.Listening): String {
        val bars = VOICE_BARS.take(1 + (state.level * (VOICE_BARS.length - 1)).toInt())
        if (voicePartial.isNotEmpty()) return "🎙 $bars"
        return if (state.speaking) "🎙 正在聽 $bars" else "🎙 請說話 $bars"
    }

    private fun onOpenMenu() {
        expanded = false
        menuOpen = true
        refresh(touchEditor = false)
    }

    /**
     * 跳出系統的輸入法選單，換成 Meta 原生鍵盤或其他鍵盤。
     * Quest 的設定裡沒有切換第三方鍵盤的入口，不放在鍵盤上就只能回 App 切。
     */
    private fun onSwitchKeyboard() {
        if (hasComposition()) commitComposition()
        menuOpen = false
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        Log.d(TAG, "input method picker requested")
    }

    /** 換配色或透明度：存下來，整個鍵盤用新顏色重建；選單保持打開，方便一個個比較。 */
    private fun applyAppearance(newTheme: KeyboardTheme, newOpacity: PanelOpacity) {
        theme = newTheme
        opacity = newOpacity
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_THEME, theme.name)
            .putString(KEY_OPACITY, opacity.name)
            .apply()
        setInputView(onCreateInputView())
    }

    private fun onHide() {
        if (hasComposition()) commitComposition()
        requestHideSelf(0)
    }

    private fun switchPage(target: KeyboardPage) {
        if (hasComposition()) commitComposition()
        if (target == KeyboardPage.ZHUYIN || target == KeyboardPage.ENGLISH) lastLanguage = target
        // 還沒用過表情時，打開停在第一個分類，不要停在空白的常用頁
        if (target == KeyboardPage.EMOJI) emojiTab = if (recents.list.isEmpty()) 1 else 0
        page = target
        shift = Shift.OFF
        expanded = false
        menuOpen = false
        buildKeys()
        refresh(touchEditor = false)
    }

    // ── 送字 ──────────────────────────────────────────

    private fun hasComposition() = composer?.isEmpty == false

    /** 送出整句：選定的字加上其餘部分的猜測。 */
    private fun commitComposition() {
        val text = composer?.text() ?: ""
        composer?.clear()
        commit(text)
    }

    private fun commit(text: String) {
        notice = null
        expanded = false
        if (text.isNotEmpty()) editorWork { currentInputConnection?.commitText(text, 1) }
        predictions = engineWork { engine?.predictor?.after(text).orEmpty() }
        refresh()
    }

    // ── 畫面 ──────────────────────────────────────────

    private fun refresh(touchEditor: Boolean = true) {
        val composer = composer
        val composing = composer != null && !composer.isEmpty
        // 注音在鍵盤上的組字框看；輸入框顯示轉出的整句，App 收到的才是正常中文
        if (touchEditor) editorWork { currentInputConnection?.setComposingText(composer?.text() ?: "", 1) }
        if (!::candidateRow.isInitialized) return

        // 語音的中途文字跟注音的組字一樣放組字框、用重點色，不要用候選列的暗色提示
        val listening = voiceListening
        compositionBox.visibility = if (composing || listening != null) View.VISIBLE else View.GONE
        if (listening != null) {
            composedLine.text = SpannableStringBuilder().apply {
                append(voicePartial)
                setSpan(ForegroundColorSpan(KeyboardThemes.panelAccentColor(theme)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            typedLine.text = voiceLabel(listening)
        } else if (composing) {
            composedLine.text = SpannableStringBuilder().apply {
                append(composer!!.fixedText())
                setSpan(ForegroundColorSpan(KeyboardThemes.panelAccentColor(theme)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(composer.guessText())
            }
            typedLine.text = composer!!.pendingTyped()
        }

        val choices = if (composing) {
            engineWork { composer!!.candidates() }.mapIndexed { index, candidate ->
                Choice(candidate.text, highlight = index == 0) {
                    expanded = false
                    if (engineWork { composer!!.select(candidate) }) commitComposition() else refresh()
                }
            }
        } else {
            predictions.map { prediction -> Choice(prediction, highlight = false) { commit(prediction) } }
        }
        candidateCount = choices.size

        candidateRow.removeAllViews()
        notice?.let { addLabel(it) }
        if (engine == null) addLabel("詞庫載入中…")
        // 聆聽中不放候選字：候選列留給聆聽提示，也避免誤點
        if (voiceListening == null) choices.forEach { addChip(it) }

        if (choices.isEmpty()) expanded = false
        clearButton.visibility = if (composing) View.VISIBLE else View.GONE
        expandButton.visibility = if (choices.isEmpty()) View.INVISIBLE else View.VISIBLE
        expandButton.text = if (expanded) "⌃" else "⌄"
        body.visibility = if (expanded || menuOpen) View.GONE else View.VISIBLE
        gridScroll.visibility = if (expanded && !menuOpen) View.VISIBLE else View.GONE
        menuScroll.visibility = if (menuOpen) View.VISIBLE else View.GONE
        // 候選列在所有頁面都在（隱藏它面板高度會跳）。選單頁不需要候選字，
        // 但青玉的玉板底會變成一塊浮在選單上的板子，所以只把底收起來。
        stripRow?.background = if (menuOpen) null else stripArt
        if (expanded && !menuOpen) fillGrid(choices)
        if (menuOpen) buildMenu()
    }

    /**
     * 切換選單。分成三頁：外觀、特效、診斷。
     *
     * 本來是一頁全部攤開——配色九個色塊、透明度四級、特效八個開關、再加診斷，
     * 三十幾個按鈕擠在一個面板上，在 VR 裡用射線點根本找不到東西。
     * 一次只顯示一段，上面一排分頁鈕切換。
     */
    private fun buildMenu() {
        menuPanel.removeAllViews()

        val actions = horizontalRow()
        actions.addView(capButton("切換輸入法", Cap.ACTIVE) { onSwitchKeyboard() }, cellParams(weight = 2f))
        actions.addView(capButton("返回鍵盤", Cap.SPECIAL) {
            menuOpen = false
            refresh(touchEditor = false)
        }, cellParams(weight = 1f))
        menuPanel.addView(actions)

        val tabs = horizontalRow()
        for (tab in MenuTab.entries) {
            val kind = if (tab == menuTab) Cap.ACTIVE else Cap.NORMAL
            tabs.addView(capButton(tab.label, kind) {
                menuTab = tab
                buildMenu()
            }, cellParams(weight = 1f))
        }
        menuPanel.addView(tabs)

        when (menuTab) {
            MenuTab.APPEARANCE -> buildAppearanceTab()
            MenuTab.EFFECTS -> buildEffectsTab()
            MenuTab.DIAGNOSTICS -> buildDiagnosticsTab()
        }
    }

    private fun buildAppearanceTab() {
        menuPanel.addView(menuLabel("配色"))
        // 配色變多了：一排放五個，多的換行，色塊才不會窄到看不出顏色
        for (group in KeyboardThemes.all.chunked(SWATCHES_PER_ROW)) {
            val swatches = horizontalRow()
            for (candidate in group) swatches.addView(swatch(candidate), cellParams(weight = 1f))
            repeat(SWATCHES_PER_ROW - group.size) { swatches.addView(View(this), cellParams(weight = 1f)) }
            menuPanel.addView(swatches)
        }

        menuPanel.addView(menuLabel("透明度"))
        val levels = horizontalRow()
        for (level in PanelOpacity.entries) {
            val kind = if (level == opacity) Cap.ACTIVE else Cap.SPECIAL
            levels.addView(capButton(level.label, kind) { applyAppearance(theme, level) }, cellParams(weight = 1f))
        }
        menuPanel.addView(levels)
    }

    private fun buildEffectsTab() {
        menuPanel.addView(menuLabel("特效"))
        for (group in FxKind.entries.chunked(EFFECTS_PER_ROW)) {
            val row = horizontalRow()
            for (kind in group) row.addView(effectToggle(kind), cellParams(weight = 1f))
            repeat(EFFECTS_PER_ROW - group.size) { row.addView(View(this), cellParams(weight = 1f)) }
            menuPanel.addView(row)
        }

        val presets = horizontalRow()
        presets.addView(capButton("建議組合", Cap.SPECIAL) {
            effectsOn.clear()
            effectsOn += FxKind.recommended
            applyEffects()
        }, cellParams(weight = 1f))
        presets.addView(capButton("全部關閉", Cap.SPECIAL) {
            effectsOn.clear()
            applyEffects()
        }, cellParams(weight = 1f))
        menuPanel.addView(presets)
    }

    private fun buildDiagnosticsTab() {
        menuPanel.addView(menuLabel("特效能力"))
        val probeRow = horizontalRow()
        probeRow.addView(capButton("測一次", Cap.SPECIAL) { runEffectProbe() }, cellParams(weight = 1f))
        menuPanel.addView(probeRow)
        effectReportView = TextView(this).apply {
            text = effectReport
            textSize = sizing.candidateTextSp * 0.6f
            setTextColor(dimText)
            setPadding(dp(4), dp(2), dp(4), dp(6))
        }
        menuPanel.addView(effectReportView)
    }

    /** 特效開關；標記為 heavy 的加上記號，提醒開了要量。 */
    private fun effectToggle(kind: FxKind): Button {
        val on = fxOn(kind)
        val label = if (kind.heavy) "${kind.label}*" else kind.label
        return capButton(if (on) "$label ✓" else label, if (on) Cap.ACTIVE else Cap.SPECIAL) {
            if (on) effectsOn -= kind else effectsOn += kind
            applyEffects()
        }
    }

    /**
     * 按到一顆鍵時放特效。座標要換算到覆蓋層的座標系：按鍵在 root 裡面，
     * 覆蓋層是 root 的兄弟，兩者原點不同，直接用 view.x 會偏掉。
     */
    private fun onKeyHit(view: View, key: Key) {
        val fx = fxView ?: return
        if (!fxOn(FxKind.PARTICLES) && !fxOn(FxKind.FLY) && !fxOn(FxKind.SHOCK)) return
        val viewSpot = IntArray(2).also { view.getLocationInWindow(it) }
        val fxSpot = IntArray(2).also { fx.getLocationInWindow(it) }
        val centerX = (viewSpot[0] - fxSpot[0] + view.width / 2).toFloat()
        val centerY = (viewSpot[1] - fxSpot[1] + view.height / 2).toFloat()

        if (fxOn(FxKind.PARTICLES)) {
            fx.burst(centerX, centerY, theme.accent, SPARK_COUNT, dp(1) * SPARK_SPEED_DP)
        }
        if (fxOn(FxKind.SHOCK)) {
            fx.shock(centerX, centerY)
        }
        // 只有真的會進到輸入框的字才飛；退格、切換那些飛過去沒有意義
        if (fxOn(FxKind.FLY) && key.label.isNotEmpty() && !key.special && !key.active) {
            val boxSpot = IntArray(2).also { compositionBox.getLocationInWindow(it) }
            val targetX = (boxSpot[0] - fxSpot[0] + compositionBox.width * FLY_TARGET_X).toFloat()
            val targetY = (boxSpot[1] - fxSpot[1] + compositionBox.height / 2).toFloat()
            fx.fly(key.label, centerX, centerY, targetX, targetY, theme.accent, sizing.keyTextSp * dp(1) * 1.15f)
        }
    }

    /** 輝光：把每顆鍵的位置抄一份給輝光層，它畫模糊的外框，鍵帽再實心蓋上去。 */
    private fun refreshGlow() {
        val glow = glowView ?: return
        if (!fxOn(FxKind.BLOOM)) {
            glow.clearSpots()
            glow.clearBlur()
            glow.visibility = View.GONE
            return
        }
        val fxSpot = IntArray(2).also { glow.getLocationInWindow(it) }
        val bounds = ArrayList<android.graphics.RectF>()
        val colors = ArrayList<Int>()
        val radius = dp(KEY_RADIUS_DP).toFloat()
        for (container in listOf(keyArea, sideColumn)) {
            forEachKeyView(container) { view ->
                val spot = IntArray(2).also { view.getLocationInWindow(it) }
                val left = (spot[0] - fxSpot[0]).toFloat()
                val top = (spot[1] - fxSpot[1]).toFloat()
                bounds += android.graphics.RectF(left, top, left + view.width, top + view.height)
                colors += KeyboardThemes.withAlpha(theme.accent, GLOW_ALPHA)
            }
        }
        glow.setSpots(bounds, colors, radius)
        glow.visibility = View.VISIBLE
        val applied = glow.applyBlur(dp(GLOW_BLUR_DP).toFloat())
        if (!applied) {
            // 這台機器做不到就別留一層沒模糊的色塊在上面
            Log.d(TAG, "effect bloom 失敗：這台裝置套不上 RenderEffect，輝光已關閉")
            effectsOn -= FxKind.BLOOM
            glow.clearSpots()
            glow.visibility = View.GONE
        }
    }

    /**
     * 青玉的背景板：玉面、金色細框、四角雲紋、角落山水，用圖檔九宮格拉伸。
     *
     * 本來這一層是程式畫的層疊遠山，2026-09-17 使用者看了說「非常醜」——
     * 程式畫得出形狀，畫不出質感。改用美術圖之後那份程式碼就整個移除了。
     */
    private fun refreshPanelArt() {
        val board = panelArtView ?: return
        if (theme.face != KeyFace.JADE) {
            board.setImageDrawable(null)
            board.visibility = View.GONE
            return
        }
        val bitmap = JadeArt.load(resources, R.drawable.jade_panel, sizing.panelWidth)
        if (bitmap == null) {
            // 讀不到圖就不要留一塊空白在上面
            Log.d(TAG, "jade panel 讀不到圖，背景板略過")
            board.visibility = View.GONE
            return
        }
        board.setImageDrawable(JadeArt.NineSliceDrawable(bitmap, JADE_PANEL_INSET_X, JADE_PANEL_INSET_Y))
        board.visibility = View.VISIBLE
    }

    /**
     * 更新輝光與背景板。
     *
     * **只在尺寸或配色真的變了才做**：`setImageDrawable` 與 `setRenderEffect` 都會再觸發一次排版，
     * 每次 layout 都做就變成自己餵自己，一秒跑四五輪，打字時看得出來在頓（2026-09-17 實機記錄到
     * 十秒 46 次 layout）。
     */
    private fun refreshArt() {
        val panel = panelArtView ?: return
        val signature = "${panel.width}x${panel.height}:${theme.name}"
        if (signature == lastArtSignature) return
        lastArtSignature = signature
        refreshGlow()
        refreshPanelArt()
    }

    /** 走訪按鍵區裡每一顆實際的按鍵（跳過用來排版的容器）。 */
    private fun forEachKeyView(container: LinearLayout, action: (View) -> Unit) {
        for (index in 0 until container.childCount) {
            when (val child = container.getChildAt(index)) {
                is Button -> action(child)
                is LinearLayout -> forEachKeyView(child, action)
                else -> Unit
            }
        }
    }

    /**
     * 套用特效設定。
     *
     * 漣漪要換掉鍵帽背景，所以得重建按鍵；其他幾層只是開關或清空。
     * 有開 heavy 的特效就自動量一次負載寫進 log——不用使用者記得按「測一次」。
     */
    private fun applyEffects() {
        fxView?.reset()
        fxView?.mist(fxOn(FxKind.MIST), KeyboardThemes.mix(theme.key, WHITE, MIST_WHITEN))
        val sheen = sheenView
        if (sheen != null) {
            if (fxOn(FxKind.SHEEN)) {
                sheen.visibility = View.VISIBLE
                sheen.start()
            } else {
                sheen.stop()
                sheen.visibility = View.GONE
            }
        }
        buildKeys()
        refresh(touchEditor = false)
        refreshGlow()
        Log.d(TAG, "effects on=${effectsOn.joinToString(",") { it.name }}")
        if (effectsOn.any { it.heavy }) main.postDelayed({ runEffectProbe() }, AUTO_PROBE_DELAY_MS)
    }

    /**
     * 量這塊面板做得動什麼特效：硬體加速、實際重畫速度、shader 能不能編譯。
     *
     * 刻意不呼叫 [refresh]：那會重建整個選單、把探針的 View 一起拆掉，量到一半就斷了。
     * 結果直接寫進已經在畫面上的那個 TextView。
     */
    private fun runEffectProbe() {
        effectProbeView?.let {
            it.stop()
            menuPanel.removeView(it)
        }
        val screen = window.window?.decorView?.display
        val hz = EffectProbe.refreshRate(screen).takeIf { it > 0f } ?: DEFAULT_REFRESH_HZ
        effectReportView?.text = "測量中…"
        val probe = EffectProbe.ProbeView(this, hz)
        effectProbeView = probe
        menuPanel.addView(probe, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)))
        probe.start(EffectProbe.DEFAULT_MEASURE_MS) { stats, accelerated ->
            val report = EffectProbe.capabilities(screen, accelerated, stats.summary())
            val verdict = EffectProbe.verdict(accelerated, stats.fps, stats.dropped)
            Log.d(TAG, "effect probe $report")
            Log.d(TAG, "effect verdict $verdict")
            effectReport = verdict + System.lineSeparator() + report
            effectReportView?.text = effectReport
            menuPanel.removeView(probe)
            effectProbeView = null
        }
    }

    /** 配色色塊：用該配色自己的底色與字色；目前的配色白色粗框，其他用各自的重點色細框。 */
    private fun swatch(candidate: KeyboardTheme) = Button(this).apply {
        text = candidate.name
        textSize = sizing.keyTextSp * 0.9f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0)
        stateListAnimator = null
        setTextColor(KeyboardThemes.labelColor(candidate))
        background = GradientDrawable().apply {
            cornerRadius = dp(KEY_RADIUS_DP).toFloat()
            setColor(KeyboardThemes.mix(candidate.tint, candidate.key, CAP_NORMAL_TOP))
            if (candidate.name == theme.name) setStroke(dp(3), Color.WHITE) else setStroke(dp(2), candidate.accent)
        }
        setOnClickListener { applyAppearance(candidate, opacity) }
    }

    private fun menuLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = sizing.candidateTextSp * 0.65f
        setTextColor(dimText)
        setPadding(dp(8), dp(2), 0, 0)
    }

    /** 展開的候選字：短的詞一排 GRID_COLUMNS 個，長句子自己佔一整排。 */
    private fun fillGrid(choices: List<Choice>) {
        grid.removeAllViews()
        gridScroll.scrollTo(0, 0)
        var row: LinearLayout? = null
        fun newRow() = horizontalRow().also {
            grid.addView(it)
            row = it
        }
        for (choice in choices) {
            val long = choice.text.codePointCount(0, choice.text.length) > GRID_SHORT_CHARACTERS
            // 格子要能上下捲動，所以放開才算，捲動時才不會誤選
            val cell = capButton(choice.text, if (choice.highlight) Cap.ACTIVE else Cap.NORMAL) { measured(PERF_PICK) { choice.onPick() } }.apply {
                textSize = sizing.candidateTextSp
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            if (long) {
                newRow().addView(cell, cellParams(weight = GRID_COLUMNS.toFloat()))
                row = null
                continue
            }
            val current = row?.takeIf { it.childCount < GRID_COLUMNS } ?: newRow()
            current.addView(cell, cellParams(weight = 1f))
        }
        // 最後一排不滿時補空位，格子寬度才會跟上面對齊
        row?.let { last ->
            repeat(GRID_COLUMNS - last.childCount) { last.addView(View(this), cellParams(weight = 1f)) }
        }
    }

    private fun cellParams(weight: Float) = LinearLayout.LayoutParams(0, sizing.keyHeight, weight).apply {
        setMargins(sizing.keyGap, sizing.keyGap, sizing.keyGap, sizing.keyGap)
    }

    private fun buildKeys() {
        if (!::keyArea.isInitialized) return
        keyArea.removeAllViews()
        when (page) {
            KeyboardPage.ZHUYIN -> ZHUYIN_ROWS.forEach { row ->
                addRow(row.map { symbol ->
                    Key(symbol, perf = if (symbol in Zhuyin.TONES) "tone" else "zhuyin") { onZhuyin(symbol) }
                })
            }
            KeyboardPage.ENGLISH -> ENGLISH_ROWS.forEachIndexed { index, row ->
                val letters = row.map { letter ->
                    val text = if (shift == Shift.OFF) letter else letter.uppercase()
                    Key(text) { onLiteral(text) }
                }
                if (index == ENGLISH_ROWS.lastIndex) {
                    val shiftKey = Key(
                        if (shift == Shift.LOCKED) "⇪" else "⇧",
                        weight = 1.5f,
                        special = shift == Shift.OFF,
                        active = shift != Shift.OFF,
                    ) { onShift() }
                    addRow(listOf(shiftKey) + letters)
                } else {
                    addRow(letters)
                }
            }
            KeyboardPage.SYMBOLS -> {
                // 注音頁過來是全形標點，英文頁過來是半形
                val rows = if (lastLanguage == KeyboardPage.ZHUYIN) FULL_WIDTH_SYMBOL_ROWS else HALF_WIDTH_SYMBOL_ROWS
                rows.forEach { row -> addRow(row.map { symbol -> Key(symbol) { onLiteral(symbol) } }) }
            }
            KeyboardPage.EMOJI -> buildEmojiPage()
        }

        val chinese = language == KeyboardPage.ZHUYIN
        val backLabel = if (chinese) "注" else "ABC"
        val symbolKey = if (page == KeyboardPage.SYMBOLS) {
            Key(backLabel, 1.5f, special = true) { switchPage(lastLanguage) }
        } else {
            Key("123", 1.5f, special = true) { switchPage(KeyboardPage.SYMBOLS) }
        }
        val emojiKey = if (page == KeyboardPage.EMOJI) {
            Key(backLabel, 1.2f, active = true) { switchPage(lastLanguage) }
        } else {
            Key("😀", 1.2f, special = true) { switchPage(KeyboardPage.EMOJI) }
        }
        val comma = if (chinese) "，" else ","
        val period = if (chinese) "。" else "."
        addRow(listOf(
            symbolKey,
            Key("🌐", 1.5f, special = true) {
                switchPage(if (chinese) KeyboardPage.ENGLISH else KeyboardPage.ZHUYIN)
            },
            Key("🎤", 1.2f, special = true) { onMic() },
            emojiKey,
            // 空白鍵上寫目前的語言，一眼看出現在打的是注音還是英文
            Key(if (chinese) "注音" else "English", 3.6f, dim = true, perf = PERF_SPACE) { onSpace() },
            Key(comma) { onLiteral(comma) },
            Key(period) { onLiteral(period) },
        ))

        sideColumn.removeAllViews()
        addTallKey(Key("⌫", special = true, repeats = true, perf = "backspace") { onBackspace() }, rows = 1)
        addTallKey(Key(EditorPolicy.enterLabel(enterAction), active = true, perf = "enter") { onEnter() }, rows = 2)
        addTallKey(Key("切換", special = true) { onOpenMenu() }, rows = 1)
        addTallKey(Key("收起", special = true) { onHide() }, rows = 1)
    }

    /** 表情頁：一排分類分頁，下面三排高的可捲動格子；最底下的功能列跟其他頁共用。 */
    private fun buildEmojiPage() {
        val tabs = listOf("🕘") + EmojiCatalog.categories.map { it.icon }
        addRow(tabs.mapIndexed { index, icon ->
            Key(icon, special = index != emojiTab, active = index == emojiTab) {
                emojiTab = index
                buildKeys()
            }
        })

        val emojis = if (emojiTab == 0) recents.list else EmojiCatalog.categories[emojiTab - 1].emojis
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (emojis.isEmpty()) {
            column.addView(TextView(this).apply {
                text = "還沒有用過的表情"
                textSize = sizing.candidateTextSp * 0.8f
                setTextColor(dimText)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sizing.keyHeight))
        }
        emojis.chunked(EMOJI_COLUMNS).forEach { chunk ->
            val row = horizontalRow()
            chunk.forEach { emoji -> row.addView(emojiCell(emoji), cellParams(weight = 1f)) }
            // 最後一排不滿時補空位，格子寬度才會跟上面對齊
            repeat(EMOJI_COLUMNS - chunk.size) { row.addView(View(this), cellParams(weight = 1f)) }
            column.addView(row)
        }
        // 格子高度用剩下的空間算，不要寫死排數。
        // 寫死 3 排時是「分類列 + 格子 + 功能列」剛好等於按鍵區，零餘裕；
        // 只要有一點內距或捨入誤差，被擠掉的就是最下面那排功能列——
        // 「注」鍵在那裡，結果就是進了表情頁切不回中文（2026-09-17 實機遇到）。
        val rowHeight = sizing.keyHeight + sizing.keyGap * 2
        val gridHeight = (sizing.keyAreaHeight - rowHeight * EMOJI_FIXED_ROWS)
            .coerceAtLeast(rowHeight)
        keyArea.addView(
            ScrollView(this).apply { addView(column) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, gridHeight),
        )
    }

    private fun emojiCell(emoji: String) = Button(this).apply {
        text = emoji
        textSize = sizing.keyTextSp * 1.1f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        background = flatPressable(Color.TRANSPARENT)
        // 格子要能上下捲動，所以放開才算，捲動時才不會誤選
        setOnClickListener { onEmoji(emoji) }
    }

    private fun onEmoji(emoji: String) {
        recents.use(emoji)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_EMOJI_RECENTS, recents.serialize()).apply()
        onLiteral(emoji)
    }

    private class Key(
        val label: String,
        val weight: Float = 1f,
        val special: Boolean = false,
        val active: Boolean = false,
        val repeats: Boolean = false,
        /** 次要文字（例如空白鍵上的語言名稱）用淡一點的字色。 */
        val dim: Boolean = false,
        /** 效能記錄裡的按鍵種類。 */
        val perf: String = "function",
        val onPress: () -> Unit,
    )

    private fun addRow(keys: List<Key>) {
        val row = horizontalRow()
        for (key in keys) {
            row.addView(keyButton(key), LinearLayout.LayoutParams(0, sizing.keyHeight, key.weight).apply {
                setMargins(sizing.keyGap, sizing.keyGap, sizing.keyGap, sizing.keyGap)
            })
        }
        keyArea.addView(row)
    }

    /** 右側欄的鍵佔好幾排高；高度要把中間吃掉的間距也算進去，底部才會跟左邊對齊。 */
    private fun addTallKey(key: Key, rows: Int) {
        val height = rows * (sizing.keyHeight + sizing.keyGap * 2) - sizing.keyGap * 2
        sideColumn.addView(keyButton(key), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply {
            setMargins(sizing.keyGap, sizing.keyGap, sizing.keyGap, sizing.keyGap)
        })
    }

    /** 放開才算的鍵帽按鈕：選單、展開格子、候選列右端的全刪與展開。 */
    private fun capButton(label: String, kind: Cap, onPress: () -> Unit) = Button(this).apply {
        styleAsCap(this, label, kind, dim = false)
        setOnClickListener { onPress() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun keyButton(key: Key): Button {
        val kind = when {
            key.active -> Cap.ACTIVE
            key.special -> Cap.SPECIAL
            else -> Cap.NORMAL
        }
        val button = Button(this)
        styleAsCap(button, key.label, kind, key.dim, space = key.perf == PERF_SPACE)
        // 按下就觸發，不等放開：在 VR 裡手把射線一晃就會滑出按鍵，等放開才算會漏掉
        val repeat = object : Runnable {
            override fun run() {
                measured(key.perf) { key.onPress() }
                main.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    // 鍵面壓下去，字跟著往下移
                    view.setPadding(0, keyDepth, 0, 0)
                    if (fxOn(FxKind.TILT)) KeyboardEffects.tilt(view, pressed = true, density = resources.displayMetrics.density)
                    onKeyHit(view, key)
                    measured(key.perf) { key.onPress() }
                    // 按住連續刪除：先等一下，免得一般點擊變成刪兩個
                    if (key.repeats) main.postDelayed(repeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.setPadding(0, 0, 0, keyDepth)
                    if (fxOn(FxKind.TILT)) KeyboardEffects.tilt(view, pressed = false, density = resources.displayMetrics.density)
                    main.removeCallbacks(repeat)
                }
            }
            true
        }
        return button
    }

    /** 鍵帽外觀：凸起的鍵面、亮色字加字影。字往上留出鍵帽厚度，才會置中在鍵面上。 */
    private fun styleAsCap(button: Button, label: String, kind: Cap, dim: Boolean, space: Boolean = false) = button.apply {
        text = label
        textSize = sizing.keyTextSp
        isAllCaps = false
        // 青玉的鍵面有雲紋與高光，細筆畫撐不住，用粗一級的字重
        typeface = Typeface.create("sans-serif-medium", if (theme.face == KeyFace.JADE) Typeface.BOLD else Typeface.NORMAL)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, keyDepth)
        stateListAnimator = null
        when {
            kind == Cap.ACTIVE -> setTextColor(theme.accentText)
            dim -> setTextColor(KeyboardThemes.withAlpha(labelColor, DIM_TEXT_ALPHA))
            else -> {
                setTextColor(labelColor)
                // 光暈要跟字色反著來。一律用黑影的話，深色字配淺鍵帽等於沒有光暈，
                // 字就直接壓在青玉的雲紋與高光上，筆畫細的注音符號會糊掉。
                val dark = KeyboardThemes.relativeLuminance(labelColor) < LABEL_DARK_THRESHOLD
                val halo = if (dark) WHITE else BLACK
                val alpha = if (dark) LABEL_HALO_ALPHA else LABEL_SHADOW_ALPHA
                val offsetY = if (dark) 0f else dp(1).toFloat()
                setShadowLayer(dp(if (dark) 3 else 2).toFloat(), 0f, offsetY, KeyboardThemes.withAlpha(halo, alpha))
            }
        }
        background = if (fxOn(FxKind.RIPPLE)) {
            KeyboardEffects.ripple(keycap(kind, space), KeyboardThemes.withAlpha(theme.accent, RIPPLE_ALPHA), dp(KEY_RADIUS_DP).toFloat())
        } else {
            keycap(kind, space)
        }
    }

    /**
     * 看起來凸起的鍵帽，不是真的立體：
     * 下緣一條深色邊當厚度、鍵面上亮下暗、上半部一層淡反光；按下時深色邊消失、鍵面移到同一個位置並變暗。
     */
    private fun keycap(kind: Cap, space: Boolean = false): StateListDrawable {
        val (top, bottom) = capColors(kind)
        val active = kind == Cap.ACTIVE
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                capLayers(KeyboardThemes.mix(top, BLACK, PRESSED_DARKEN), KeyboardThemes.mix(bottom, BLACK, PRESSED_DARKEN), raised = false, active = active, space = space),
            )
            addState(intArrayOf(), capLayers(top, bottom, raised = true, active = active, space = space))
        }
    }

    /** 鍵帽顏色從面板底色往按鍵色調：每套配色都帶自己的色調，無彩就是純灰。幾乎不透明，字才看得清楚。 */
    private fun capColors(kind: Cap): Pair<Int, Int> {
        val (top, bottom) = when (kind) {
            Cap.NORMAL -> KeyboardThemes.faceColors(theme, special = false)
            Cap.SPECIAL -> KeyboardThemes.faceColors(theme, special = true)
            Cap.ACTIVE -> KeyboardThemes.mix(theme.accent, WHITE, ACTIVE_BRIGHTEN) to theme.accent
        }
        return KeyboardThemes.withAlpha(top, CAP_ALPHA) to KeyboardThemes.withAlpha(bottom, CAP_ALPHA)
    }

    /**
     * 青玉的鍵帽用圖檔畫（九宮格拉伸），不是程式畫的。
     * 讀不到圖就回 null，讓呼叫端退回程式畫的版本——少一套配色總比整個鍵盤不見好。
     */
    private fun jadeCap(space: Boolean): Drawable? {
        val resourceId = if (space) R.drawable.jade_space else R.drawable.jade_key
        val target = if (space) sizing.keyWidth * 4 else sizing.keyWidth
        val bitmap = JadeArt.load(resources, resourceId, target) ?: return null
        return if (space) {
            JadeArt.NineSliceDrawable(bitmap, JADE_SPACE_INSET_X, JADE_SPACE_INSET_Y)
        } else {
            JadeArt.NineSliceDrawable(bitmap, JADE_KEY_INSET, JADE_KEY_INSET)
        }
    }

    /** 青玉才有圖檔鍵帽；其他配色回 null 走程式畫的。 */
    private fun jadeFace(active: Boolean, space: Boolean): Drawable? =
        if (theme.face == KeyFace.JADE) {
            jadeCap(space)?.also { if (active) it.alpha = JADE_ACTIVE_ALPHA }
        } else {
            null
        }

    private fun capLayers(top: Int, bottom: Int, raised: Boolean, active: Boolean = false, space: Boolean = false): LayerDrawable {
        val radius = dp(if (theme.face == KeyFace.PLAIN) PLAIN_RADIUS_DP else KEY_RADIUS_DP).toFloat()
        val depth = keyDepth
        val face = jadeFace(active, space) ?: KeycapArt.face(theme, top, bottom, radius, dp(1).toFloat(), active)
        if (!raised) {
            return LayerDrawable(arrayOf(face)).apply { setLayerInset(0, 0, depth, 0, 0) }
        }
        // 平面風格與青玉都不要額外的厚度與反光：前者是刻意的，後者圖裡已經畫好了
        if (theme.face == KeyFace.PLAIN || theme.face == KeyFace.JADE) {
            return LayerDrawable(arrayOf(face)).apply { setLayerInset(0, 0, 0, 0, depth) }
        }
        val edge = GradientDrawable().apply {
            cornerRadius = radius
            setColor(KeyboardThemes.withAlpha(BLACK, EDGE_ALPHA))
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(KeyboardThemes.withAlpha(WHITE, SHEEN_ALPHA), Color.TRANSPARENT, Color.TRANSPARENT),
        ).apply { cornerRadius = radius }
        return LayerDrawable(arrayOf(edge, face, sheen)).apply {
            setLayerInset(0, 0, depth, 0, 0)
            setLayerInset(1, 0, 0, 0, depth)
            setLayerInset(2, 0, 0, 0, depth)
        }
    }

    private fun addChip(choice: Choice) {
        candidateRow.addView(Button(this).apply {
            text = choice.text
            textSize = sizing.candidateTextSp
            isAllCaps = false
            minWidth = sizing.keyHeight
            minimumWidth = sizing.keyHeight
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            stateListAnimator = null
            if (choice.highlight) {
                // 第一個候選是膠囊形淡重點色底，其餘透明；候選字不做鍵帽，跟按鍵分得開
                background = flatPressable(KeyboardThemes.withAlpha(theme.accent, FIRST_CANDIDATE_ALPHA), radiusDp = CHIP_RADIUS_DP)
                // 第一候選的字也在面板上：淺面板配色不能用提亮過的重點色（會看不見）
                setTextColor(KeyboardThemes.panelAccentColor(theme))
                setTypeface(typeface, Typeface.BOLD)
            } else {
                // 候選字畫在透明底上，也就是直接畫在面板上：要用面板的字色，不是鍵帽的
                background = flatPressable(Color.TRANSPARENT, radiusDp = CHIP_RADIUS_DP)
                setTextColor(panelTextColor)
            }
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), KeyboardThemes.withAlpha(BLACK, LABEL_SHADOW_ALPHA))
            // 候選字列要能左右捲動，所以維持放開才算，捲動時才不會誤選
            setOnClickListener { measured(PERF_PICK) { choice.onPick() } }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            sizing.candidateHeight - sizing.keyGap * 2,
        ).apply {
            setMargins(sizing.keyGap, sizing.keyGap, sizing.keyGap, sizing.keyGap)
        })
    }

    private fun addLabel(text: String) {
        candidateRow.addView(TextView(this).apply {
            this.text = text
            textSize = sizing.candidateTextSp * 0.8f
            setTextColor(dimText)
            setPadding(dp(12), 0, dp(12), 0)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, sizing.candidateHeight))
    }

    /** 平的（不做鍵帽）按下效果：候選字、表情格子。 */
    private fun flatPressable(color: Int, radiusDp: Int = KEY_RADIUS_DP) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(KeyboardThemes.withAlpha(theme.key, FLAT_PRESSED_ALPHA), radiusDp))
        addState(intArrayOf(), rounded(color, radiusDp))
    }

    private fun rounded(color: Int, radiusDp: Int = KEY_RADIUS_DP) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        private const val TAG = "ZhuyinIme"
        private const val PERF_TAG = "ZhuyinPerf"
        private const val PERF_PICK = "pick"
        /** 配色色塊一排幾個。 */
        private const val SWATCHES_PER_ROW = 5
        private const val VOICE_BARS = "▁▂▃▄▅▆▇█"
        private const val MB = 1024 * 1024

        /** 整個程序共用的詞庫載入器。只在主執行緒碰；載入用 applicationContext，不抓住任何一個服務實體。 */
        private var engineLoader: SharedLoader<Engine>? = null

        private fun sharedEngine(context: Context): SharedLoader<Engine> = engineLoader ?: SharedLoader(
            load = { loadEngine(context) },
            startLoad = { Thread(it, "engine-load").start() },
            deliver = { Handler(Looper.getMainLooper()).post(it) },
        ).also { engineLoader = it }

        private fun loadEngine(context: Context): Engine {
            val open = { name: String ->
                // APK 打包時會把 assets 的 .gz 副檔名去掉，兩種名字都試
                listOf(name, name.removeSuffix(".gz")).firstNotNullOfOrNull { candidate ->
                    try {
                        context.assets.open(candidate)
                    } catch (e: IOException) {
                        null
                    }
                }
            }
            val timer = LoadTimer()
            val start = System.nanoTime()
            val lexicon = Lexicon.load(open, timer)
            val lexiconDone = System.nanoTime()
            val predictor = Predictor.load(open, timer)
            val done = System.nanoTime()
            // 沒有先強制回收，heap 含還沒清掉的解析暫存；PSS 是整個程序實際佔用的記憶體
            val runtime = Runtime.getRuntime()
            val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
            Log.d(
                PERF_TAG,
                "perf load lexiconMs=${ms(lexiconDone - start)} predictorMs=${ms(done - lexiconDone)} " +
                    "totalMs=${ms(done - start)} heapMb=$heapMb pssMb=${Debug.getPss() / 1024}",
            )
            // 讀檔含解壓；parse 是 org.json 解析；build 是轉成查詢用的表；index 是建構時的首符號索引
            Log.d(
                PERF_TAG,
                "perf loadSplit " + timer.millis().entries.joinToString(" ") { (name, millis) ->
                    "$name=${String.format(Locale.US, "%.1f", millis)}"
                },
            )
            return Engine(lexicon, predictor)
        }

        private fun ms(nanos: Long) = String.format(Locale.US, "%.1f", nanos / 1_000_000.0)
        /** 問不到螢幕更新率時的備案；Quest 3S 平常是 72 Hz。 */
        /** 漣漪的透明度：用配色自己的重點色，太濃會蓋掉鍵帽紋樣。 */
        private const val RIPPLE_ALPHA = 0.55f
        /** 流光最亮處的白色透明度。VR 裡太亮會刺眼。 */
        private const val SHEEN_PEAK_ALPHA = 0.14f
        /** 輝光外框的粗細與模糊半徑（dp）。 */
        private const val GLOW_STROKE_DP = 3
        private const val GLOW_BLUR_DP = 7
        /** 輝光的顏色濃度。太濃會把鍵帽的紋樣洗掉。 */
        private const val GLOW_ALPHA = 0.85f
        /** 按一下噴幾顆火花、初速多少（dp/秒）。 */
        private const val SPARK_COUNT = 14
        private const val SPARK_SPEED_DP = 210f
        /** 飛字的落點：輸入框偏左一點，那是游標大概會在的地方。 */
        private const val FLY_TARGET_X = 0.32f
        /** 特效開關一排放幾個。 */
        private const val EFFECTS_PER_ROW = 4
        /** 開了吃資源的特效之後，隔多久自動量一次負載。 */
        private const val AUTO_PROBE_DELAY_MS = 1_500L
        private const val DEFAULT_REFRESH_HZ = 72f
        private const val REPEAT_DELAY_MS = 400L
        private const val REPEAT_INTERVAL_MS = 60L

        /** 字鍵區是 11 欄（大千注音一排最多 11 鍵），右側欄約 1.6 鍵寬。 */
        private const val MAIN_WEIGHT = 11f
        private const val SIDE_WEIGHT = 1.6f

        /** 組字框最寬約佔候選列的三分之一，留空間給候選字。 */
        private const val COMPOSITION_MAX_DP = 180

        private const val GRID_COLUMNS = 6
        /** 超過這個字數的候選（通常是整句）在展開格子裡自己佔一排。 */
        private const val GRID_SHORT_CHARACTERS = 3

        private const val EMOJI_COLUMNS = 10
        /** 表情頁：分類分頁一排、格子三排、功能列一排，合起來跟一般按鍵區一樣高。 */
        /** 表情頁除了格子以外固定佔掉的排數：上面的分類列與下面的功能列。 */
        private const val EMOJI_FIXED_ROWS = 2

        // 圓角：面板大、按鍵中、候選字膠囊形（Quest 密度 1.25 下約 24／16／全圓）
        private const val PANEL_RADIUS_DP = 19
        private const val KEY_RADIUS_DP = 13
        /** 平面風格的圓角：沒有光影撐場面，圓角要更明顯才不會像色塊。 */
        private const val PLAIN_RADIUS_DP = 16
        // 青玉素材的九宮格邊界。全部是量出來的，不是猜的：
        // 先把圖裁到實際內容，再看哪一塊是不能拉伸的（金邊、圓端、雲紋角飾）。
        private const val JADE_KEY_INSET = 0.20f
        private const val JADE_SPACE_INSET_X = 0.06f
        private const val JADE_SPACE_INSET_Y = 0.20f
        /** 候選字條的圓端只佔 3.7%，取 9% 把兩端的山水也含進不拉伸的區域。 */
        private const val JADE_CANDIDATES_INSET_X = 0.09f
        private const val JADE_CANDIDATES_INSET_Y = 0.30f
        /** 候選字條的圓端要留白，字不要壓在端帽上。 */
        private const val JADE_CANDIDATES_PAD_DP = 12
        /** 空白鍵在效能記錄裡的種類名稱；也用來判斷要不要鋪山水玉板。 */
        private const val PERF_SPACE = "space"
        private const val JADE_PANEL_INSET_X = 0.16f
        private const val JADE_PANEL_INSET_Y = 0.22f
        /** Enter 用同一張玉鍵帽，壓亮一點跟一般鍵分開。 */
        private const val JADE_ACTIVE_ALPHA = 255
        /** 煙霧的顏色：鍵帽色往白色調。 */
        private const val MIST_WHITEN = 0.55f
        /** 字色亮度低於這個就算深色字，光暈改用白色。 */
        private const val LABEL_DARK_THRESHOLD = 0.25
        /** 深色字的白色光暈濃度。比黑影濃，因為它要撐出一塊乾淨的底。 */
        private const val LABEL_HALO_ALPHA = 0.75f
        private const val CHIP_RADIUS_DP = 40
        /** 鍵帽厚度：下緣深色邊露出的高度。 */
        private const val KEY_DEPTH_DP = 3

        private val BLACK = KeyboardThemes.rgb(0, 0, 0)
        private val WHITE = KeyboardThemes.rgb(255, 255, 255)

        // 鍵帽：從面板底色往按鍵色調的比例（上緣較亮、下緣較暗），以及幾乎不透明
        private const val CAP_NORMAL_TOP = 0.3f
        private const val CAP_NORMAL_BOTTOM = 0.2f
        private const val CAP_SPECIAL_TOP = 0.18f
        private const val CAP_SPECIAL_BOTTOM = 0.11f
        private const val CAP_ALPHA = 0.94f
        private const val ACTIVE_BRIGHTEN = 0.18f
        private const val PRESSED_DARKEN = 0.2f
        private const val EDGE_ALPHA = 0.45f
        private const val SHEEN_ALPHA = 0.16f

        // 文字：鍵帽上的字往白色提亮，加一層淡字影
        private const val LABEL_BRIGHTEN = 0.45f
        private const val LABEL_SHADOW_ALPHA = 0.55f
        private const val DIM_TEXT_ALPHA = 0.7f

        private const val COMPOSITION_ALPHA = 0.22f
        private const val FIRST_CANDIDATE_ALPHA = 0.26f
        private const val FLAT_PRESSED_ALPHA = 0.2f

        private const val PREFS = "keyboard"
        private const val KEY_EMOJI_RECENTS = "emoji_recents"
        private const val KEY_THEME = "theme"
        private const val KEY_OPACITY = "panel_opacity"
        /** 已經拿掉的放大視窗實驗留下的設定。 */
        private const val LEGACY_WINDOW_SCALE = "window_scale"

        /** 大千排列，跟手機注音鍵盤一樣四排。 */
        private val ZHUYIN_ROWS = listOf(
            "ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦ",
            "ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ",
            "ㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤ",
            "ㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ",
        ).map { row -> row.map { it.toString() } }

        private val ENGLISH_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            .map { row -> row.map { it.toString() } }

        private val FULL_WIDTH_SYMBOL_ROWS = listOf(
            "1234567890",
            "？！、：；「」『』～",
            "（）…—＠＃％＆＊＋",
        ).map { row -> row.map { it.toString() } }

        private val HALF_WIDTH_SYMBOL_ROWS = listOf(
            "1234567890",
            "@#$%&-+()/",
            "*\"':;!?_=~",
        ).map { row -> row.map { it.toString() } }
    }
}

package tw.pinnedbopomofo.quest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tw.pinnedbopomofo.quest.engine.Composer
import tw.pinnedbopomofo.quest.engine.Engine
import tw.pinnedbopomofo.quest.engine.Zhuyin
import java.io.IOException

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
    private var sizing = KeyboardSizing.fit(0, 1f)

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

    /** 符號頁與表情頁沒有自己的語言，跟著切過來之前的那一頁。 */
    private val language get() =
        if (page == KeyboardPage.SYMBOLS || page == KeyboardPage.EMOJI) lastLanguage else page

    /** 鍵帽上的字比一般文字更亮，加上字影，才不會跟鍵面融在一起。 */
    private val labelColor get() = KeyboardThemes.mix(theme.text, WHITE, LABEL_BRIGHTEN)
    private val dimText get() = KeyboardThemes.withAlpha(theme.text, DIM_TEXT_ALPHA)
    private val keyDepth get() = dp(KEY_DEPTH_DP)

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        recents = EmojiRecents.parse(prefs.getString(KEY_EMOJI_RECENTS, null))
        theme = KeyboardThemes.named(prefs.getString(KEY_THEME, null))
        opacity = PanelOpacity.entries.firstOrNull { it.name == prefs.getString(KEY_OPACITY, null) } ?: PanelOpacity.MIST
        // 放大視窗的實驗已經拿掉，清掉它留在設定檔裡的舊值
        if (prefs.contains(LEGACY_WINDOW_SCALE)) prefs.edit().remove(LEGACY_WINDOW_SCALE).apply()

        // 詞庫解析要一兩秒，不能卡住主執行緒；載入前照樣能按鍵
        Thread {
            val loaded = Engine.load { name ->
                // APK 打包時會把 assets 的 .gz 副檔名去掉，兩種名字都試
                listOf(name, name.removeSuffix(".gz")).firstNotNullOfOrNull { candidate ->
                    try {
                        assets.open(candidate)
                    } catch (e: IOException) {
                        null
                    }
                }
            }
            main.post {
                engine = loaded
                composer = Composer(loaded.lexicon)
                refresh()
            }
        }.start()
    }

    override fun onCreateInputView(): View {
        // Horizon OS 把輸入法放在固定 780×355 的面板上，要求更大的視窗也不會給（2026-09 實測）；
        // 手機則是整個螢幕。照實際給的高度排版。
        val available = window.window?.windowManager?.maximumWindowMetrics?.bounds?.height() ?: 0
        sizing = KeyboardSizing.fit(available, resources.displayMetrics.density)
        Log.d(TAG, "input window height=$available density=${resources.displayMetrics.density} sizing=$sizing theme=${theme.name} opacity=$opacity")

        // Quest 沒有視窗模糊，但視窗可以半透明（2026-09 實測）：視窗本身全透明，圓角面板自己帶半透明底色
        window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(KeyboardThemes.withAlpha(theme.tint, opacity.alpha), PANEL_RADIUS_DP)
            setPadding(sizing.padding, sizing.padding, sizing.padding, sizing.padding)
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
        menuPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(menuPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sizing.keyAreaHeight))

        buildKeys()
        refresh(touchEditor = false)
        // Quest 上抓不到輸入法視窗的畫面結構，只能靠記錄確認最下面一排和右側欄有沒有被切掉
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (body.visibility != View.VISIBLE) return@addOnGlobalLayoutListener
            val lastRow = keyArea.getChildAt(keyArea.childCount - 1) ?: return@addOnGlobalLayoutListener
            val lastSide = sideColumn.getChildAt(sideColumn.childCount - 1) ?: return@addOnGlobalLayoutListener
            val rowBottom = body.top + keyArea.top + lastRow.bottom
            val sideBottom = body.top + sideColumn.top + lastSide.bottom
            val sideRight = body.left + sideColumn.right
            val clipped = rowBottom > root.height || sideBottom > root.height || sideRight > root.width
            Log.d(TAG, "layout root=${root.width}x${root.height} lastRowBottom=$rowBottom sideBottom=$sideBottom sideRight=$sideRight clipped=$clipped")
        }
        return root
    }

    /** 最上面一行：左邊是組字框（選定的字＋猜測，下面小字是還沒選的注音），中間候選字，右邊全刪與展開。 */
    private fun buildStrip(): View {
        val strip = horizontalRow().apply { gravity = Gravity.CENTER_VERTICAL }
        composedLine = TextView(this).apply {
            textSize = sizing.candidateTextSp * 0.85f
            setTextColor(labelColor)
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
            background = rounded(KeyboardThemes.withAlpha(BLACK, COMPOSITION_ALPHA))
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
        super.onFinishInput()
        reset()
    }

    private fun reset() {
        composer?.clear()
        predictions = emptyList()
        notice = null
        expanded = false
        menuOpen = false
        refresh(touchEditor = false)
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
        notice = if (composer.type(symbol) || symbol in Zhuyin.TONES) null else "沒有以「$symbol」開頭的讀音"
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
            composer.backspace()
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
        notice = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            "請先打開「智慧優先注音 Quest 原型」App，允許使用麥克風"
        } else {
            "語音輸入還在開發中"
        }
        refresh()
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
        if (text.isNotEmpty()) currentInputConnection?.commitText(text, 1)
        predictions = engine?.predictor?.after(text).orEmpty()
        refresh()
    }

    // ── 畫面 ──────────────────────────────────────────

    private fun refresh(touchEditor: Boolean = true) {
        val composer = composer
        val composing = composer != null && !composer.isEmpty
        // 注音在鍵盤上的組字框看；輸入框顯示轉出的整句，App 收到的才是正常中文
        if (touchEditor) currentInputConnection?.setComposingText(composer?.text() ?: "", 1)
        if (!::candidateRow.isInitialized) return

        compositionBox.visibility = if (composing) View.VISIBLE else View.GONE
        if (composing) {
            composedLine.text = SpannableStringBuilder().apply {
                append(composer!!.fixedText())
                setSpan(ForegroundColorSpan(theme.accent), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(composer.guessText())
            }
            typedLine.text = composer!!.pendingTyped()
        }

        val choices = if (composing) {
            composer!!.candidates().mapIndexed { index, candidate ->
                Choice(candidate.text, highlight = index == 0) {
                    expanded = false
                    if (composer.select(candidate)) commitComposition() else refresh()
                }
            }
        } else {
            predictions.map { prediction -> Choice(prediction, highlight = false) { commit(prediction) } }
        }

        candidateRow.removeAllViews()
        notice?.let { addLabel(it) }
        if (engine == null) addLabel("詞庫載入中…")
        choices.forEach { addChip(it) }

        if (choices.isEmpty()) expanded = false
        clearButton.visibility = if (composing) View.VISIBLE else View.GONE
        expandButton.visibility = if (choices.isEmpty()) View.INVISIBLE else View.VISIBLE
        expandButton.text = if (expanded) "⌃" else "⌄"
        body.visibility = if (expanded || menuOpen) View.GONE else View.VISIBLE
        gridScroll.visibility = if (expanded && !menuOpen) View.VISIBLE else View.GONE
        menuPanel.visibility = if (menuOpen) View.VISIBLE else View.GONE
        if (expanded && !menuOpen) fillGrid(choices)
        if (menuOpen) buildMenu()
    }

    /** 切換選單：換輸入法、返回、配色（每個色塊用該配色自己的顏色）、透明度。 */
    private fun buildMenu() {
        menuPanel.removeAllViews()

        val actions = horizontalRow()
        actions.addView(capButton("切換輸入法", Cap.ACTIVE) { onSwitchKeyboard() }, cellParams(weight = 2f))
        actions.addView(capButton("返回鍵盤", Cap.SPECIAL) {
            menuOpen = false
            refresh(touchEditor = false)
        }, cellParams(weight = 1f))
        menuPanel.addView(actions)

        menuPanel.addView(menuLabel("配色"))
        val swatches = horizontalRow()
        for (candidate in KeyboardThemes.all) {
            swatches.addView(swatch(candidate), cellParams(weight = 1f))
        }
        menuPanel.addView(swatches)

        menuPanel.addView(menuLabel("透明度"))
        val levels = horizontalRow()
        for (level in PanelOpacity.entries) {
            val kind = if (level == opacity) Cap.ACTIVE else Cap.SPECIAL
            levels.addView(capButton(level.label, kind) { applyAppearance(theme, level) }, cellParams(weight = 1f))
        }
        menuPanel.addView(levels)
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
        setTextColor(KeyboardThemes.mix(candidate.text, WHITE, LABEL_BRIGHTEN))
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
            val cell = capButton(choice.text, if (choice.highlight) Cap.ACTIVE else Cap.NORMAL) { choice.onPick() }.apply {
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
                addRow(row.map { symbol -> Key(symbol) { onZhuyin(symbol) } })
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
            Key(if (chinese) "注音" else "English", 3.6f, dim = true) { onSpace() },
            Key(comma) { onLiteral(comma) },
            Key(period) { onLiteral(period) },
        ))

        sideColumn.removeAllViews()
        addTallKey(Key("⌫", special = true, repeats = true) { onBackspace() }, rows = 1)
        addTallKey(Key(EditorPolicy.enterLabel(enterAction), active = true) { onEnter() }, rows = 2)
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
        val gridHeight = EMOJI_GRID_ROWS * (sizing.keyHeight + sizing.keyGap * 2)
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
        styleAsCap(button, key.label, kind, key.dim)
        // 按下就觸發，不等放開：在 VR 裡手把射線一晃就會滑出按鍵，等放開才算會漏掉
        val repeat = object : Runnable {
            override fun run() {
                key.onPress()
                main.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    // 鍵面壓下去，字跟著往下移
                    view.setPadding(0, keyDepth, 0, 0)
                    key.onPress()
                    // 按住連續刪除：先等一下，免得一般點擊變成刪兩個
                    if (key.repeats) main.postDelayed(repeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.setPadding(0, 0, 0, keyDepth)
                    main.removeCallbacks(repeat)
                }
            }
            true
        }
        return button
    }

    /** 鍵帽外觀：凸起的鍵面、亮色字加字影。字往上留出鍵帽厚度，才會置中在鍵面上。 */
    private fun styleAsCap(button: Button, label: String, kind: Cap, dim: Boolean) = button.apply {
        text = label
        textSize = sizing.keyTextSp
        isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
                setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), KeyboardThemes.withAlpha(BLACK, LABEL_SHADOW_ALPHA))
            }
        }
        background = keycap(kind)
    }

    /**
     * 看起來凸起的鍵帽，不是真的立體：
     * 下緣一條深色邊當厚度、鍵面上亮下暗、上半部一層淡反光；按下時深色邊消失、鍵面移到同一個位置並變暗。
     */
    private fun keycap(kind: Cap): StateListDrawable {
        val (top, bottom) = capColors(kind)
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                capLayers(KeyboardThemes.mix(top, BLACK, PRESSED_DARKEN), KeyboardThemes.mix(bottom, BLACK, PRESSED_DARKEN), raised = false),
            )
            addState(intArrayOf(), capLayers(top, bottom, raised = true))
        }
    }

    /** 鍵帽顏色從面板底色往按鍵色調：每套配色都帶自己的色調，無彩就是純灰。幾乎不透明，字才看得清楚。 */
    private fun capColors(kind: Cap): Pair<Int, Int> {
        val (top, bottom) = when (kind) {
            Cap.NORMAL -> KeyboardThemes.mix(theme.tint, theme.key, CAP_NORMAL_TOP) to KeyboardThemes.mix(theme.tint, theme.key, CAP_NORMAL_BOTTOM)
            Cap.SPECIAL -> KeyboardThemes.mix(theme.tint, theme.key, CAP_SPECIAL_TOP) to KeyboardThemes.mix(theme.tint, theme.key, CAP_SPECIAL_BOTTOM)
            Cap.ACTIVE -> KeyboardThemes.mix(theme.accent, WHITE, ACTIVE_BRIGHTEN) to theme.accent
        }
        return KeyboardThemes.withAlpha(top, CAP_ALPHA) to KeyboardThemes.withAlpha(bottom, CAP_ALPHA)
    }

    private fun capLayers(top: Int, bottom: Int, raised: Boolean): LayerDrawable {
        val radius = dp(KEY_RADIUS_DP).toFloat()
        val depth = keyDepth
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = radius
        }
        if (!raised) {
            return LayerDrawable(arrayOf(face)).apply { setLayerInset(0, 0, depth, 0, 0) }
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
                setTextColor(KeyboardThemes.mix(theme.accent, WHITE, ACTIVE_BRIGHTEN))
                setTypeface(typeface, Typeface.BOLD)
            } else {
                background = flatPressable(Color.TRANSPARENT, radiusDp = CHIP_RADIUS_DP)
                setTextColor(labelColor)
            }
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), KeyboardThemes.withAlpha(BLACK, LABEL_SHADOW_ALPHA))
            // 候選字列要能左右捲動，所以維持放開才算，捲動時才不會誤選
            setOnClickListener { choice.onPick() }
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
        private const val EMOJI_GRID_ROWS = 3

        // 圓角：面板大、按鍵中、候選字膠囊形（Quest 密度 1.25 下約 24／16／全圓）
        private const val PANEL_RADIUS_DP = 19
        private const val KEY_RADIUS_DP = 13
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

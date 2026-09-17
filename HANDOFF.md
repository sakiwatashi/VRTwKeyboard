# Quest 注音輸入法：交接文件

> 最後更新：2026-09-16 深夜（第二個對話：修掉排版監聽器殘留 §7、§9；效能量測與共用詞庫載入 §6；語音輸入階段 A §7、`docs/VOICE_INPUT_PLAN.md`）。寫給接手的下一個對話（AI 或人）。先讀完這份再動手。
> 使用者用繁體中文溝通，要求說明詳細、有實測根據，不要只給標題。

## 1. 一句話現況

在使用者的 **Meta Quest 3S（Horizon OS 2.7）** 上，這個 Android 系統輸入法**已經實際可用**：能啟用、能切換、能用手機式注音連打整句、逐段選字、表情、切換配色與透明度。總審查評分 **7.3 / 10**，屬於「可日常試用的原型，離發布還有一段」。程式碼已提交在本機 commit `9e402b9`（未 push）。

相關連結：
- 總審查報告（評分、風險、未來方向）：https://claude.ai/artifact/6rdYr3B16LztnN4Nkb9c2Y
  - 原始檔：`quest-bopomofo-ime/docs/quest-zhuyin-review.html`
- 鍵盤美術設計稿（A–D 風格、六套配色）：https://claude.ai/artifact/HutqwMTVerTtmLTa4X3hhK
  - 原始檔：`quest-bopomofo-ime/design/keyboard-styles/`

---

## 2. 專案位置與結構

外層是多專案工作區 `C:\Users\may05\Documents\New project 2`。本專案在 `quest-bopomofo-ime/`，**跟 Windows 版輸入法 `pime-bopomofo-core/` 是兩個專案**，但 Quest 版直接讀 Windows 版的詞庫資料（見 §6）。

```
quest-bopomofo-ime/
├─ HANDOFF.md                     ← 本文件
├─ settings.gradle.kts / build.gradle.kts / gradle.properties
├─ gradle/wrapper/gradle-wrapper.properties   （只有 properties，沒有 gradlew 腳本與 jar）
├─ gradle/gradle-daemon-jvm.properties        （Android Studio 自動產生，JDK toolchain 25）
├─ local.properties               （git 忽略；sdk.dir=C:/Users/may05/Android/Sdk）
├─ app/build.gradle.kts           （AGP 9.4.0、compileSdk 37、targetSdk 34、minSdk 32；assets 直接指向 pime 詞庫資料夾）
├─ app/src/main/AndroidManifest.xml（只有 RECORD_AUDIO 權限，沒有 INTERNET）
├─ app/src/main/java/tw/pinnedbopomofo/quest/      （以下行數都不含空白行；編輯器顯示的行數會比較多）
│  ├─ ZhuyinImeService.kt  1062 行  輸入法本體：所有畫面、按鍵、候選列、選單、表情頁、鍵帽繪製、效能記錄、語音按鍵（下面其他檔案的行數是第一個對話時的數字）
│  ├─ MainActivity.kt       121 行  診斷畫面（狀態、開啟鍵盤設定、切換輸入法、麥克風權限、三個測試欄位）
│  ├─ EditorPolicy.kt        48 行  依輸入框類型決定頁面、Enter 動作與標籤；KeyboardPage 列舉
│  ├─ KeyboardSizing.kt            依系統給的視窗寬高決定按鍵尺寸：寬度上限 640dp 置中、鍵高不超過鍵寬 2.5 倍（2026-09-16 修）
│  ├─ KeyboardTheme.kt       50 行  六套配色、三段透明度、顏色混合/透明度純計算
│  ├─ EmojiCatalog.kt        47 行  8 個表情分類
│  ├─ EmojiRecents.kt        17 行  最近用過的表情（最多 30）
│  ├─ SharedLoader.kt               整個程序只載入一次詞庫（見 §6「載入時間」）
│  ├─ voice/                        語音輸入階段 A（見 §7、docs/VOICE_INPUT_PLAN.md）
│  │  ├─ AudioCapture.kt            AudioRecord 16 kHz 單聲道、每框 20 ms
│  │  ├─ Endpointer.kt              只看音量判斷開口／說完（校正噪音底、上限）
│  │  ├─ VoiceSession.kt            一次語音輸入的狀態機＋SpeechEngine 介面＋VoiceState
│  │  └─ VoiceModels.kt             模型放哪、檔案齊不齊
│  └─ engine/
│     ├─ Lexicon.kt         166 行  詞庫查詢（完整讀音＋不完整音節比對）
│     ├─ PhraseDecoder.kt    65 行  整句詞網格（移植自 Python）
│     ├─ Composer.kt        112 行  手機式連打與逐段選字的狀態
│     ├─ Predictor.kt        48 行  聯想詞
│     ├─ Zhuyin.kt           21 行  注音符號分類、聲調、音節延伸規則
│     ├─ JsonData.kt         27 行  讀 JSON（自動辨識 gzip）、Unicode 字元切分
│     └─ Engine.kt            6 行  Lexicon＋Predictor 組合
├─ app/src/test/java/…           54 項單元測試（見 §9）
├─ tools/check-layout-listener.ps1  頭盔記錄檢查：排版監聽器有沒有殘留（見 §9）
├─ tools/measure-perf.ps1         效能記錄整理：載入時間與細分、每鍵時間、重複載入、服務實體、記憶體（見 §6）
├─ design/keyboard-styles/        設計稿原始檔（見 §10）
├─ docs/perf-baseline-2026-09-16.{md,log}       效能基準（修正前，同一程序載入兩次）
├─ docs/perf-shared-loader-2026-09-16.log       共用載入器版的冷啟動（單次載入 8.5 秒）
├─ docs/VOICE_INPUT_PLAN.md                      語音輸入計畫、模型比較、下載同意清單、來源
└─ docs/quest-zhuyin-review.html  總審查報告原始檔
```

---

## 3. 開發環境（路徑都是實際值）

| 項目 | 值 |
|---|---|
| Android SDK | `C:\Users\may05\Android\Sdk`（2026-09-16 從 Claude MSIX 私人資料夾搬出來，見 §3.2） |
| JDK | `C:\Program Files\Android\Android Studio\jbr`（Android Studio 2026.1.4.7 內附） |
| Gradle | `C:\Users\may05\.gradle\manual\gradle-9.6.0\bin\gradle.bat`（手動下載，不是 wrapper） |
| SDK 內容 | platform-tools、emulator、cmdline-tools/latest（22.0）、build-tools 36.0.0、platforms android-37.0、system-images android-34 default x86_64 |
| 模擬器 AVD | `quest_ime_api34`（Pixel 7 外型、API 34、無 Google 服務、無實體鍵盤），存在 `%USERPROFILE%\.android\avd` |
| 頭盔用的 adb | **SideQuest 內附的** `C:\Users\may05\AppData\Local\Programs\SideQuest\resources\platform-tools\adb.exe`（34.0.5） |
| 頭盔 | Quest 3S，序號 `340YC10G9G042G`，Wi-Fi IP `192.168.1.198`，無線 adb 埠 `5555` |
| C 槽 | 只剩約 5.7 GB（2026-09-16）。暫存區 1.7 GB 安裝檔隨舊對話消失即可；使用者尚未決定是否清其他空間 |

### 3.1 常用指令（PowerShell）

建置＋測試：
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\may05\Android\Sdk"
Set-Location "C:\Users\may05\Documents\New project 2\quest-bopomofo-ime"
& "$env:USERPROFILE\.gradle\manual\gradle-9.6.0\bin\gradle.bat" testDebugUnitTest assembleDebug --console=plain
# APK：app\build\outputs\apk\debug\app-debug.apk（約 6.0 MB）
# 測試結果：app\build\test-results\testDebugUnitTest\*.xml
```

安裝到頭盔（先確認連線穩定再裝）：
```powershell
$adb = "C:\Users\may05\AppData\Local\Programs\SideQuest\resources\platform-tools\adb.exe"
$d = "192.168.1.198:5555"
& $adb connect $d
& $adb -s $d shell echo ok          # 回 ok 才代表連線真的可用
& $adb -s $d install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $d shell settings get secure default_input_method
& $adb -s $d logcat -c
& $adb -s $d shell am start -n tw.pinnedbopomofo.quest/.MainActivity
```

讀鍵盤記錄（Quest 上抓不到輸入法畫面，只能靠記錄）：
```powershell
& $adb -s $d logcat -d -s ZhuyinIme:D     # input window height / layout root / theme / picker 等
& $adb -s $d logcat -d -b crash           # 當機
& $adb -s $d shell run-as tw.pinnedbopomofo.quest cat shared_prefs/keyboard.xml   # 設定檔
& $adb -s $d shell dumpsys meminfo tw.pinnedbopomofo.quest   # 看 Views: 數量（一份鍵盤約 80–150）
```

**請使用者戴頭盔測試之前，先開背景記錄存到檔案**（頭盔的記錄緩衝區只有 256 KB，幾分鐘就洗掉，見 §3.2）。
用 PowerShell 工具的 `run_in_background` 執行，檔案放對話的暫存區；要保留的證據測完再搬進專案：
```powershell
& $adb -s $d logcat -v time -s ZhuyinIme:D InputMethodManager:V ImeTracker:V cr_Ime:V chromium:W *:S |
    Out-File "<暫存區>\capture.log" -Encoding utf8
```
`cr_Ime` 是 Meta 瀏覽器（Chromium）的輸入法記錄，查瀏覽器裡的輸入問題靠它。

判斷頭盔上跑的是不是剛建的那份：`pm path` 找到 APK → `adb pull` → 比對 APK 內 **全部** `classes*.dex` 的 SHA-256。
- **不要比整個 APK 的雜湊**：內容相同的兩次建置，外層打包資訊不同，整檔雜湊就不同。
- **也不要只比 `classes.dex`**：這個專案有 6 個 dex，2026-09-16 的尺寸修正只改到 `classes4.dex`，只比第一個會誤判成「已經是新版」。

### 3.2 環境地雷（每一條都踩過）

- **Claude 桌面版是 MSIX 封裝**：從 Claude 啟動的程式寫 `%LOCALAPPDATA%` 會被改寫到 `C:\Users\may05\AppData\Local\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Local\...`。SDK 原本就是這樣「看起來在 AppData、其實在 LocalCache」。**不要再把工具裝進 AppData**；`%USERPROFILE%` 底下、Documents 不受影響。
- **使用者從開始功能表開 Android Studio** 時，要自己到「設定 › Languages & Frameworks › Android SDK」把位置改成 `C:\Users\may05\Android\Sdk`（Studio 的設定在 AppData，從 Claude 裡改會被改寫到 LocalCache，外面看不到）。
- **不要同時用兩個版本的 adb**：SDK 的 adb 是 37.0.1、SideQuest 的是 34.0.5，同時跑會搶 5037 埠互相踢掉。操作頭盔時用 SideQuest 那一份；要開模擬器前先 `adb kill-server`、確認頭盔這邊沒在用。
- **Gradle 官方下載點經 GitHub 發行檔 CDN，從台灣很慢**（0.1 MB/s）；`curl.exe` 比 `Invoke-WebRequest` 快。騰訊雲鏡像 10 MB/s，但換下載來源要先問使用者。
- **PowerShell 的 `Expand-Archive` 在暫存區長路徑下會失敗**（超過 260 字）；用 `tar.exe -xf` 解到短路徑。
- **在 adb shell 字串裡的 `$` 會被頭盔的 shell 吃掉**：元件名要寫 `'...Settings\$AvailableVirtualKeyboardActivity'`。
- **工具的安全檢查會擋含 `rm` 的指令**（尤其跟 XPath 字串混在一起時），抓頭盔檔案改放 `/data/local/tmp/` 不刪除。
- **頭盔記錄緩衝區只有 256 KB**（`logcat -g`），系統記錄很多，幾分鐘前的記錄就會被洗掉。2026-09-16 兩次踩到：存「修改前」證據時檔案是空的；使用者回報瀏覽器問題時，發生當下的記錄已經沒了。測試前一律先開背景記錄（§3.1）。
- **Gradle 顯示 `UP-TO-DATE` 時測試根本沒重跑**，讀到的是舊結果。要驗證就加 `--rerun`（只重跑該任務）或 `--rerun-tasks`，並確認 `test-results` 的 XML 修改時間晚於這次執行。
- **讀測試結果 XML 要 `Get-Content -Raw -Encoding UTF8`**：測試名稱是中文，Windows PowerShell 預設編碼讀進來會解析失敗。
- **`.ps1` 含中文就要存成有 BOM 的 UTF-8**，否則 Windows PowerShell 5.1 讀錯。Write 工具寫出來沒有 BOM，寫完要用 `[IO.File]::WriteAllText(路徑, 內容, (New-Object Text.UTF8Encoding($true)))` 補上。

---

## 4. 頭盔連線與操作流程

1. **無線 adb 建立方式**：使用者用 USB-A 轉 C 線接電腦（已勾「一律允許這部電腦」），`adb -s 340YC10G9G042G tcpip 5555` 後 `adb connect 192.168.1.198:5555`，就可以拔線。**重開機後無線模式會失效**，要再插一次線。
2. **頭盔脫下一陣子就會休眠**，無線 adb 會「連上又立刻離線」或整個連不上（ping 不通、10060 逾時）。處理：請使用者戴上頭盔喚醒，再 `connect` → `shell echo ok` 確認 → 才安裝。判斷有沒有重開機：`adb shell cat /proc/uptime`。
3. Quest 3S 的 Android 休眠設定讀不到真正的自動休眠時間（`screen_off_timeout` 是 24 小時、`sleep_timeout=-1`，但實際不照這個走）。使用者頭盔的「設定 › 電源」可能有自動休眠選項，尚未確認。曾提議用開發者方式暫停休眠偵測，**使用者沒有同意，不要自己做**。
4. **找 App**：Meta 鍵 › 應用程式庫 › 右上角篩選「未知來源」 › 「智慧優先注音 Quest 原型」。也可以從電腦 `am start` 叫出來。
5. **切換輸入法**：
   - 我們的鍵盤上：右側欄「切換」→ 選單最上面「切換輸入法」。
   - Meta 原生鍵盤上**沒有切換鍵**，要回 App 按「切換輸入法」，或請 AI 從電腦下 `adb shell ime set tw.pinnedbopomofo.quest/.ZhuyinImeService`（使用者有請求時才做）。
6. **重新安裝**：在頭盔上重裝**不會**把輸入法關掉或改預設（模擬器上會）；但從頭盔換成 Meta 鍵盤後，預設就是 Meta 的，要記得切回來。

---

## 5. Quest 平台實測事實（非推論）

| 事實 | 證據 |
|---|---|
| Horizon OS 2.7 = 建置號 `ro.vros.build.version=207`，Android 14 / API 34，建置日期 2026-08-26 | `getprop` |
| 第三方輸入法會被系統登記 | 安裝後 `ime list -a -s` 出現 `tw.pinnedbopomofo.quest/.ZhuyinImeService` |
| `ACTION_INPUT_METHOD_SETTINGS` 被 `com.oculus.vrshell/.intents.AndroidIntentsRelayActivity`（優先權 2）搶走，導到沒有第三方開關的 Meta 設定 | `cmd package resolve-activity`、記錄 |
| Android 原生「螢幕鍵盤」頁還在且可開：`com.android.settings/.Settings$AvailableVirtualKeyboardActivity`，可在這裡啟用我們的輸入法 | 使用者在頭盔上實際打開開關，`enabled_input_methods` 出現我們 |
| `showInputMethodPicker()` 可用（從 App 或從鍵盤上呼叫都行） | 使用者成功切換，`default_input_method` 改變 |
| **輸入法面板尺寸會變，不要寫死**：2026-09-15 是 780×355（`setLayout(1248,568)` 無效）；**09-16 重開機後同一個 OS 版本改成回報整個顯示器 3664×1920**，鍵盤被排成長條；稍後又回報 3664×455（等於我們上次排出的高度，是回饋迴圈）。同時鍵盤變成可移動、可縮放的獨立面板 | `dumpsys window`、`input window width/height` 記錄、使用者目視 |
| **輸入法可以錄音**：IME 服務用 AudioRecord（VOICE_RECOGNITION、16 kHz）錄到真實聲音，說話峰值 0.66、安靜 0.147，不是被餵靜音 | 2026-09-16 `voice heard speechMs=2580 peak=0.66`／`voice noSpeech peak=0.147` |
| **可以半透明**（視窗 `fmt=TRANSLUCENT` 生效，使用者看得到後面） | 使用者目視 |
| **不支援視窗模糊**：`ro.surface_flinger.supports_background_blur` 未設定、`mBlurEnabled=false`、App 端 `isCrossWindowBlurEnabled=false`、合成器沒有任何一層模糊半徑非 0 | 2026-09-15 實測 |
| `uiautomator dump` 抓不到輸入法視窗；`screencap` 抓到的是左右眼鏡片畫面，看不到面板 | 實測 |
| 頭盔上鍵盤行程記憶體：TOTAL PSS 約 117 MB、Java heap 約 61 MB（整份詞庫 JSON 解析） | `dumpsys meminfo` 2026-09-16 |
| 系統語音辨識服務：**無**（`SpeechRecognizer.isRecognitionAvailable=false`） | 診斷畫面 |
| 頭盔上的輸入法視窗（`ty=INPUT_METHOD`）在瀏覽器上方是獨立視窗；Meta 瀏覽器（150.1.0.24.52）的網址／搜尋建議清單**不是**獨立視窗，畫在瀏覽器視窗裡 | `dumpsys window windows` 2026-09-16 |
| Meta 瀏覽器偶爾會進入「叫不出鍵盤」的狀態：`showSoftInput` 連續被系統略過（`Ignoring showSoftInput() as view=… is not served`），鍵盤服務根本沒被叫到；**關掉瀏覽器面板再打開就恢復** | 背景記錄 2026-09-16 00:58:35–00:58:50 |

---

## 6. 選字引擎（engine/）

資料來源：`app/build.gradle.kts` 用 `androidComponents.onVariants { sources.assets.addStaticSourceDirectory(...) }` 把 `../pime-bopomofo-core/bopomofo_core/data` 直接當 assets，不複製。
- **打包時 assets 的 `.gz` 副檔名會被去掉**：`reading_phrases.json.gz` 在 APK 裡叫 `reading_phrases.json`。所以 `JsonData.readJson` 看檔頭（`1f 8b`）判斷 gzip，服務端的 opener 兩種名字都試。
- 用到的資料檔：`reading_phrases.json.gz`（178,148 條、146,919 個讀音）、`extra_phrases.json`（補詞，例如「這座」）、`tone_sandhi.json`（變調）、`taiwan_preferred.json`、`polyphone_weights.json`、`variant_demotions.json`（異體字降權 ÷1000）、`high_frequency_phrases.json`（Rime Essay）、`taiwan_frequency.json`（教育部 1996 詞頻）。

### 載入時間（2026-09-16 頭盔實測，debug 版）
- **冷啟動到可以選字約 8.5 秒**：詞庫 6.1 秒、聯想詞 2.5 秒（`docs/perf-shared-loader-2026-09-16.log`）。之前寫的「一兩秒」是錯的。
- 01:16 那次同一個程序 `onCreate` 跑了兩次、詞庫同時載入兩份（8.9 秒）；01:29 那次只建一個實體。**多久發生一次不知道**。
  - 已加 `SharedLoader`：整個程序共用一份、服務結束時取消請求（4 項測試，證明過會失敗）。這次量測中它沒派上用場，不能說它讓啟動變快。
- **真正的瓶頸是解析那份 7.4 MB 的 JSON**。計畫：第 0 階段先拆時間（`LoadTimer`，記錄 `perf loadSplit`：`phrases.read/parse/build`、`small.*`、`index`、`predictor.read/parse/collect/build`），**程式已完成、APK 已建好，尚未裝到頭盔量**；確認瓶頸後才做第 1 階段「建置時產生二進位詞庫」（方案 A 新模組需下載 `org.jetbrains.kotlin.jvm` 插件索引，要先問使用者）。
- 量測：戴頭盔前先開背景記錄（§3.1），`powershell -File tools\measure-perf.ps1 -LogFile <記錄>`。記錄只記按鍵種類，不記打了什麼。

### Lexicon.kt
- 移植自 `pime-bopomofo-core/bopomofo_core/reading_phrase_lexicon.py`：權重調整順序 = 原始權重 → 破音字權重 → 台灣寫法覆寫 → 異體字 ÷1000（保底 1）；補詞/變調只補沒有的詞。快取上限 512。
- 讀音格式：音節以空白分隔、**每個音節帶聲調，一聲寫成 `ˉ`**（例 `ㄧㄣˉ`、`ㄓㄜˋ ㄗㄨㄛˋ`）。
- **不完整音節**（手機式）：任一音節沒帶聲調就走 `matched()`：先用「各音節第一個符號」索引縮小範圍，再逐音節比對（有聲調要完全相同；沒聲調則「讀音去掉聲調後以打的符號開頭」），同一個詞取最高權重。Python 實算對照：只打 `ㄉ` 有 825 個候選，「打」第 10；`ㄉㄚ` 時「打」第 1、「大」第 2；`ㄉㄚ ㄐㄧㄚ` 時「大家」第 1。

### PhraseDecoder.kt
- 移植 `phrase_decoder.py`：比較順序 = 讀音涵蓋 → 長詞比例（compaction）→ Σ ln(1+權重)；相等保留先到的。**個人詞庫（personal）那層沒有移植。** 支援 `protected`（鎖定的字形成硬邊界）。

### Composer.kt（手機式連打＋逐段選字）
- `type(symbol)`：注音依「聲母 < 介音 < 韻母」順位接到最後一個音節，接不上就另起一個音節；聲調鍵把最後一個音節加上或換成新聲調；最後一個音節已選定時只能另起。組不出任何讀音就回傳 false。
- `fixedCount`：開頭已選定的音節數；重算整句時這些位置是 `protected`，不會被改掉。
- `candidates()`：從第一個未選定音節開始；第一個永遠是「剩下部分的整句猜測」，接著由長到短列詞，上限 60。
- `select(candidate)`：只固定那一段，回傳 true 代表整句都選完（服務端就送出）。
- `backspace()`：刪最後一個注音；如果最後面都是選定的字，就把最後一個選定的字變回注音。
- 對外文字：`text()` 整句、`typed()` 全部注音、`fixedText()` 已選、`guessText()` 猜測、`pendingTyped()` 未選部分的注音。

### Predictor.kt（聯想詞）
- 取二到四字詞；Rime Essay 語料中**三字以上的詞要在台灣詞表裡才收**（擋掉「市場份額、市盈率」），兩字詞不過濾（「一個、我的」台灣詞表裡反而沒有）；最後補上台灣詞表的詞（語料把「台」都轉成「臺」，「台灣」只在台灣詞表）。每個字最多 15 個。

---

## 7. 鍵盤畫面與行為（ZhuyinImeService.kt）

### 版面（在 780×355 內）
```
┌[組字框：已選字(重點色)+猜測 / 下排小字=未選注音]│ 候選字（可橫捲，第一個膠囊淡底）│ ✕ │ ⌄ ┐  高 50
│ ㄅ ㄉ ˇ ˋ ㄓ ˊ ˙ ㄚ ㄞ ㄢ ㄦ                                            │ ⌫   │
│ ㄆ ㄊ ㄍ ㄐ ㄔ ㄗ ㄧ ㄛ ㄟ ㄣ                                             │ ⏎   │（兩排高、重點色）
│ ㄇ ㄋ ㄎ ㄑ ㄕ ㄘ ㄨ ㄜ ㄠ ㄤ                                             │     │
│ ㄈ ㄌ ㄏ ㄒ ㄖ ㄙ ㄩ ㄝ ㄡ ㄥ                                             │ 切換 │
│ 123 · 🌐 · 🎤 · 😀 · [ 注音/English 空白 ] · ，· 。                     │ 收起 │
└──────────────────────────────────────────────────────────────┴──────┘
```
- 字鍵區與右側欄權重 11 : 1.6。實測：面板 780×351、最後一排底部 348、右側欄底部 346、右邊界 776，沒有切掉（每次開鍵盤會記錄 `layout root=… clipped=…`）。
- `KeyboardSizing.fit`：手機照原尺寸（候選列 52dp、按鍵 56dp、間距 3dp、內距 4dp、字 22sp）；放不下就等比縮小（Quest：候選列 50、按鍵 55、間距 2、內距 3、字約 17.2sp）。

### 輸入行為
- 注音鍵、功能鍵**按下就觸發**（不等放開，VR 射線會晃）；⌫ 按住 400ms 後每 60ms 連刪。
- 候選字、展開格子、選單、表情格子、✕、⌄ **放開才算**（要能捲動、避免誤觸）。
- 空白鍵：有組字就送出整句，否則輸入空白。⏎：有組字就送出；欄位有動作（搜尋/前往/傳送/完成…）就執行；否則送 Enter 按鍵事件（聊天 App 靠它送出）。
- 輸入框裡顯示**轉出的整句**（composing text）；注音只在鍵盤上的組字框看。
- ✕ 全刪：只清組字中的注音與選定字，只在組字時出現。⌄：把按鍵區換成候選字格子（一排 6 個，超過 3 字的自己佔一排），⌃ 回來。
- 欄位類型：密碼/信箱/網址 → 英文頁；數字/電話/日期 → 符號頁；一般文字沿用上次語言。
- 英文頁：⇧ 一次=大寫一個字、兩次=鎖定（⇪）、三次=關。符號頁：從注音來是全形、從英文來是半形。
- 表情頁：分頁 🕘 常用＋8 類，一排 10 個、3 排高可捲；點了直接送出並記進常用（`SharedPreferences` 最多 30）。還沒用過時停在第一類。
- 🎤（語音輸入階段 A，**還沒在頭盔上驗證**）：麥克風權限要在 App 裡先允許（輸入法服務本身跳不出對話框）。有權限時按 🎤 開始錄音，候選列顯示「🎙 請說話／正在聽＋音量條」；說完安靜 0.9 秒自動停，或再按 🎤 停止；6 秒沒開口顯示「沒有聽到說話」，全程音量 0 顯示「可能被系統擋下」。**還沒接辨識引擎**（要下載 sherpa-onnx 與模型，需使用者同意），所以只顯示「聽到約 X 秒」。收起鍵盤、離開輸入框、服務結束都會取消錄音。聲音不存檔、記錄只有時長／狀態／最大音量。
- 右側欄「切換」→ 選單：「切換輸入法」（系統選單）、「返回鍵盤」、配色六色塊（用各配色自己的底色/字色，目前的白色粗框）、透明度三段。按了立即重建鍵盤並存檔，選單保持打開。

### 外觀
- 配色（`KeyboardThemes.all`）：暖茶（預設）、抹茶、靛夜、霧櫻、石墨金、無彩（全灰，Enter 與第一候選靠亮灰與粗體區分）。
- 透明度（`PanelOpacity`）：清透 0.2、霧面 0.6（預設）、實色 0.92。**35% 與 50% 在頭盔上看不出差別**，所以拉開。
- 視窗背景全透明，圓角面板（19dp）自己帶半透明底色。
- **擬物鍵帽**（使用者要求「看起來凸起但不是真的凸」）：`LayerDrawable` = 下緣深色邊（黑 45%，往下錯開 3dp）＋上亮下暗漸層鍵面（面板底色往按鍵色混 30%→20%，功能鍵 18%→11%，94% 不透明）＋上半部淡反光；按下時只剩鍵面、移到下緣位置、變暗 20%，字的 padding 同步往下。
- 字：鍵帽上的字往白色提亮 45%、`sans-serif-medium`、淡字影（黑 55%）。**使用者曾反映「注音跟背景融為一體」**，就是鍵面太透、字太暗造成的。
- **所有橫排都用 `horizontalRow()`（`isBaselineAligned = false`）**：之前按一個鍵整排一起「按下去」，原因是按下時字往下移，LinearLayout 為了對齊文字基準線把整排都推下去。新增橫排一定要用這個 helper。
- **換配色／透明度會用 `setInputView(onCreateInputView())` 重建整個鍵盤**，所以 `onCreateInputView` 裡掛的任何監聽器都要跟著 root 一起回收：
  - 排版檢查記錄（`layout root=… clipped=… id=…`）用 `root.addOnLayoutChangeListener`。
  - **不要用 `root.viewTreeObserver.addOnGlobalLayoutListener`**：它會併進整個輸入法視窗的監聽清單、永遠不會移除，而且抓著舊鍵盤不放。2026-09-16 實測：換 3 次外觀後 Views 601、同一次排版有 4 個鍵盤在回報；改掉後換 5 次外觀仍只有目前的鍵盤回報，Views 82。
  - 記錄裡的 `id=` 是 root 的 `identityHashCode`，給 `tools/check-layout-listener.ps1` 判斷用，不要拿掉。
- 設定檔 `SharedPreferences("keyboard")`：`theme`（配色名）、`panel_opacity`（CLEAR/MIST/SOLID）、`emoji_recents`（換行分隔）。啟動時會清掉舊實驗的 `window_scale`。

### 已移除的實驗（不要再做一次）
- 要求更大的視窗（1.3/1.6 倍）：系統不給，已刪除。
- `setBackgroundBlurRadius` 背景模糊：平台不支援，已刪除。

---

## 8. MainActivity（診斷畫面）

顯示 Android SDK、系統組建、已啟用/目前選用、麥克風權限、系統語音辨識服務。按鈕：開啟鍵盤設定（**指名開原生螢幕鍵盤頁**，失敗才退回一般 intent）、切換輸入法、允許使用麥克風。欄位：一般注音欄、搜尋欄（Enter 顯示「搜尋」）、密碼欄（自動英文）。整個畫面包在 ScrollView（Quest 的 App 視窗只有 500×800）。這是開發用的畫面，**還不是給一般使用者的引導**。

---

## 9. 測試（54 項，全部通過；第二個對話新增的 20 項都證明過會失敗）

| 檔案 | 項數 | 內容 |
|---|---|---|
| `engine/EngineTest.kt` | 17 | 用真實詞庫：「這座城市」不變成「蔗作城市」、一聲寫 ˉ、不打聲調/只打聲母有候選、`ㄉㄚㄐㄧㄚ`→大家、音節切分、接不上另起、倒退鍵、全刪、異體字排序、逐段選字、選定字不被重算、倒退鍵把選定字變回注音、聯想詞三項 |
| `EditorPolicyTest.kt` | 5 | 欄位類型→頁面、Enter 動作與標籤 |
| `KeyboardSizingTest.kt` | 5 | 手機原尺寸、Quest 780×355 放得下、**整個顯示器 3664×1920 時面板不鋪滿要置中**、**窄面板時鍵高不超過鍵寬 2.5 倍**、各種高度不超出 |
| `KeyboardThemeTest.kt` | 6 | 預設配色退回、無彩沒有色相、透明度換算、顏色混合、無彩鍵帽沒有色相、名稱不重複 |
| `EmojiRecentsTest.kt` | 3 | 去重移到最前、上限 30、存讀一致（含多字元表情） |
| `SharedLoaderTest.kt` | 4 | 同時要只載入一次、載入完直接給、取消不回呼、載入中取消再要仍只載入一次 |
| `engine/LoadTimerTest.kt` | 2 | 每一段都有計時、有沒有計時結果相同（讀真實詞庫） |
| `voice/EndpointerTest.kt` | 5 | 開口與說完的時間點、一直沒開口、風扇噪音不算、講太久、音量條（合成聲音 `Signals.kt`） |
| `voice/VoiceSessionTest.kt` | 5 | 辨識不在錄音執行緒、字頭不被切（含開口前緩衝）、沒模型回報秒數、沒開口按停止、取消不回呼、引擎出錯 |
| `voice/VoiceModelsTest.kt` | 2 | 檔案齊全才算安裝 |

- `VoiceSessionTest` 的「字頭不被切」原本門檻太鬆（拿掉緩衝也會過），已改成 ≥ 2000 ms 並證明拿掉緩衝會失敗。

- 詞庫測試讀 `../../pime-bopomofo-core/bopomofo_core/data`；環境變數 `ENGINE_TEST_DATA` 可指向刻意拿掉檔案的副本。
- **專案慣例：每個新測試都先證明會失敗**（暫時改壞程式→跑該測試→原樣還原並比對 SHA-256→再跑全部）。這是使用者記憶裡的規則（「不會失敗的測試」）。
- JVM 測試不能呼叫 Android 的 `Color` 等方法（stub 會丟例外），所以顏色計算寫成純 Kotlin 位元運算。

### 頭盔上的檢查：`tools/check-layout-listener.ps1`
畫面框架的行為（監聽器掛在哪、會不會殘留）JVM 單元測試測不到；要在電腦上測得加 Robolectric（新下載，要先問使用者，目前沒加）。所以這類問題用「讀頭盔記錄」的腳本檢查：
```powershell
powershell -NoProfile -File tools\check-layout-listener.ps1            # 直接讀頭盔（也印 Views 數量）
powershell -NoProfile -File tools\check-layout-listener.ps1 -LogFile x  # 讀背景記錄存下的檔案
```
- 做法：請使用者開鍵盤、換 3 次配色或透明度、打幾個字，然後執行。
- 判定：相隔 20ms 內的 layout 記錄算同一次排版；同一次排版出現超過一個 `id` 就失敗。結束碼 0 通過、1 失敗、2 無法判定（沒有記錄，或是沒有 `id` 的舊版記錄）。
- **已證明會失敗**（2026-09-16）：暫時把監聽器改回 `viewTreeObserver` 建一份 APK 裝到頭盔 → 使用者換 3 次外觀 → 判定失敗（5 次排版有多個鍵盤回報、最多 4 個，Views 601）→ 原始碼還原並比對 SHA-256 → 裝回修好的版本 → 判定通過。使用者回報兩個版本手感一樣。

---

## 10. 設計稿

- 位置：`design/keyboard-styles/`。`.dc.html` 是畫板原始檔，`canvas.json` 是版面：A 現行深灰、B 半透明玻璃（Main）、C 墨與朱、D 暖色玻璃六套配色（WarmGlass＋Glass*）。
- `make-glass-themes.mjs`：從 `WarmGlass.dc.html` 複製出其他配色畫板（只改配色預設值）。用法：`node design/keyboard-styles/make-glass-themes.mjs <keyboard-styles 資料夾>`。
- 組出的畫布 `quest-zhuyin-keyboard-styles.html`（約 2.5 MB，含編輯器）被 git 忽略。要更新設計稿：在新對話用 `/design`（Claude Design 預覽功能）重新組裝並用同一個 artifact 網址發布；設計稿上的「毛玻璃模糊」在頭盔上做不到（§5）。
- 使用者的美術偏好歷程：喜歡 C 的色彩 → 要 Apple 那種玻璃感與圓角 → 要對比調低 → 要多套配色與無彩 → 實機上要「看起來凸起」與「字要清楚」。實作版以實機可讀性優先。

---

## 11. Git 狀態與規則

- 分支：`codex/inputmethod-release`（外層工作分支）。**絕對不能 push**（upstream 指向已封存 repo；根目錄 `AGENTS.md` 與 `pime-bopomofo-core/AI_MAINTENANCE.md` 有寫）。
- 已提交：`9e402b9 feat: Quest 3S 注音輸入法原型`（39 檔、3,505 行，全在 `quest-bopomofo-ime/`）。
- 這個分支上有**大量其他專案的未提交修改**（memory-maze-cardgame 等）。提交時：先確認 `git diff --cached --name-only` 是空的 → 只 `git add -- quest-bopomofo-ime` → 檢查暫存檔全在資料夾內 → commit。只在使用者要求時 commit。
- commit 訊息慣例：中文、`feat:`/`fix:`/`docs:` 前綴，結尾加 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`。
- 以下都是 `9e402b9` 之後的修改，**尚未 commit**（使用者兩次說「先不提交」，要提交等使用者開口）：
  - 第一個對話結尾：本文件、`docs/quest-zhuyin-review.html`、`design/keyboard-styles/make-glass-themes.mjs`、根目錄 `AGENTS.md` 的 Quest 段落。
  - 第二個對話：`ZhuyinImeService.kt` 排版監聽器修正（`addOnLayoutChangeListener`＋記錄加 `id=`）、`tools/check-layout-listener.ps1`、本文件更新。
  - 第二個對話後段：效能記錄（`ZhuyinPerf`）、`tools/measure-perf.ps1`、`docs/perf-*`、`SharedLoader.kt`＋測試、`engine/LoadTimer.kt`＋`Lexicon/Predictor/JsonData/Engine` 計時參數＋測試、`voice/`＋測試、`docs/VOICE_INPUT_PLAN.md`、`KeyboardSizing` 寬度修正＋測試。
  - 頭盔上目前裝的是「共用載入器」那一版（01:28）；**第 0 階段計時與語音階段 A 的 APK 已建好但還沒裝**（頭盔休眠）。
  - `lintDebug` 有 1 個錯誤在被忽略的 `local.properties`（路徑 `:` 沒跳脫），跟程式無關，沒動，等使用者決定。
- 根目錄 `AGENTS.md` 在 git 下有 LF→CRLF 的警告，是換行設定造成的，內容沒問題。

---

## 12. 已知問題、風險與技術債（依影響排序）

1. **高｜Meta 可能再封鎖第三方輸入法**（v77.1027 曾拿掉切換介面），而啟用流程依賴指名開啟原生設定頁。對策：保留「App 內打字再複製」退路；每次 Horizon OS 更新回歸檢查：780×355、半透明、原生設定入口、輸入法選單。
2. **中｜發布授權**：詞庫含 LGPL-2.1+（libchewing-data）、LGPL-3.0（Rime Essay）、MIT（McBopomofo）、教育部開放資料。App 內還沒有授權聲明頁。
3. **高｜冷啟動 8.5 秒**（§6「載入時間」）；打注音每鍵 frame 中位約 80 ms、p95 約 140 ms，時間大多在重建最多 60 個候選按鈕（相關、未證明）。載入後 PSS 約 120–140 MB。
4. **中｜ZhuyinImeService.kt 1062 行（不含空白行）**，畫面全用程式碼組出，又加了效能記錄與語音按鍵，拆分更迫切。
4b. **中｜瀏覽器建議清單關不掉（未定案）**：2026-09-16 使用者在 Meta 瀏覽器遇到「網址／搜尋的建議清單關不掉」，之後自己恢復。發生當下沒有記錄（緩衝區洗掉了），**不能確定是不是我們的問題**；同一段時間有錄到瀏覽器「叫不出鍵盤」的狀態（§5），比較像瀏覽器焦點卡住。程式裡有三個可疑點，**沒有證據前先不改**：
   - `commit()` 送出文字後會呼叫 `refresh()`，又對輸入框送一次空的 `setComposingText("")`（選字、空白、Enter、「收起」都會）；網頁可能當成又輸入了一次而重新打開建議清單。
   - 沒有覆寫 `onUpdateSelection`：使用者點建議項目或移動游標時，鍵盤不知道組字已經被打斷。
   - 沒有覆寫 `onFinishInputView`：鍵盤被系統收起（不是按「收起」）時沒有結束組字。
   - 下次重現：先開背景記錄（§3.1），請使用者記下時間、網站、輸入框，並用 Meta 原生鍵盤做同樣操作對照；只有我們的鍵盤會卡住，才在鍵盤加送字／組字記錄、重現、再修。
5. **中｜語音輸入**：階段 A（錄音＋說話偵測）已實作未驗證；最大風險是 Horizon OS 讓不讓輸入法錄音。辨識引擎要下載 AAR 47.8 MB＋模型約 228 MB，**需使用者同意**（清單在 `docs/VOICE_INPUT_PLAN.md`）。
6. **低｜無線 adb 受頭盔休眠影響。**
7. 只有 debug 簽章、沒有 release 設定；沒有 gradlew 腳本；沒有介面/儀器測試；聯想詞不會學習；沒有游標移動與句中修改；明體字型在頭盔上是否存在未確認；表情沒有膚色。

已解決（2026-09-16）：專案進版控（9e402b9）、SDK 搬出 MSIX 私人資料夾（重新編譯與測試通過）、換配色／透明度時舊鍵盤殘留在記憶體（排版監聽器改掛 root，頭盔上驗證，見 §7、§9；尚未 commit）。

---

## 13. 下一步（依審查報告的階段，順序有意義）

**頭盔一醒來就做（APK 已建好，都不需要下載）：**
- 先開背景記錄（§3.1，含 `ZhuyinPerf:D ZhuyinIme:D`）→ 安裝 → 比對 dex。
- 請使用者開一次鍵盤等載入完 → `measure-perf.ps1` 看「載入細分」→ 決定二進位詞庫計畫（使用者已同意計畫，方案 A 的下載還要問）。
- 請使用者按 🎤 測三種情況：說一句話等它自動停、按了不說話等 6 秒、說到一半收起鍵盤 → 讀 `voice …` 記錄（看 peak 是否為 0）。

**第一階段剩下的：**
1. ~~加計時記錄~~：已完成（`ZhuyinPerf`、`tools/measure-perf.ps1`、基準在 `docs/perf-baseline-2026-09-16.md`）。還缺：照 13 音節長句步驟補量打字。
2. 正式簽章與版本號（`versionCode`/`versionName` 目前 1 / 0.0.1）。
3. 拆分 `ZhuyinImeService.kt`：鍵帽繪製（keycap/capColors/capLayers）、候選列（buildStrip/addChip/fillGrid）、選單（buildMenu/swatch）、表情頁、按鍵區（buildKeys/addRow/addTallKey）。拆的時候保留 `horizontalRow()` 規則。

**第二階段（選字引擎對齊 Windows 版）：**
- 移植 `pime-bopomofo-core/bopomofo_core/` 的個人詞庫與上下文（`pinned_store.py`、`context_store.py`、`phrase_decoder.py` 的 personal 層與 `PERSONAL_OVERRIDE_RATIO=10`、`_is_near_miss`）、錯字修正（`autocorrect.py`、`common_typos.json`）、近音修正（`phonetic_corrector.py`）。動 Windows 版之前先讀 `pime-bopomofo-core/AI_MAINTENANCE.md`。
- 詞庫改成預先建好的二進位索引，降低 61 MB heap 與啟動時間。

**第三階段（體驗）：** 首次使用引導（三步驟：開原生螢幕鍵盤頁啟用 → 切換輸入法 → 試打）、游標左右移、句中修改、按鍵音效、明體可行性、表情膚色。

**第四階段（離線語音）：** sherpa-onnx（有 Android/Kotlin API，AAR 從 GitHub releases）；中文模型候選：串流 Zipformer（幾十 MB）或 SenseVoice Small（維護者說明可商用需標註）；輸出多為簡體，接 OpenCC s2twp（android-opencc 或 OpenccJava）。**模型下載前要先把檔名、來源、大小告訴使用者並取得同意。**

**第五階段（發布）：** 授權聲明與隱私說明（強調離線）、先上 SideQuest、確認 Horizon Store 對輸入法 App 的政策、在 Quest 3 等機型驗證。

---

## 14. 跟使用者合作的注意事項

- 全程繁體中文；使用者會戴著頭盔測試並口頭回報，常用手機拍照給截圖。AI 從電腦讀記錄驗證，**記錄只能證明「排得進去、沒當機」，好不好用要問使用者**。
- 需要使用者操作頭盔時，給**完整路徑**（Meta 鍵 › 應用程式庫 › 未知來源 …），不要只說「去設定」。
- 下載檔案、改系統設定、關休眠偵測、換下載來源、刪除檔案、push：**都要先問**。
- 使用者的記憶檔在 `C:\Users\may05\.claude\projects\C--Users-may05-Documents-New-project-2\memory\`，本專案相關：`quest-ime-platform-limits.md`、`claude-msix-appdata-redirect.md`、`a-test-that-cannot-fail.md`、`verify-what-the-user-actually-runs.md`、`pime-release-git-flow.md`。
- 使用者的全域規則：問到會變動的產品/版本/價格，**第一次回答前先搜尋**並附來源。

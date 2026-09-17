# VRTwKeyboard — Meta Quest 臺灣注音輸入法

給 Meta Quest 的注音輸入法。用 Android `InputMethodService` 寫成，介面全部由程式繪製，
在 Horizon OS 上以手把射線或手勢操作。內建**離線語音輸入**，不連網也能說話打字。

**狀態：可以每天用的原型。**還沒上架商店，安裝要透過 adb。

---

## 目錄

- [安裝](#安裝)
- [啟用輸入法](#啟用輸入法)
- [打字教學](#打字教學)
- [語音輸入](#語音輸入)
- [換配色與特效](#換配色與特效)
- [從原始碼建置](#從原始碼建置)
- [疑難排解](#疑難排解)
- [授權](#授權)

---

## 安裝

需要一台開啟開發者模式的 Quest，以及電腦上的 `adb`。

### 1. 開啟 Quest 的開發者模式

手機上的 Meta Horizon App →「裝置」→ 選你的頭盔 →「開發者模式」打開。
頭盔接上 USB 線，戴上頭盔時會跳出「允許 USB 偵錯」，選允許。

### 2. 一鍵部署

```powershell
.\tools\deploy.ps1
```

這支腳本會做完全部四件事：下載語音模型（約 226 MB，含 SHA256 驗證，會快取）、
安裝 APK、把模型推進頭盔、啟用輸入法。找不到 `adb` 的話會自動去找 SideQuest 內附的那份。

只想裝鍵盤、不要語音：

```powershell
.\tools\deploy.ps1 -SkipModels
```

或者手動裝：

```powershell
adb install -r app-debug.apk
adb shell pm list packages | Select-String pinnedbopomofo
```

### 3.（選用）改用無線連線

拔線之前先開無線偵錯，之後就不用一直插著：

```powershell
adb tcpip 5555
adb connect <頭盔的IP>:5555
```

頭盔 IP 在「設定 →  Wi-Fi → 目前連線的網路」裡。
**頭盔重開機之後 5555 會關掉**，要插線重跑一次 `adb tcpip 5555`。

---

## 啟用輸入法

這一步在 Quest 上有個坑：Horizon OS 把標準的「輸入法設定」捷徑攔走了，
點下去只會跳回 vrshell。要**指名開啟原生設定頁**：

```powershell
adb shell am start -n com.android.settings/.Settings`$AvailableVirtualKeyboardActivity
```

> PowerShell 裡的 `$` 要用反引號跳脫（`` `$ ``）。
> 在 bash 裡則是寫成 `Settings\$AvailableVirtualKeyboardActivity`。

頭盔上會出現「虛擬鍵盤」清單：

1. 把 **注音輸入法** 打開
2. 系統會警告「這個輸入法可以收集你輸入的文字」——這是所有第三方輸入法都會有的
   標準警告。本程式**不連網、不上傳任何東西**，語音辨識也完全在裝置上跑
3. 回到任何可以打字的地方，點一下輸入框
4. 如果還是跳出系統鍵盤，按鍵盤上的**切換輸入法**鍵，或執行：

```powershell
adb shell ime set tw.pinnedbopomofo.quest/.ZhuyinImeService
```

---

## 打字教學

鍵盤是**大千（標準）注音鍵位**，跟 Windows 微軟注音同一套排法。

```
ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦ          ⌫
ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ          ⏎
ㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤ          ⏎
ㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ         切換
[123][🌐][🎤][😀][ 注音 ][，][。] 收起
```

### 基本流程

1. **直接打注音**，例如 `ㄋㄧˇ ㄏㄠˇ`
2. 上方的**候選字列**會即時給出整句結果，第一個候選會被標示出來
3. 按 **空白鍵**（就是那顆寫著「注音」的長鍵）或 **⏎** 送出
4. 想換字就直接**點候選字列**上的其他選項

### 幾個好用的地方

| 你想做的事 | 怎麼做 |
|---|---|
| 看更多候選字 | 按候選列右邊的 `⌄` 展開成格子 |
| 清掉還沒送出的注音 | 按候選列左邊的 `✕` |
| 打英文 | 按 `🌐` 切到英文頁，再按一次切回來 |
| 打數字或標點 | 按 `123` |
| 打表情符號 | 按 `😀`，上排切分類；按「注」回到注音 |
| 全形／半形標點 | 從注音頁進 `123` 是全形，從英文頁進是半形 |
| 收起鍵盤 | 按右下角「收起」 |

### 整句選字

它不是逐字選字，而是**整句一起比對**。打 `ㄇㄟˇ ㄩˋ ㄉㄠˋ ㄧ ㄐㄩˋ ㄒㄧㄣ ㄐㄩˋ ㄗ˙`
會得到「每遇到一句新句子」，不會變成「美譽心」。

打錯近音也會自動修：`ㄘㄥˊ ㄕㄨˋ ㄐㄧㄠˋ ㄍㄠ` 出來是「層數較高」，不是「曾恕叫高」。

---

## 語音輸入

**完全離線**。聲音不會離開頭盔，沒有任何連網行為。

### 第一次使用

1. **模型要先用電腦推進去**——`.\tools\deploy.ps1` 會處理。
   App **不會自己下載**，因為它根本沒有網路權限（見下方說明）
2. 開啟 App 本體（應用程式清單裡的「注音輸入法」），授予**麥克風權限**。
   輸入法自己跳不出權限對話框，一定要從 App 裡給
3. 回到鍵盤，按 `🎤`

### 為什麼模型不由 App 自己下載

輸入法看得到你打的每一個字。這個 App 的 `AndroidManifest.xml` 裡**只有
`RECORD_AUDIO` 一項權限，沒有 `INTERNET`**——它在技術上就沒有能力把任何東西送出去，
不必只靠我們口頭保證。

代價是模型得由電腦端的腳本下載再推進去。我們認為這個交換划算。

### 使用

按 `🎤` → 直接說話 → 停頓約 1 秒就會自動結束並送出。

- 說話時會看到**即時的部分辨識結果**
- 輸出是**臺灣正體**（模型輸出簡體，內部用 OpenCC 轉換）
- 再按一次 `🎤` 可以取消

### 已知限制

中文辨識還不錯，但**英文專有名詞會被拆壞**：「Minecraft」可能變成「MCRAFT」、
「Call of Duty」變成「COFDUTY」。這是模型本身的限制（Paraformer 不支援 hotwords），
目前沒有繞過的辦法。

---

## 換配色與特效

按鍵盤上的**「切換」**鍵打開選單，分三個分頁。

### 外觀

- **十一套配色**：暖茶、抹茶、靛夜、霧櫻、石墨金、無彩、磚塊、竹簡、青玉、素白、霓虹
  - **青玉**是用美術圖做的（背景板、鍵帽、候選條、山水空白鍵）
  - **磚塊**是瑪利歐風格的交錯磚
  - **霓虹**是夜市燈牌，配「輝光」特效才完整
  - **素白**是乾淨的淺色系統風
- **透明度**三段：清透、霧面、實色。VR 裡背景會透過鍵盤看到，依環境亮度選

### 特效

八種，可以單獨開關，也可以一鍵「建議組合」或「全部關閉」。

| 特效 | 說明 |
|---|---|
| 輝光 | 鍵帽後面一層模糊光暈，霓虹配色開了才像霓虹燈 |
| 粒子 | 按鍵噴出火花 |
| 飛字 | 注音符號沿拋物線飛進輸入框 |
| 漣漪 | 按鍵的水波擴散 |
| 傾斜 | 鍵帽以底邊為支點往後倒 24 度 |
| 流光 | 一道光帶定時掃過鍵盤 |
| 聲波 | 語音輸入時跟著**真實音量**起伏的波形 |
| 煙霧 | 幾團淡霧緩慢飄過 |
| 衝擊波 `*` | 按鍵擴散出能量環。標星號代表比較吃資源 |

### 診斷

按「測一次」會量這台裝置的硬體加速、實際重畫速度、shader 支援度，結果寫進
logcat（tag `ZhuyinIme`）。特效卡頓時可以用這個確認是裝置問題還是程式問題。

---

## 從原始碼建置

需要 JDK 21+、Android SDK（compileSdk 37）、Gradle 9.6+。
本專案**沒有 `gradlew` 腳本**，用系統裝的 Gradle。

```powershell
# 1. 抓不進版控的相依檔（sherpa-onnx AAR，50 MB，含 SHA256 驗證）
.\tools\fetch-deps.ps1

# 2. 建置與測試
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
gradle testDebugUnitTest assembleDebug --console=plain
```

產出在 `app\build\outputs\apk\debug\app-debug.apk`。

### 測試

115 支單元測試，涵蓋選字引擎、版面尺寸、對比度、語音斷句、特效模擬與九宮格切圖。

```powershell
gradle testDebugUnitTest --rerun-tasks --console=plain
```

> **注意**：Gradle 顯示 `UP-TO-DATE` 時測試根本沒重跑，讀到的是舊結果。
> 要驗證就加 `--rerun-tasks`。

本專案的慣例是**新測試要先證明它有能力變紅**：故意破壞對應的程式碼、確認測試失敗、
還原後比對 SHA-256。不會失敗的測試等於沒有測試。

### 專案結構

```
app/src/main/java/tw/pinnedbopomofo/quest/
├─ ZhuyinImeService.kt      輸入法本體與版面
├─ KeyboardTheme.kt         配色定義與對比度計算
├─ KeycapArt.kt             程式繪製的鍵帽（磚塊、霓虹、竹簡、青玉）
├─ KeyboardSizing.kt        面板尺寸計算
├─ engine/                  選字引擎（詞網格、詞庫、近音修正）
├─ voice/                   語音輸入（錄音、VAD、辨識、簡繁轉換）
└─ effects/                 特效（模擬本體與繪製層分開，模擬的部分可單元測試）

app/src/main/assets/        注音詞庫（4.7 MB）
app/src/main/res/drawable-nodpi/  青玉配色的美術素材
```

---

## 疑難排解

**找不到「注音輸入法」的開關**
標準的輸入法設定被 vrshell 攔走，要用上面那條 `am start` 指令指名開啟原生設定頁。

**鍵盤變成很寬的一條**
輸入法面板尺寸由系統決定，而且**會變**。量過 780×355，頭盔重開機後同一個系統版本
回報過 3664×1920。程式會自己把關寬高，若還是跑版請回報 logcat 裡的 `input window` 那一行。

**adb 連得上但指令都失敗（device offline）**
頭盔重開機後無線偵錯會失效。插 USB 線重跑 `adb tcpip 5555`。

**語音按了沒反應**
先確認在 App 本體裡給過麥克風權限。輸入法自己跳不出權限對話框。

**想看診斷記錄**

```powershell
adb logcat -s ZhuyinIme:*
```

---

## 授權

### 本專案

[Apache License 2.0](LICENSE)。可自由使用、修改、散布，含專利授權；
散布時保留版權聲明與 [`NOTICE`](NOTICE)。

### 第三方元件

完整盤點見 [`docs/licenses/third-party.md`](docs/licenses/third-party.md) 與 [`NOTICE`](NOTICE)。

| 元件 | 授權 |
|---|---|
| sherpa-onnx | Apache-2.0 |
| ONNX Runtime | MIT |
| opencc4j | Apache-2.0 |
| Silero VAD | MIT |
| Paraformer 語音模型 | 倉庫標 apache-2.0（上游鏈未完全確認） |

### 詞庫

`app/src/main/assets/` 的注音詞庫來自
[smart-priority-bopomofo](https://github.com/sakiwatashi/keyinput123)，
上游轉製自 **libchewing**（LGPL-2.1）、**McBopomofo**（MIT）
與 **Rime Essay**（LGPL-3.0）的資料。

兩個 copyleft 上游都是 **LGPL**（函式庫型），不是 GPL——實務上的意思是
**詞庫資料本身**要維持 LGPL 並可取得，但**程式碼不受影響**。
詞庫是純 JSON 且隨 repo 公開，這個義務自然滿足。

---

## 已知限制

- 語音辨識的英文專有名詞會被拆壞（模型限制）
- 聯想詞不會學習
- 沒有游標移動與句中修改
- Horizon OS 沒有跨視窗模糊，毛玻璃只能用半透明模擬
- 語音模型必須用電腦部署，App 端沒有下載功能（這是刻意的，見上方）
- 只有 debug 簽章，沒有 release 設定

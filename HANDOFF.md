# Quest 注音輸入法：交接文件

> 最後更新：2026-09-18 凌晨。寫給接手的下一個對話（AI 或人）。**先讀完這份再動手。**
>
> 使用者用繁體中文溝通，要求說明詳細、有實測根據，不要只給標題。
> 他會直接說「這很醜」「我不懂你的意思」——那是有效回饋，照做就好，不要辯解。
> 卡住一直用同一個方法猜的時候他會直接說「不要再猜了」，那時候要換方法驗證，不要再改同一段程式碼賭一次。

---

## 1. 一句話現況

**專案已經公開發佈，App 也已經裝上頭盔可以用了。** 程式在 Meta Quest 3S（Horizon OS 2.7）上：
注音整句選字、離線語音輸入、十一套配色、八種特效。

公開位置：

| | 網址 | 狀態 |
|---|---|---|
| 原始碼 | https://github.com/sakiwatashi/VRTwKeyboard | Apache-2.0，已公開 |
| 安裝頁 | https://sakiwatashi.github.io/VRTwKeyboard/ | 已上線，**但還沒成功裝過一次，見下方 §2、§9** |
| Release | https://github.com/sakiwatashi/VRTwKeyboard/releases/tag/v0.2.0 | 附簽章 APK |

**2026-09-18 凌晨：改用 `adb.exe install -r` 直接把 v0.2.0 APK 裝上頭盔，成功（回 `Success`）。**
這條路跳過了網頁安裝器，是為了不讓「網頁安裝器卡住」擋住「App 能不能用」——兩件事現在是分開的。
**網頁安裝器（WebUSB 直接跑 ADB）本身到目前為止仍然一次都沒成功過**，診斷細節見 §9，
已知不是 JS 邏輯寫錯（封包送對了、Consumable 用法對了），懷疑是 Chrome 對這顆頭盔 USB 介面的
獨占聲明機制，優先度不高，App 已經能用，可以之後再查。

---

## 2. 立刻要做的事

App 已經能用了，接下來是發布後續：

1. **拍 SideQuest 上架要的六張截圖**（清單在 `docs/sidequest-submission.md`），App 已經裝好、
   現在是最方便拍的時候。
2. 建 SideQuest listing，加 GitHub webhook（見 §6「SideQuest」小節，webhook 目前還沒加）。
3. 網頁安裝器要不要繼續修，問使用者——不是發布的擋路石了，是加分項。
   繼續查的話先看 §9 的診斷紀錄，**不要從頭用同一招（改 `docs/index.html` 猜）**，
   先用 `adb.exe devices` / `Get-PnpDevice` 這類系統工具確認頭盔本身狀態，
   而且測試時 Chrome 跟 adb.exe **不要同時開**（見下方環境地雷）。

頭盔目前**已經裝好 App**（不是乾淨狀態了）。本機還有模型快取，
語音模型要下載可以直接在頭盔裡按，或用 `.\tools\deploy.ps1`（幾秒，不用重新下載 226 MB）。

---

## 3. 專案位置與 git

外層是多專案工作區 `C:\Users\may05\Documents\New project 2`（remote 是 `keyinput123`）。
本專案在 `quest-bopomofo-ime/`。

**公開的 repo 是用 subtree split 推的**，不要直接把外層分支推到 VRTwKeyboard：

```powershell
cd "C:\Users\may05\Documents\New project 2"
git add quest-bopomofo-ime
git commit -m "..."
git branch -D vrtw-release
git subtree split --prefix=quest-bopomofo-ime -b vrtw-release
git push https://github.com/sakiwatashi/VRTwKeyboard.git vrtw-release:main
```

本機分支：`codex/inputmethod-release`。已提交且已推送到 VRTwKeyboard 的 main。

### 不進版控但必須存在的東西

| 路徑 | 是什麼 | 怎麼取得 |
|---|---|---|
| `app/libs/sherpa-onnx-1.13.8.aar` | 語音函式庫，50 MB | `.\tools\fetch-deps.ps1`（含 SHA256 驗證） |
| `.models/` | 語音模型快取，226 MB | `deploy.ps1` 會下載 |
| `local.properties` | SDK 路徑 **與 release 簽章密碼** | 見下 |
| `C:\Users\may05\Documents\VRTwKeyboard-signing\` | **release 簽章金鑰** | **遺失＝永遠無法再發更新**。已請使用者自行備份 |

---

## 4. 環境

| | |
|---|---|
| JDK | `C:\Program Files\Android\Android Studio\jbr` |
| Gradle | `C:\Users\may05\.gradle\manual\gradle-9.6.0\bin\gradle.bat`（手動下載，**沒有 gradlew**） |
| adb | `C:\Users\may05\AppData\Local\Programs\SideQuest\resources\platform-tools\adb.exe` |

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:USERPROFILE\.gradle\manual\gradle-9.6.0\bin\gradle.bat" `
  :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --console=plain
```

### 環境地雷（每一條都踩過，不要再踩）

- **Gradle 顯示 `UP-TO-DATE` 時測試根本沒重跑**，讀到的是舊結果。一律加 `--rerun-tasks`。
- **heredoc 會吃掉反斜線**。這個對話裡踩了**四次**：`\t` 變 Tab、`\b` 變退格、
  `\n` 變真換行害 JS 語法錯誤。**寫任何含反斜線的內容一律用 Write 工具**，
  或在 Python 裡用 `chr(92)`。改完一定要驗（`cat -A` 或 `node --check`）。
- **Python 在 Windows 上讀不到 `/tmp/...`**，要用 `%TEMP%` 或環境變數傳遞。
- **bash 的 `/sdcard/...` 會被 MSYS 轉成 Windows 路徑**，要 `export MSYS_NO_PATHCONV=1`。
- **commit 訊息裡的反引號會被 bash 當成指令執行**。用 `-F` 讀檔，不要用 `-m`。
- **PowerShell 的 `-p` 之類參數會被當成參數名吃掉**，傳給外部程式要用陣列。
- **PowerShell 裡陣列的 `-notmatch` 回傳「不符合的元素」不是布林值**，非空恆為真。
- **判斷頭盔上跑的是不是新版**：比對 APK 內**全部** `classes*.dex` 的 SHA-256，
  只比 `classes.dex` 會被騙。
- **無線 adb 在頭盔重開機後會失效**（連得到但 `device offline`），要插線重跑 `adb tcpip 5555`。
  這個對話裡斷線了七八次，很浪費時間——長時間工作建議直接插線。
- **Chrome 用 WebUSB 連過頭盔之後，`adb.exe` 會看不到裝置**，就算那次連線失敗、就算頁面程式碼
  呼叫了 `device.raw.close()` 也沒用、reload 頁面也沒用——**只有把 Chrome 整個關掉（所有視窗）
  才會真正放掉**。這個對話裡來回中獎好幾次，浪費很多時間才確認。要交替測試 adb.exe 跟瀏覽器時，
  一定要先確認 Chrome 完全關閉，兩者不要同時開著碰同一顆頭盔。
- **自己手動跑的 `adb start-server` 診斷完要記得 `kill-server`**，忘記關的話它會在頭盔喚醒時
  自動搶走 ADB 介面，讓瀏覽器那邊出現「device is already in use by another program」，
  這個對話裡就是這樣自己害自己卡住一次。
- **頭盔的 USB 組合模式會變**（用 `Get-PnpDevice -PresentOnly` 查 `VID_2833` 看得到）：
  有時是 `PID_5012`（有 MTP 儲存空間「Quest 3S」+ ADB Interface），有時變成 `PID_5013`
  （沒有儲存空間，多了 XRSP/Crosswind/Highwind，可能是 Quest Link 相關模式）。
  兩種模式下 `Get-PnpDevice` 都顯示 ADB Interface 狀態 OK，但只有 `PID_5012` 那個模式
  實測 `adb.exe devices` 抓得到裝置。卡住時先查現在是哪個 PID，不要假設一定是同一個。

---

## 5. Quest 平台實測事實（非推論）

這些是量出來的，不是查文件得到的。記憶檔 `quest-ime-platform-limits` 也有一份。

- **面板尺寸會變，不要寫死。** 量過 780×355，頭盔重開機後同一個系統版本
  回報過 3664×1920。程式自己把關（寬度上限、鍵的長寬比上限、置中留白）。
- **輸入法視窗有硬體加速，而且跑得很順**：`hwAccel=true`、90 fps、兩秒掉 0 幀、
  p95 只比 p50 多 0.3 ms、**AGSL shader 編得過**。所以連續特效可行。
  量的時候要數 **onDraw**，不是 Choreographer 回呼。
- **沒有跨視窗模糊**，但 `RenderEffect` 模糊**我們自己畫的東西**可以。
- **啟用第三方輸入法**：`ACTION_INPUT_METHOD_SETTINGS` 被 vrshell 攔走，
  要指名開 `com.android.settings/.Settings$AvailableVirtualKeyboardActivity`。
  該頁有 `BROWSABLE`+`DEFAULT`，**一般 App 叫得動**（App 裡的按鈕就是這樣做的）。
- **頭盔自己裝不了 APK**。Quest 上沒有任何 App 宣告 `REQUEST_INSTALL_PACKAGES`
  （瀏覽器、檔案管理員都查過），而 Meta 把那個權限列入商店禁止清單。
  這是刻意的封鎖，不是我們沒做。
- **`REQUEST_INSTALL_PACKAGES` 寫在 manifest 不等於拿得到**：Android 8 之後
  要使用者在系統設定裡逐個 App 開啟。實測 `granted=false`。
- **Meta 商店上不了**：`BIND_INPUT_METHOD` 在禁止權限清單上，
  上傳驗證會自動失敗。第三方輸入法這條路在 Horizon Store 是不通的。

---

## 6. 散布方式（這個對話的主軸）

### 為什麼是網頁安裝器

使用者明確拒絕「要先裝 SideQuest 才能用我們的東西」。而頭盔本身裝不了 APK（見 §5）。
所以做了 `docs/index.html`：**一個靜態網頁，用 Chromium 的 WebUSB 直接跑 ADB**。
使用者開網址、插線、按一下，不安裝任何軟體。

ADB 實作用 [Tango](https://github.com/yume-chan/ya-webadb)（MIT），從 esm.sh 載入。
四個 API 都對過型別定義：`PackageManager(adb)`、`installStream(size, stream)`、
`subprocess.noneProtocol.spawnWaitText(cmd)`、
`AdbDaemonTransport.authenticate({serial, connection, credentialStore})`。

**已知的兩個坑（都已修）**：

1. `AdbWebCredentialStore` 是 **default export**，不是具名匯出。
2. **函式庫必須在頁面載入時就抓**，不能等按鈕按下才抓——Chrome 的
   `requestDevice()` 授權期只有約 5 秒，載模組要更久。

### 裝過舊版的人會撞到簽章衝突

實測確認：`INSTALL_FAILED_UPDATE_INCOMPATIBLE`。安裝器已經會偵測、
翻成人話、問過使用者之後自動移除舊版再裝。

### SideQuest

文案與橫幅都備好在 `docs/sidequest-submission.md`。還缺：截圖、建立 listing。

**SideQuest 的 GitHub 整合是 webhook 不是 OAuth**：先建 listing → 從 app manager
複製 `https://sdq.st/release-webhook/<TOKEN>` → 加到 repo webhook（只勾 Releases）。
repo 上目前**沒有**這個 webhook。

### 發版規矩

**每次發新版一定要把 `versionCode` 加一。** Android 用它判斷新舊，
`versionName` 只是給人看的。忘了加，SideQuest 與 App 自我更新都會失效。
`versionName` 要跟 release tag 對得起來（0.2.0 ↔ v0.2.0）。

---

## 7. 程式結構

```
app/src/main/java/tw/pinnedbopomofo/quest/
├─ ZhuyinImeService.kt   輸入法本體：版面、按鍵、候選列、分頁選單、特效接線
├─ MainActivity.kt       設定畫面。**唯一會用到網路的地方**：下載模型、檢查更新
├─ KeyboardTheme.kt      十一套配色、對比度計算（WCAG）
├─ KeycapArt.kt          程式繪製的鍵帽（磚塊、霓虹、竹簡、青玉、平面）
├─ KeyboardSizing.kt     面板尺寸計算
├─ engine/               選字引擎：詞網格、詞庫、近音修正
├─ voice/                語音：錄音、能量式 VAD、Paraformer、簡繁轉換
├─ net/                  下載與更新：Asset、Downloader、Version、Updater
└─ effects/              特效。**模擬本體與繪製層分開**，模擬的部分可單元測試

app/src/main/assets/                注音詞庫 4.7 MB（已從 pime 搬進來）
app/src/main/res/drawable-nodpi/    青玉配色的四張美術圖
docs/index.html                     網頁安裝器
tools/deploy.ps1                    電腦端一鍵部署
tools/fetch-deps.ps1                下載 AAR
tools/make-banner.py                產生上架橫幅
```

### 配色模型有個陷阱

配色有 `text`（鍵帽字）、`panelText`（面板上的候選字）、`panelAccent`（組字中的字）。
**面板與鍵帽明暗相反的配色**（竹簡、青玉、素白）一定要分別指定，
否則會出現「白字配白底」。三套配色都因此出過事，現在有對比度測試擋著。

---

## 8. 測試（141 支，全部通過）

```powershell
gradle testDebugUnitTest --rerun-tasks --console=plain
```

**本專案的鐵則：新測試要先證明它有能力變紅。**
故意破壞對應的程式碼 → 確認測試失敗 → 還原 → 比對 SHA-256。
這個對話裡每一支新測試都做過，而且抓到不少真問題：

- 對比度測試**不用破壞程式**就抓到三套既有配色的隱形文字（1.05、1.54、1.63）
- 九宮格測試抓到「中間帶被擠成零」會讓玉板長出原圖沒有的尖角
- 版本比較測試抓到字串比較會讓 `v0.10` 被當成比 `v0.9` 舊
- 清單同步測試綁住 App 與 `deploy.ps1` 的模型 SHA256，只改一邊會紅
- 山形測試抓到**我自己寫錯**：頻率太低，每排只長一個峰，是小丘不是遠山

---

## 9. 已知問題與技術債

**網頁安裝器的診斷紀錄（2026-09-17 深夜～2026-09-18 凌晨，一次對話裡）**

現象：`docs/index.html` 按下安裝、選裝置、`device.connect()` 成功（有「USB 已開啟」），
接著 `AdbDaemonTransport.authenticate()` 卡住，20 秒逾時，全程**沒有任何 USB 斷線事件**。

已經排除、不是原因的：
- ~~函式庫太晚載入、錯過授權期~~（已修，見 git log 裡「fix: 網頁安裝器按下去就失敗」那次 commit）
- ~~程式碼寄出的封包格式錯~~——加了封包 log 後證實：我們的 code 在收到連線後 **12ms 內**送出
  格式完全正確的 CNXN 封包（`command:1314410051` 就是 ADB 協定的 `CNXN`，版本號、payload 都對）
- ~~頭盔睡著斷線~~——加了 USB connect/disconnect 事件監控後，證實失敗全程頭盔都沒有斷線
- ~~本機背景 `adb server` 搶介面~~——診斷時期一度是真的（見 §4 環境地雷），但清掉之後問題依舊
- ~~Consumable 用法錯/ getter-only 屬性寫壞~~——這兩個是除錯過程中**自己**踩的次要 bug，
  已在 git log 裡修掉（`封包除錯功能本身把安裝流程弄壞了` 那次 commit）

目前唯一還沒排除、最可疑的方向：**Chrome 對這顆頭盔 USB 介面的獨占聲明**。
實測 `adb.exe devices` 在 Chrome 完全關閉時能連得上、能 `adb install`；
但只要 Chrome 開著試過一次網頁安裝器（不管成功失敗），`adb.exe` 就看不到裝置了，
連呼叫 `device.raw.close()` 都救不回來，只有整個關掉 Chrome 才會放。
這暗示 Chrome 內部可能還握著某個底層 USB handle 沒放（也可能是 Windows WinUSB 驅動層的行為），
跟 `docs/index.html` 的 JS 邏輯本身關係較小。下次要查：
1. 換一個全新的 Chrome 使用者設定檔（或換 Edge）測試，排除這台電腦上 Chrome 設定/擴充功能的因素。
2. 查 `chrome://device-log` 或 `chrome://usb-internals` 在卡住的當下有沒有更細的錯誤。
3. 如果懷疑是驅動，考慮用 Zadig 重新綁一次這個 ADB Interface 的 WinUSB 驅動看看有沒有差。
4. 不要再靠一直改 `docs/index.html` 加 log 猜——先用 `adb.exe` + `Get-PnpDevice` 把頭盔本身的狀態
   確認清楚，兩邊測試之間切換時必須先把 Chrome 完全關掉。

**擋發布的**

- ~~網頁安裝器的 USB 流程從未成功跑過一次~~ → 已經不擋發布：2026-09-18 改用 `adb install` 直接把
  App 裝上頭盔成功，App 本身可以用了。網頁安裝器本身還是沒修好（見上面診斷紀錄），但已經降級成
  「加分項」，不是發布必要條件。
- SideQuest 還沒建 listing、沒有截圖（App 已裝好，現在可以拍了）

**功能缺口**

- 語音辨識的英文專有名詞會被拆壞（Paraformer 不支援 hotwords，無解）
- 聯想詞不會學習；沒有游標移動與句中修改
- 載入模型後記憶體約 382 MB，**閒置時不會釋放**
- 詞庫是 JSON，冷啟動約 6.75 秒。二進位快取可以壓到 ~1 秒，**還沒做**

**授權未定論**

- 詞庫上游 libchewing（LGPL-2.1）與 rime-essay（LGPL-3.0）都是**函式庫型 copyleft**，
  不強制 App 程式碼開源。但「重新統計的詞頻表算不算衍生著作」沒有定論。
  免費公開風險低；**要收費前應取得專業意見**。
- Paraformer 模型的上游授權鏈仍未完全確認。當初講好的退路是改用 SenseVoiceSmall。
- 詳見 `docs/licenses/third-party.md`。

---

## 10. 跟使用者合作的注意事項

- **他要看得到的東西，不要只有描述。** 這個對話裡他說過「給我預覽圖阿 不要每次都不給」。
  美術相關的改動一律先給他看再上機。我用 PIL 寫了合成腳本（`tools/make-banner.py`
  的 nine_slice 可以重用），用真實素材與真實尺寸合成，比口頭描述有用得多。
- **他會直接說難聽的話**（「這也太醜了」「非常丑」）。那是省時間，不是情緒。
- **不要宣稱沒驗證過的事。** 我在這個對話裡犯過一次大的：README 寫「第一次會自動
  下載語音模型」，但 App 根本沒有 INTERNET 權限，整個功能不存在。那份 README
  已經公開發佈出去了才發現。
- **量尺要先驗證。** 有一次我掃描「哪些 App 有安裝權限」得到 0，差點當成結論——
  用自己的 App 當對照組才發現裝置早就離線了，掃描本身是壞的。
- **他不喜歡一次問太多問題**，但授權、公開性這類不可逆的決定一定要問。

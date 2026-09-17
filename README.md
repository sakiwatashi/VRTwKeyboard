# VRTwKeyboard — Quest 注音輸入法

給 Meta Quest 的臺灣注音輸入法。用 Android `InputMethodService` 寫成，介面全部由程式繪製，
在 Horizon OS 上以射線或手勢操作。

**狀態：可用的原型。**每天拿來打字沒問題，但還沒上架、也還沒做完整的授權稽核（見下方）。

## 功能

- **大千（標準）注音鍵位**，四排注音加功能列，右側固定放倒退、Enter、切換、收起
- **整句詞網格選字**：不要求整句預先存在於字典，會比較所有切分方式與現代詞頻
- **近音修正**：「層數較高」不會逐字變成「曾恕叫高」
- **離線語音輸入**：mic → VAD → 串流 Paraformer → OpenCC → 臺灣正體，全程不連網
- **九套配色**，含用美術圖做的「青玉」（背景板、鍵帽、候選條、山水空白鍵）
- **八種特效**可單獨開關：輝光、粒子、飛字、漣漪、傾斜、流光、聲波、煙霧
- 表情符號、全半形標點、英數頁

## 建置

需要 JDK 21+、Android SDK（compileSdk 37）。

```powershell
# 1. 抓不進版控的相依檔（sherpa-onnx AAR，50 MB，含 SHA256 驗證）
.\tools\fetch-deps.ps1

# 2. 建置與測試
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
gradle testDebugUnitTest assembleDebug --console=plain
```

沒有 `gradlew` 腳本，用系統裝的 Gradle 9.6+。

### 裝到頭盔

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Horizon OS 的「輸入法設定」被 vrshell 攔走，要指名開原生設定頁：

```powershell
adb shell am start -n com.android.settings/.Settings`$AvailableVirtualKeyboardActivity
```

### 語音模型

第一次使用語音功能時另外下載（約 226 MB），不打包進 APK。
來源、revision 與 SHA256 記在 [`docs/licenses/asr-paraformer.md`](docs/licenses/asr-paraformer.md)。

## 測試

116 支單元測試，涵蓋選字引擎、版面尺寸、對比度、語音斷句、特效模擬與九宮格切圖。

```powershell
gradle testDebugUnitTest --rerun-tasks --console=plain
```

本專案的慣例是**新測試要先證明它有能力變紅**：故意破壞對應的程式碼、確認測試失敗、
還原後比對 SHA-256。不會失敗的測試等於沒有測試。

## 詞庫

`app/src/main/assets/` 的注音詞庫來自
[smart-priority-bopomofo](https://github.com/sakiwatashi/keyinput123)（MIT），
上游轉製自 libchewing、McBopomofo 與 Rime Essay 的資料。

> **注意**：libchewing 是 LGPL-2.1+、Rime 本體是 GPL-3.0。
> 這些上游授權對衍生資料的效力**尚未釐清**，商業散布前必須確認。
> 詳見 [`docs/licenses/third-party.md`](docs/licenses/third-party.md)。

## 授權

第三方元件的盤點見 [`docs/licenses/third-party.md`](docs/licenses/third-party.md)。
本專案本身的授權條款**尚未決定**。

## 已知限制

- 輸入法面板尺寸由系統決定，而且**會變**：量過 780×355，頭盔重開機後同一個系統版本
  回報過 3664×1920。程式會自己把關，但相關數值不要當成固定值
- Horizon OS 沒有跨視窗模糊，毛玻璃效果只能用半透明模擬
- 英文專有名詞的辨識不佳（Paraformer 不支援 hotwords）
- 聯想詞不會學習；沒有游標移動與句中修改
- 只有 debug 簽章，沒有 release 設定

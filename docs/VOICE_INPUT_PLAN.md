# 語音輸入計畫（離線、在頭盔上辨識）

> 2026-09-16 寫。模型與函式庫資訊都是當天搜尋查到的，會變動；動手下載前再查一次版本。
> **所有下載（函式庫、模型、OpenCC）都要先經使用者同意**（交接文件 §13、§14 的規則）。

## 目標與原則
- 按 🎤 說話，放開或說完自動停，轉成**台灣正體中文**送進輸入框。
- **完全離線**：App 沒有網路權限，也不打算加。聲音不存檔、不上傳、不寫進記錄（記錄只有時長與狀態）。
- Quest 上系統沒有語音辨識服務（`SpeechRecognizer.isRecognitionAvailable=false`，交接文件 §5），所以要自己帶引擎。

## 引擎：sherpa-onnx
- k2-fsa 的 sherpa-onnx：onnxruntime 包好的離線語音工具，有 Android AAR 與 Kotlin API（`OfflineRecognizer`、`OfflineModelConfig.senseVoice / qwen3Asr / funasrNano`）。
- 最新版 **v1.13.8（2026-09-10）**，Android 資產 `sherpa-onnx-1.13.8.aar` **47.8 MB**（GitHub Releases，官方）。
  Maven Central 上的 `com.bihe0832.android:lib-sherpa-onnx` 是**第三方**重新打包，不用。
- 1.13.7／1.13.8 修過 Qwen3-ASR、FunASR-Nano「安靜時幻聽出文字」的問題 → 版本不要低於 1.13.8。

## 模型候選：小而快的那一類（都輸出簡體，要轉正體；台灣原生繁體的模型見最後一節）
| 模型 | 大小 | 串流 | 標點 | 授權 | 備註 |
|---|---|---|---|---|---|
| **SenseVoice int8 2024-07-17**（首選先試） | model.int8.onnx 228 MB | 否（非自回歸，一段一段辨識很快） | 有（use_itn） | FunASR Model License v1.1，可商用需標註 | 中英日韓粵 |
| SenseVoice int8 2025-09-09 | 226 MB | 否 | **沒有** | 同上 | 粵語加強；台灣國語用不到 |
| **Qwen3-ASR 0.6B int8 2026-03-25**（準確度候選） | 待查 | 否 | 有 | Apache-2.0 | 52 種語言與方言（含閩南語）；LLM 解碼器、較慢 |
| FunASR-Nano int8 2025-12-30 | 948 MB | 否 | 有 | 待查 | 太大；RTF 約 0.15–0.19（官方數字，非頭盔） |
| 串流 zipformer small CTC zh int8 2025-04-01 | 25 MB | **是** | 沒有 | 待查 | 很小、可以邊講邊出字，但準確度較低 |

**建議順序**：先用 SenseVoice 2024-07-17 做完整條流程（快、有標點、授權清楚），在頭盔上量延遲與準確度；再用同一個介面換 Qwen3-ASR 0.6B 比較。

## 簡轉繁：OpenCC s2twp
- 模型輸出簡體，要用 OpenCC 的 **s2twp**（簡體 → 台灣正體，含台灣慣用詞，例如「軟件→軟體」）。
- 候選：`android-opencc`（qichuan，JNI 包原版 OpenCC，JitPack）、`opencc4j`（houbb，純 Java，Maven Central）、`OpenCC-Java`（yichen0831）。要比較大小與是否維護中，**下載前要同意**。
- 之後可以把轉完的結果再過我們自己的台灣詞表（`taiwan_preferred.json`）修正。

## 架構（2026-09-16 已實作的部分標 ✅）
```
🎤 按鍵 ──► AudioCapture（AudioRecord 16 kHz 單聲道，每框 20 ms，錄音執行緒）
                │ 音框
                ▼
          VoiceSession ✅ ── Endpointer ✅（只看音量：校正噪音底、開口、說完、上限）
                │ 說完的一段聲音（含開口前 300 ms）
                ▼
          SpeechEngine ✅ 介面 ──► SherpaSenseVoiceEngine（待下載 AAR 與模型後實作）
                │ 簡體文字
                ▼
          OpenCC s2twp（待下載） ──► commitText 進輸入框
```
- `VoiceSession`：錄音執行緒送音框、主執行緒按停止或取消；辨識在背景執行緒；狀態 `Listening / Recognizing / Done / Heard / NoSpeech / Failed` 送回主執行緒；取消後不再回呼。
- `VoiceModels` ✅：模型不打包進 APK，放在 `/sdcard/Android/data/tw.pinnedbopomofo.quest/files/asr/sense-voice/`（`model.int8.onnx`、`tokens.txt`），檔案齊全才算安裝。開發時用 `adb push` 放進去。
- **沒安裝模型時**也能用：會錄音、判斷說話，顯示「聽到約 X 秒」。這樣不用任何下載就能先在頭盔上驗證最大的風險（下面第 1 點）。

## 風險（依影響排序）
1. **Horizon OS 上輸入法能不能用麥克風**：Android 14 對背景錄音有限制；Gboard 這類鍵盤可以語音輸入，但 Quest 沒實測過。→ 階段 A 先驗證。
2. **記憶體**：SenseVoice 228 MB 模型＋onnxruntime 載入輸入法程序，加上詞庫（目前 PSS 約 120–140 MB）。可能要「按 🎤 才載入、閒置一段時間釋放」。
3. **延遲**：非串流模型要說完才出字；目標「說完到出字 < 1 秒」（5 秒以內的句子）。要在頭盔上量。
4. **準確度**：台灣口音、中英夾雜。SenseVoice 與 Qwen3-ASR 都要用同一組句子實測比較。
5. **APK 與儲存**：AAR 47.8 MB 會讓 APK 從 6 MB 變 50 MB 以上（可只留 arm64-v8a 減少）；模型另外放。

## 階段
- **A. 麥克風與說話偵測（不需下載，已實作，等頭盔驗證）**
  驗收：在頭盔上按 🎤 說一句話，鍵盤顯示聆聽中與音量條，說完自動停，顯示「聽到約 X 秒」；安靜不說話 6 秒後顯示沒聽到；收起鍵盤會停止錄音。
- **B. 接上 sherpa-onnx＋SenseVoice（需同意下載 AAR 47.8 MB、模型約 228 MB＋壓縮檔）**
  驗收：說「今天天氣很好」轉出文字；量「說完到出字」時間、記憶體；辨識不卡鍵盤（在背景執行緒）。
- **C. 簡轉繁（需同意下載 OpenCC 函式庫）**
  驗收：輸出為台灣正體與台灣用詞；加單元測試。
- **D. 比較 Qwen3-ASR 0.6B**：同一組 20 句台灣日常句子，比較字錯率、延遲、記憶體，決定預設模型。
- **E. 體驗**：模型安裝引導（App 裡顯示放檔路徑與檢查結果）、閒置釋放模型、授權聲明頁（FunASR Model License／Apache-2.0／sherpa-onnx Apache-2.0）。

## 需要使用者同意的下載清單（階段 B、C）
| 檔案 | 來源 | 大小 |
|---|---|---|
| `sherpa-onnx-1.13.8.aar` | https://github.com/k2-fsa/sherpa-onnx/releases （v1.13.8） | 47.8 MB |
| `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2` | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/ | 壓縮檔大小待查；解開後 model.int8.onnx 228 MB（另含 float32 894 MB，可不解出） |
| OpenCC 函式庫（三選一） | JitPack／Maven Central | 待比較 |
| （階段 D）`sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25` | sherpa-onnx 文件指向 ModelScope／GitHub | 待查 |

## 來源
- sherpa-onnx SenseVoice 預訓練模型：https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- sherpa-onnx CHANGELOG：https://github.com/k2-fsa/sherpa-onnx/blob/master/CHANGELOG.md
- sherpa-onnx Releases：https://github.com/k2-fsa/sherpa-onnx/releases
- sherpa-onnx Kotlin OfflineRecognizer：https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt
- sherpa-onnx Qwen3-ASR：https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/index.html
- sherpa-onnx FunASR-Nano：https://k2-fsa.github.io/sherpa/onnx/funasr-nano/pretrained.html
- 串流 zipformer CTC 模型：https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/zipformer-ctc-models.html
- Qwen3-ASR（Apache-2.0）：https://github.com/QwenLM/Qwen3-ASR 、https://qwen.ai/blog?id=qwen3asr
- SenseVoice 授權討論：https://github.com/FunAudioLLM/SenseVoice/issues/279 、https://huggingface.co/FunAudioLLM/SenseVoiceSmall
- OpenCC s2twp：https://github.com/BYVoid/OpenCC/blob/master/data/config/s2twp.json 、https://github.com/qichuan/android-opencc 、https://github.com/houbb/opencc4j
- Android 14 麥克風前景服務限制：https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- 注意：比較文章（Northflank、AssemblyAI、Gladia 等）多為廠商自家部落格，排名有偏頗，只當線索，不當結論。

## 台灣自己的模型（2026-09-16 補；使用者問「為什麼要簡轉繁」）
**台灣有開源的語音辨識模型，而且直接輸出繁體**，前面選 SenseVoice／Qwen3-ASR 是「為了小而快」的取捨，不是因為沒有台灣的模型。

| 模型 | 來源 | 基底／大小 | 語言 | 授權 | 輸出 |
|---|---|---|---|---|---|
| **Breeze-ASR-25**（2025-07） | 聯發創新基地 MediaTek Research | Whisper-large-v2 微調，HF 標 2B（large-v2 本體 15.5 億參數） | 台灣華語＋中英夾雜（句內、句間） | Apache-2.0 | **繁體（台灣用語）** |
| **Breeze-ASR-26**（2026，Breeze 3 系列） | 同上 | Whisper-large-v2 微調，2B，1 萬小時台語資料 | **台語**（＋華語、英語夾雜） | Apache-2.0 | 繁體 |
| Whisper-Taiwanese-model-v0.5 | 臺南大學 NUTN-KWS | whisper-large-v3-turbo 微調 | 待確認（可能偏台語） | 待查 | 待確認 |

**為什麼目前的計畫還是先用簡體模型＋OpenCC**
- 這三個都是 Whisper large 等級（0.8B–2B）。sherpa-onnx 有 `scripts/whisper/export-onnx.py`，**可以匯出微調過的 Whisper 並 int8 量化**（社群已有第三方微調模型的 int8 版本），所以技術上跑得動。
- 但這是**編碼器–解碼器、逐字產生**的架構，在手機／頭盔 CPU 上很慢：whisper.cpp 在 Snapdragon 888 上 small 約 1.2 倍實時、base 約 3.5 倍實時，large 級別遠低於實時；int8 large 峰值記憶體約 1.5 GB（第三方實測，**不是 Quest 實測**）。
- 鍵盤要的是「說完 1 秒內出字」。SenseVoice 是非自回歸、228 MB，量級差很多。
- 代價就是簡體輸出要轉繁：一對多（干／幹／乾、著／着、里／裡）與台灣用語（軟件→軟體）靠 s2twp 詞表處理，**一定會有轉錯的情況**；Breeze 這類模型則是原生繁體，中英夾雜也更準。

**結論：不要只憑推測跳過台灣模型。** 階段 D 的比較改成三方，並先量再決定：
1. SenseVoice int8（228 MB）＋ OpenCC s2twp
2. Qwen3-ASR 0.6B int8
3. **Breeze-ASR-25 int8**（需下載；先估算 encoder/decoder int8 檔案大小再問使用者）

量的是同一組台灣日常句子（含中英夾雜），比較字錯率、說完到出字的時間、記憶體。如果 Breeze 在頭盔上「慢但可接受」（例如 2 秒內），繁體原生的品質優勢可能值得；太慢就退回小模型＋轉換。台語辨識（Breeze-ASR-26）另外當一個獨立功能評估，不放進第一版。

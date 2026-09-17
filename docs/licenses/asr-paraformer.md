# 語音辨識元件：來源、版本與授權紀錄

> 2026-09-16 建立。**這不是法律意見。**正式商用上架前要再做一次完整 license audit（使用者要求）。
> 模型不進 git、不打包進 APK：照這份紀錄重新下載。

## 採用的元件

| 元件 | 版本／revision | 授權 | 來源 |
|---|---|---|---|
| sherpa-onnx（Android AAR） | v1.13.8 | Apache-2.0 | https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar |
| ONNX Runtime（含在 AAR 內） | 隨 sherpa-onnx | MIT | https://github.com/microsoft/onnxruntime |
| Streaming Paraformer 中英雙語 INT8 | HF revision `8e40c43232a1c5c66c82111efc5820d3accca11b`（2023-08-14） | apache-2.0（倉庫標籤） | https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en |
| Silero VAD INT8 | sherpa-onnx asr-models 發行檔 | MIT | https://github.com/snakers4/silero-vad |
| opencc4j（簡→台灣繁） | 待定版本 | Apache-2.0 | https://github.com/houbb/opencc4j |

### 檔案（SHA256 下載後補）
```
sherpa-onnx-1.13.8.aar   50,129,134 bytes  sha256=633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
encoder.int8.onnx       165,462,184 bytes  sha256=81a70226a8934e6ed92aa1d4fc486b428b5398e2f2619ed4897b7294cab90e9a
decoder.int8.onnx        71,664,561 bytes  sha256=f3cca9f77bb9d93c8fcbfb63ae617b6b1ee96818df3aa3b151c40658fe38594f
tokens.txt                   75,756 bytes  sha256=59aba8873a2ed1e122c25fee421e25f283b63290efbde85c1f01a853d83cb6e6
silero_vad.int8.onnx        212,860 bytes  sha256=c36d490aff5ab924ca6c7aeec4d8f6bd3d22db6fa17611b9c5b17eae58ac3a20

下載時間 2026-09-16 13:07–13:19（約 12 分鐘，GitHub 發行檔從台灣約 65 KB/s）。
```
模型三檔合計約 226 MiB。

## Paraformer 的授權鏈（2026-09-16 查證）
1. **上游**：ModelScope `iic/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online`
   與 `damo/speech_paraformer_asr_nat-zh-cn-16k-common-vocab8404-online`，
   ModelScope API 回報 `License: Apache License 2.0`。訓練資料是阿里巴巴自有的產業標註語料。
2. **轉檔**：Hugging Face `csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en`，
   倉庫授權標籤 `apache-2.0`，README 明寫轉自上述 ModelScope 模型（快照見 `paraformer-hf-readme.snapshot.md`）。
3. **尚未確認**：FunASR 體系對「特定 revision 權重的散布與商用許可」仍有公開 clarification issue 未獲維護者完整回覆。
   因此**現階段不標記為「授權完全確認」**。若最後無法確認，改用已有官方商用澄清的 SenseVoiceSmall（FunASR Model License v1.1，可商用須標註）。

## 明確排除的選項與理由
| 選項 | 理由 | 查證狀態 |
|---|---|---|
| Zipformer zh-14M INT8（25 MB） | 官方文件寫「trained on the WenetSpeech corpus」，WenetSpeech 條款限非商業研究與教育；轉檔倉庫雖標 apache-2.0，但沒有 LICENSE 檔也沒說明資料來源，授權鏈是斷的。另外它**只支援中文**，不符合中英混講需求 | 本地查證（sherpa 官方文件原文＋HF 倉庫） |
| Streaming Zipformer small bilingual zh-en（47.5 MB） | 官方文件只寫「trained on tens of thousands hours of some internal dataset」，來源不明、無授權聲明 | 本地查證 |
| Piper（TTS） | 依賴 GPL 的 eSpeak-NG；`zh_CN-huayan` 模型卡標 Dataset License: Unknown | 使用者提供的分析，**本地尚未查證** |
| AISHELL-3（TTS） | HF 標 Apache-2.0，AISHELL 官方網站寫僅供研究、禁止商用，互相矛盾 | 使用者提供的分析，**本地尚未查證** |

## 旁證：這類模型的授權普遍沒交代清楚
sherpa-onnx 倉庫 2026-09 有兩個 issue 在問模型授權，維護者都沒有回覆：
- https://github.com/k2-fsa/sherpa-onnx/issues/3915 （問「能否隨免費 Android App 散布、能否商用」）
- https://github.com/k2-fsa/sherpa-onnx/issues/3914 （問俄語模型沒有授權聲明）

所以**不能只看 Hugging Face 上那一行 license 標籤**，要追到上游資料集。

## 上架前要做的事
- App 內加一頁「第三方授權聲明」，列出 Apache-2.0／MIT 的 copyright、授權全文與 NOTICE。
- 重新確認 Paraformer 權重散布條款是否已獲官方澄清。
- 重新檢查當時採用的每一個模型版本（revision 可能已變動）。

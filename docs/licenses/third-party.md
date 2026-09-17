# 第三方元件授權盤點

> 2026-09-17 為「公開發佈到 GitHub」做的盤點。**這不是法律意見。**
> 使用者的目標是「未來可能閉源、收費上架」，所以這份特別標出**會影響閉源的項目**。
>
> 語音模型的細節另見 [asr-paraformer.md](asr-paraformer.md)。

## 已確認、對閉源沒有問題

| 元件 | 版本 | 授權 | 進不進版控 |
|---|---|---|---|
| sherpa-onnx（Android AAR） | v1.13.8 | Apache-2.0 | 否（`.gitignore` 排除，50 MB） |
| ONNX Runtime（含在 AAR 內） | 隨 sherpa-onnx | MIT | 否 |
| Silero VAD INT8 | sherpa-onnx asr-models 發行檔 | MIT | 否（首次啟用才下載） |
| opencc4j | 1.14.0 | Apache-2.0 | 否（Maven 相依） |

Apache-2.0 與 MIT 都允許閉源散布，只要保留授權聲明。上架前要附一份 NOTICE。

## 需要決定或釐清

### 1. 注音詞庫（**會擋住 clone 之後的建置**）

`app/build.gradle.kts` 把 assets 指向 repo 外面：

```kotlin
rootDir.resolve("../pime-bopomofo-core/bopomofo_core/data")
```

那個目錄有 157 個檔、4.7 MB，**不在這個 repo 裡，也沒有被 git 追蹤**。
任何人 clone `VRTwKeyboard` 之後都建置不起來。

`pime-bopomofo-core` 本身是 **MIT**，所以搬進來沒問題。但它的 README 寫著詞庫
「參考 libchewing、McBopomofo、Rime Essay 轉製」，而那三個的授權不一樣：

| 上游 | 授權 | 對閉源的影響 |
|---|---|---|
| McBopomofo | MIT | 沒問題 |
| libchewing | **LGPL-2.1+** | 資料檔是否受 copyleft 影響要確認 |
| Rime `essay.txt` | **需確認**（Rime 本體是 GPL-3.0） | 同上 |

**這是整份盤點裡風險最高的一項**，而且跟收費上架直接相關。

### 2. 語音模型 Paraformer

HF 倉庫標 `apache-2.0`，但上游 ModelScope 的授權鏈當時查不到明確結論——
這正是當初講好的「若最終無法確認，fallback 改用已有官方商用澄清的 SenseVoiceSmall」。
模型不進版控、首次啟用才下載，所以不擋發佈，但擋上架。

### 3. 美術資源（使用者已確認）

`app/src/main/res/drawable-nodpi/` 的四張青玉素材由使用者以 GPT 生成，
使用者於 2026-09-17 表示授權已確認。這裡只記錄事實，不重複判斷。

| 檔案 | 用途 | 原始尺寸 |
|---|---|---|
| `jade_panel.png` | 面板背景板 | 1869×841 |
| `jade_key.png` | 鍵帽 | 1254×1254 |
| `jade_candidates.png` | 候選字列 | 2172×724 |
| `jade_space.png` | 空白鍵山水玉板 | 2172×724 |

### 4. 本專案本身還沒有 LICENSE

沒有授權檔的公開 repo，法律上是「保留所有權利」：別人不能合法使用或修改，
但**程式碼仍然已經公開**。已經推上去的 commit 就算之後刪掉，別人也可能已經 fork。

「先公開、之後閉源收費」做得到（自己的程式碼可以換授權），
但**已公開的那些版本收不回來**。

## 上架前還要做的

- [ ] 確認詞庫資料的上游授權鏈（libchewing / Rime）
- [ ] Paraformer 授權定案，或改用 SenseVoiceSmall
- [ ] 產生 NOTICE：列出 Apache-2.0 與 MIT 元件的聲明
- [ ] 決定本專案的授權

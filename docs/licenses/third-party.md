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

| 上游 | 授權 | 查證來源 |
|---|---|---|
| McBopomofo | MIT | 倉庫 README「本專案採用 MIT License 釋出」 |
| libchewing | **LGPL-2.1** | 倉庫 `COPYING`（2026-09-17 讀取） |
| Rime `essay.txt` | **LGPL-3.0** | `rime/rime-essay` 倉庫授權標示 |

### 2026-09-17 查證結論

兩個 copyleft 上游**都是 LGPL，不是 GPL**。（先前這份文件寫「Rime 本體是 GPL-3.0」是錯的，
Rime 有多個倉庫用不同授權，`rime-essay` 本身是 LGPL-3.0。）

這個差別很重要：LGPL 是「函式庫型」copyleft，設計上就是讓**使用者的程式可以不開源**。
套到資料檔上的實務結果是：

- **詞庫資料本身**要維持 LGPL、要能拿到、要附授權聲明
- **App 的程式碼不受影響**，可以用任何授權，包括未來閉源收費

詞庫是純 JSON、直接放在 `app/src/main/assets/` 且隨 repo 公開，
「提供對應原始碼」這個義務自然滿足。

**但有一項仍未定論**：從字典檔重新統計出來的詞頻表，在法律上算不算原字典的「衍生著作」，
這件事本身就有爭議。上游 `pime-bopomofo-core` 標的是 MIT，
但若資料確實是 LGPL 素材的衍生物，作者無法單方面改成 MIT。

**這不是法律意見。**若之後真的要收費上架，這一項值得找專業意見；
維持免費公開則風險很低。

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

### 4. 本專案的授權：Apache-2.0（2026-09-17 定案）

選 Apache-2.0 的理由：跟 LGPL 的詞庫相容、含專利授權條款、對公開專案最通用。

要留著的認知：**已經推上去的 commit 收不回來**。之後的新版本可以改成別的授權
（自己的程式碼本來就可以換），但已公開的那些版本別人可以永久 fork 使用。

## 上架前還要做的

- [x] ~~確認詞庫資料的上游授權鏈~~ → 2026-09-17 查證：libchewing LGPL-2.1、rime-essay LGPL-3.0、
      McBopomofo MIT。都是 LGPL，不強制 App 程式碼開源
- [ ] 若要收費上架：確認「重新統計的詞頻表算不算衍生著作」
- [ ] Paraformer 授權定案，或改用 SenseVoiceSmall
- [x] ~~產生 NOTICE~~ → 見 `NOTICE`
- [x] ~~決定本專案的授權~~ → Apache-2.0

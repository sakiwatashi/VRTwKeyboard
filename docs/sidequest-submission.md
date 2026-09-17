# SideQuest 上架資料

送審在 <https://sidequestvr.com>（登入後 → Dashboard → Submit an App）。
**表單要你自己送**，這份是準備好可以直接貼的內容。

SideQuest 的必填只有三項：App 名稱、套件名、簡介。其餘都是加分項，
但截圖與橫幅會直接影響有沒有人願意點進來。

---

## 必填

**App Name**

```
VRTwKeyboard — 臺灣注音輸入法
```

**Package Name**

```
tw.pinnedbopomofo.quest
```

**Summary**（一行簡介）

```
給 Quest 的臺灣注音輸入法：大千鍵位、整句選字、離線語音輸入。
```

英文版（SideQuest 的使用者以英文為主，建議兩種都填）：

```
Traditional Chinese (Bopomofo) keyboard for Quest, with offline voice input.
```

---

## 描述

```
VRTwKeyboard 是給 Meta Quest 的臺灣注音輸入法。

Quest 內建的鍵盤不支援注音，要打中文只能一個字一個字慢慢找。
這個輸入法用標準的大千鍵位，跟 Windows 微軟注音同一套排法，
上手不需要重新學。

■ 整句選字
不是逐字選，而是整句一起比對。打「ㄇㄟˇ ㄩˋ ㄉㄠˋ ㄧ ㄐㄩˋ ㄒㄧㄣ ㄐㄩˋ ㄗ˙」
會得到「每遇到一句新句子」，不會變成「美譽心」。
打錯近音也會自動修：「層數較高」不會變成「曾恕叫高」。

■ 離線語音輸入
按麥克風直接說話，停頓一秒自動送出。
辨識完全在頭盔上跑，聲音不會離開你的裝置。
輸出是臺灣正體。

■ 十一套配色、八種特效
從乾淨的淺色系統風到用美術圖做的青玉山水都有。
特效可以單獨開關：輝光、粒子、飛字、漣漪、傾斜、流光、聲波、煙霧。

■ 開源
Apache-2.0。原始碼、詞庫、建置方式全部公開：
https://github.com/sakiwatashi/VRTwKeyboard

---

安裝後的設定（三步）：
1. 打開「注音輸入法」App
2. 按「開啟鍵盤設定」，把注音輸入法打開
3. 要用語音就按「允許使用麥克風」，再按「下載語音模型」（226 MB，在頭盔內下載）

已知限制：
- 語音辨識的英文專有名詞會被拆壞
- 聯想詞不會學習，也還沒有游標移動與句中修改
```

英文描述：

```
A Traditional Chinese (Bopomofo/Zhuyin) input method for Meta Quest.

Quest's built-in keyboard has no Bopomofo support, so typing Chinese means
hunting for characters one at a time. This IME uses the standard Dachen
layout — the same one Microsoft Bopomofo uses on Windows.

■ Whole-sentence conversion
Not character-by-character. The engine compares every possible segmentation
against modern word frequencies, so a full sentence comes out right even
when it isn't in the dictionary as a phrase.

■ Offline voice input
Press the microphone and speak. Recognition runs entirely on the headset —
audio never leaves your device. Output is Traditional Chinese (Taiwan).

■ 11 themes, 8 toggleable effects
From a clean light system look to a hand-illustrated jade landscape.
Effects: bloom, particles, flying glyphs, ripple, tilt, sheen, voice
waveform, mist.

■ Open source, Apache-2.0
https://github.com/sakiwatashi/VRTwKeyboard

Setup after install (3 steps):
1. Open the "注音輸入法" app
2. Tap "開啟鍵盤設定" and enable the keyboard
3. For voice: tap "允許使用麥克風", then "下載語音模型" (226 MB, downloads
   in-headset)

Known limitations: English proper nouns are mangled by the speech model;
no learned predictions; no mid-sentence cursor editing yet.
```

---

## 其他欄位

| 欄位 | 填什麼 | 為什麼 |
|---|---|---|
| Supported devices | Quest 2 / Quest 3 / Quest 3S / Quest Pro | 只在 Quest 3S 實測過，但沒有任何裝置專屬的程式碼 |
| Comfort level | **Comfortable** | 2D 面板，沒有移動、沒有高度變化 |
| License | **Apache-2.0**（或 Open Source） | 跟 repo 一致 |
| Category | Tools / Utilities | 不是遊戲 |
| Price | Free | |
| YouTube / Vimeo | 先留空 | 還沒有影片 |
| Website | https://github.com/sakiwatashi/VRTwKeyboard | |

---

## 素材

### 截圖（3–6 張）

**還沒做。**截圖必須是真的，不能用渲染圖充數。要在頭盔上抓：

```powershell
# 在頭盔裡把鍵盤叫出來，然後
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png
```

建議的六張：

1. 注音鍵盤打字中，候選字列有內容（**青玉**配色）
2. 同上，**素白**配色——讓人知道有淺色選項
3. 語音輸入中，聲波在動
4. 選單的「特效」分頁，看得到八個開關
5. 選單的「外觀」分頁，看得到十一個配色色塊
6. 表情符號頁

### 橫幅

`docs/banner.png`（見同目錄）。若要重做，尺寸參考 SideQuest 現有的 app 頁面。

---

## 自動發佈：GitHub Release → SideQuest

SideQuest 不是用 OAuth 讀你的 repo，是用 **webhook**。設好之後每發一個
GitHub Release，SideQuest 上的版本就自動跟著更新，不用再手動上傳 APK。

設定順序（**webhook 網址要先有 app listing 才拿得到**）：

1. 先在 SideQuest 建立 app listing（用上面那些欄位）
2. 到該 app 的 **app manager** 頁面，複製 `https://sdq.st/release-webhook/<TOKEN>`
3. GitHub repo → Settings → Webhooks → Add webhook
   - Payload URL：貼上剛剛複製的網址
   - Content type：`application/json`
   - 事件：選「Let me select individual events」，**只勾 Releases**

那個網址裡的 TOKEN 等於發佈權限，**不要提交進版控、不要貼在 issue 裡**。

### 發版時必須遵守的規矩

**每次發新版一定要把 `versionCode` 加一。**
Android 用 `versionCode`（整數）判斷新舊，`versionName` 只是給人看的。
忘了加的話，使用者的裝置會認為那不是更新，SideQuest 與我們自己的
自我更新功能都可能不會生效。

發版的完整順序：

```powershell
# 1. app/build.gradle.kts：versionCode +1，versionName 跟著改
# 2. 建置並確認測試全過
gradle testDebugUnitTest assembleRelease --console=plain

# 3. 發 Release（tag 要跟 versionName 對得起來，例如 0.3.0 → v0.3.0）
gh release create v0.3.0 `
  app/build/outputs/apk/release/app-release.apk#VRTwKeyboard-v0.3.0.apk `
  --title "v0.3.0 — 說明" --notes-file notes.md
```

第 3 步一完成，webhook 就會通知 SideQuest。

---

## 送審前的檢查

- [x] APK 用 release 金鑰簽過（v2 簽章）
- [x] `versionName` 與 GitHub release tag 一致（0.2.0 / v0.2.0）
- [x] 授權檔與 NOTICE 都在 repo 裡
- [ ] 截圖（要頭盔）
- [x] ~~橫幅~~ → `docs/banner.png`
- [ ] 實際在乾淨的頭盔上用 SideQuest 裝一次，確認裝得起來

最後一項最重要：**我們自己還沒用 SideQuest 裝過這個 APK**。
上架前應該先用 SideQuest 桌面版手動裝一次，確認它不會因為簽章或 manifest 被擋下來。

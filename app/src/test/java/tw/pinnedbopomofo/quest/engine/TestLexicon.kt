package tw.pinnedbopomofo.quest.engine

import java.io.File

/**
 * 測試用的詞庫資料夾。
 *
 * 2026-09-17 之前，詞庫放在這個 repo **外面**的 pime-bopomofo-core，測試直接寫死相對路徑。
 * 準備公開發佈時把資料搬進 `app/src/main/assets`，所以這裡先找 repo 內的那一份——
 * 不然別人 clone 下來跑測試一定失敗（他們沒有 pime-bopomofo-core）。
 *
 * 外部路徑留著當備案，方便在 monorepo 裡拿還沒搬進來的新資料比對。
 * `ENGINE_TEST_DATA` 環境變數優先於兩者。
 */
object TestLexicon {

    private val CANDIDATES = listOf(
        // repo 內（發佈用的那一份）
        "app/src/main/assets",
        "src/main/assets",
        // 從 app/ 目錄跑時
        "../app/src/main/assets",
        // monorepo 裡的上游來源
        "../../pime-bopomofo-core/bopomofo_core/data",
    )

    fun directory(): File {
        System.getenv("ENGINE_TEST_DATA")?.let { return File(it) }
        for (path in CANDIDATES) {
            val file = File(path)
            if (file.isDirectory && File(file, "high_frequency_phrases.json").isFile) return file
        }
        // 都找不到就回第一個，讓呼叫端的 check() 印出可讀的路徑
        return File(CANDIDATES.first())
    }
}

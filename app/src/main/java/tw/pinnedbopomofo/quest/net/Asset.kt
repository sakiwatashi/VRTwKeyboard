package tw.pinnedbopomofo.quest.net

/**
 * 一個要下載的檔案：網址、預期大小、預期 SHA256。
 *
 * 三樣都釘死是刻意的。只比大小的話，半途被中斷的檔案接上去仍然剛好等於預期大小；
 * 只比雜湊的話，要整個下載完才知道白費工。兩個都有才能既早停也可信。
 */
data class Asset(
    val name: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
)

/** 下載進度。[done] 是已完成的位元組，[total] 是 0 表示伺服器沒給長度。 */
data class Progress(val done: Long, val total: Long) {

    /** 0～1；總量未知時回 -1，呼叫端要畫成不定量的進度條。 */
    val fraction: Float
        get() = if (total <= 0L) -1f else (done.toFloat() / total).coerceIn(0f, 1f)

    val percent: Int
        get() = if (total <= 0L) -1 else Math.round(fraction * 100)

    companion object {
        /** 人看得懂的大小。用 MB / GB（1024 進位），跟系統的檔案管理員一致。 */
        fun readable(bytes: Long): String = when {
            bytes < 0L -> "?"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

/**
 * 續傳的判斷：既有的檔案要從哪裡接下去，還是根本不能用。
 *
 * 分開成純函式是因為這裡最容易錯，而且錯了很難察覺——
 * 「檔案比預期大」若當成可以續傳，就會一直送出超出範圍的 Range 請求；
 * 「剛好等於預期大小」若還去續傳，伺服器會回 416 而不是成功。
 */
object Resume {

    sealed interface Plan {
        /** 檔案已經完整，只要驗雜湊。 */
        data object Verify : Plan
        /** 從 [offset] 續傳。 */
        data class Continue(val offset: Long) : Plan
        /** 重來：既有檔案不可信（太大、或預期大小不明）。 */
        data object Restart : Plan
    }

    /**
     * [existing] 是磁碟上現有的位元組數（沒有檔案就給 0），[expected] 是 [Asset.bytes]。
     */
    fun plan(existing: Long, expected: Long): Plan = when {
        existing < 0L -> Plan.Restart
        expected <= 0L -> Plan.Restart          // 不知道該多大就不要猜，重下
        existing == 0L -> Plan.Continue(0L)
        existing == expected -> Plan.Verify
        existing > expected -> Plan.Restart     // 比預期大 = 這不是我們要的檔案
        else -> Plan.Continue(existing)
    }

    /** HTTP Range 標頭；從 0 開始就不需要標頭。 */
    fun rangeHeader(offset: Long): String? = if (offset > 0L) "bytes=$offset-" else null
}

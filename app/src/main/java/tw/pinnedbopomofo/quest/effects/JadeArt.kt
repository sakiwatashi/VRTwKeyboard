package tw.pinnedbopomofo.quest.effects

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * 用圖檔畫的青玉素材：外框、鍵帽、空白鍵的山水玉板。
 *
 * 一律走 [NineSlice]，不是整張拉伸——四角的雲紋、流蘇、月亮一拉就變形。
 *
 * **載入時就縮到實際要用的大小。**原圖是 1774×887 之類的尺寸，
 * 照原尺寸解碼一張就要 6 MB；鍵盤上實際只有幾十到幾百像素。
 * 這件事在這個專案特別要緊：載入語音模型之後記憶體已經到 382 MB。
 */
object JadeArt {

    private const val TAG = "ZhuyinIme"

    /** 已經縮好的圖，用「資源 id + 目標寬」當快取鍵：面板尺寸會變，變了就重載一張。 */
    private val cache = HashMap<String, Bitmap>()

    /** alpha 低於這個就當成透明邊。生成圖的邊緣常有一圈接近全透明的殘留。 */
    private const val ALPHA_THRESHOLD = 24

    /**
     * 解碼並縮到接近 [targetWidth] 的大小。
     *
     * 用 `inSampleSize`（2 的次方）粗縮，剩下的交給繪製時的縮放——
     * 這樣比一路 createScaledBitmap 省記憶體，也不會多出一份中間產物。
     */
    fun load(resources: Resources, resourceId: Int, targetWidth: Int): Bitmap? {
        if (targetWidth <= 0) return null
        val key = "$resourceId@$targetWidth"
        cache[key]?.let { if (!it.isRecycled) return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resourceId, bounds)
        if (bounds.outWidth <= 0) {
            Log.d(TAG, "jade art 讀不到圖 id=$resourceId")
            return null
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeResource(resources, resourceId, options)
        if (bitmap == null) {
            Log.d(TAG, "jade art 解碼失敗 id=$resourceId")
            return null
        }
        // 生成的圖四周常有大片全透明的邊：山水玉板實際內容只佔高度的 22%～78%，
        // 不裁掉的話九宮格切出來的上下兩塊是空的，玉板看起來會變成一條線。
        val cropped = cropTransparent(bitmap)
        if (cropped !== bitmap) bitmap.recycle()
        Log.d(
            TAG,
            "jade art id=$resourceId ${bounds.outWidth}x${bounds.outHeight} -> " +
                "${cropped.width}x${cropped.height} sample=$sample ${cropped.byteCount / 1024}KB",
        )
        cache[key] = cropped
        return cropped
    }

    /**
     * 裁掉四周幾乎全透明的邊。整張都透明就原樣回傳。
     *
     * 一次掃整張的 alpha：只在載入時做一次，而且是已經縮小過的圖。
     */
    fun cropTransparent(bitmap: Bitmap, threshold: Int = ALPHA_THRESHOLD): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) > threshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return bitmap
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    /** 換配色或收鍵盤時放掉，圖不會一直佔著。 */
    fun clearCache() {
        for (bitmap in cache.values) if (!bitmap.isRecycled) bitmap.recycle()
        cache.clear()
    }

    /**
     * 九宮格拉伸的圖。[insetX]、[insetY] 是角落佔原圖的比例。
     *
     * [alpha] 讓呼叫端套面板的透明度設定；圖本身不重新解碼。
     */
    class NineSliceDrawable(
        private val bitmap: Bitmap,
        private val insetX: Float,
        private val insetY: Float,
    ) : Drawable() {

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val src = Rect()
        private val dst = RectF()

        override fun draw(canvas: Canvas) {
            if (bitmap.isRecycled) return
            val area = bounds
            val pieces = NineSlice.slices(
                bitmap.width, bitmap.height,
                area.width().toFloat(), area.height().toFloat(),
                insetX, insetY,
            )
            canvas.save()
            canvas.translate(area.left.toFloat(), area.top.toFloat())
            for (piece in pieces) {
                if (piece.dstWidth <= 0f || piece.dstHeight <= 0f) continue
                src.set(piece.srcLeft, piece.srcTop, piece.srcRight, piece.srcBottom)
                dst.set(piece.dstLeft, piece.dstTop, piece.dstRight, piece.dstBottom)
                canvas.drawBitmap(bitmap, src, dst, paint)
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

package com.wilsontryingapp2023.drawingbysound

import android.content.Context
import android.graphics.*
import android.os.AsyncTask
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar


class PaintView : View {
    private var progressBar : ProgressBar? = null
    private var brushSize : Int = 0 //  先設定成0，之後會再更改
    private var currentColor = 0 //  先設定成0，之後會再更改

    private var myPath: Path? = null // Path物件代表每條線
    private val paths = ArrayList<FingerPath>()

    // myCanvas是畫筆、myPaint是畫筆的設定，例如畫筆的顏色、粗度等等、myBitmap是畫紙
    private var myPaint: Paint? = null
    private var myBitmap: Bitmap? = null
    private var myCanvas: Canvas? = null
    private var myBitmapPaint: Paint? = null

    // mode 1代表目前要畫線條，-1代表fill
    var mode = 1
    private var mX = 0f
    private var mY = 0f

    companion object {
        private const val COLOR_PEN = Color.RED
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        // 以下這些設定，是對於整個畫面中的所有Finger Path都適用的總設定
        myPaint = Paint()
        myPaint!!.isAntiAlias = true // 抗鋸齒效果設定
        myPaint!!.isDither = true
        // Dithering通過交替不同顏色的像素來產生新顏色的錯覺，可以產生color panel上不存在的顏色。
        // 此技術還可用於減少漸變中的條帶或平滑顏色過渡。
        myPaint!!.style = Paint.Style.STROKE
        // 當 Paint Object的style屬性設置為 STROKE 時，會致出的結果會是Stroke
        // 其他可以設定的有Fill以及Fill_and_Stroke
        myPaint!!.strokeJoin = Paint.Join.ROUND
        // 指定應如何連接stroke的corners
        // 如果設定ROUND，代表設定成平滑的曲線
        myPaint!!.strokeCap = Paint.Cap.ROUND
        // 設定筆畫的起點與終點是圓形，而不是扁平或方形
        myPaint!!.alpha = 0xff
        // alpha設定透明度。這裡我們設定0xff代表完全不透明

        currentColor = COLOR_PEN
        myBitmapPaint = Paint()
        myBitmapPaint!!.isDither = true
    }


    override fun onSizeChanged(xNew: Int, yNew: Int, xOld: Int, yOld: Int) {
        super.onSizeChanged(xNew, yNew, xOld, yOld)
        var viewWidth = xNew
        var viewHeight = yNew
        // 如果在constructor內部取得PaintView的this.width, this.height都是0
        // 所以必須要在onSizeChanged中取得。
        // 此方法通常在首次繪製視圖時或視圖大小因佈局更改或包含該視圖的窗口大小發生更改時調用。

        // 取得PaintView的viewWidth, viewHeight之後，用來設定myBitmap
        myBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        // Bitmap.Config.ARGB_8888 parameter specifies that the bitmap should use 32 bits per pixel, with alpha, red, green, and blue channels each using 8 bits.
        myCanvas = Canvas(myBitmap!!)
    }

    fun useProgressBar(bar : ProgressBar) {
        progressBar = bar
        progressBar!!.visibility = View.INVISIBLE;
    }

    fun changeBrushColor(color: String?) {
        currentColor = Color.parseColor(color)
    }

    fun changeMode(input: Int) {
        mode = input
    }

    fun clear() {
        paths.clear()
        val width = myBitmap!!.width
        val height = myBitmap!!.height
        for (i in 0 until width) {
            for (j in 0 until height) {
                myBitmap!!.setPixel(i, j, Color.WHITE)
            }
        }
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        // myCanvas的工作，是將圖案畫在myBitmap上面
        // onDraw的canvas物件的工作，是將myBitmap畫在PaintView上面
        for (fp in paths) {
            // 繪製paths時，根據每個path的顏色、寬度來設定paint object
            myPaint!!.color = fp.color
            myPaint!!.strokeWidth = fp.strokeWidth.toFloat()
            myCanvas!!.drawPath(fp.path, myPaint!!)
            myCanvas!!.save()
        }
        canvas.drawBitmap(myBitmap!!, 0f, 0f, myBitmapPaint)
    }

    private fun touchStart(x: Float, y: Float) {
        brushSize = if (MainActivity.EraserMode) { 40 } else { 10 }
        myPath = Path()
        val fp = FingerPath(currentColor, brushSize, myPath!!)
        paths.add(fp)

        myPath!!.moveTo(x, y)
        mX = x
        mY = y
    }

    private fun touchMove(x: Float, y: Float) {
        // 這四行程式碼，可以看到touchMove的x, y會出現在哪裡，這可以方便我們理解貝茲曲線的運作方式
//        var pointPaint : Paint = Paint()
//        pointPaint.color = Color.BLACK
//        pointPaint.strokeWidth = 30f
//        myCanvas!!.drawPoint(x, y, pointPaint)

        // 用quadratic Bézier curve讓他變平滑
        // quad會使用上次path走到的地方當作起點
        // x1, y1當作control point
        // x2, y2當作end point
        // 繪製出貝茲曲線
        // 這裡的邏輯細節，請參考Notion
        myPath!!.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
        mX = x
        mY = y
    }

    private fun touchUp() {
        // lineTo是畫直線
        myPath!!.lineTo(mX, mY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            // 如果偵測到手指往下，目前的mode>0代表要畫的是線條
            MotionEvent.ACTION_DOWN -> if (mode > 0) {
                touchStart(event.x, event.y) // 將event的x, y放入touchStart，也就代表，將touch start的起點記錄起來
                invalidate()
            } else {
                val fillPoint = Point()
                fillPoint.x = x.toInt()
                fillPoint.y = y.toInt()
                val sourceColor = myBitmap!!.getPixel(x.toInt(), y.toInt())
                val targetColor = currentColor
                TheTask(myBitmap!!, fillPoint, sourceColor, targetColor).execute()
                invalidate()
            }

            // 若目前是在移動手指，且目前正在畫線
            MotionEvent.ACTION_MOVE -> {
                if (mode > 0) {
                    touchMove(x, y)
                    invalidate()
                }
            }

            // 若手指拿起來了，且目前正在畫線
            MotionEvent.ACTION_UP -> if (mode > 0) {
                touchUp()
                invalidate()
            }
        }
        return true
    }

    internal inner class TheTask(
        var bmp: Bitmap,
        var pt: Point,
        var targetColor: Int,
        var replacementColor: Int) : AsyncTask<Void?, Int?, Void?>() {

        override fun onPreExecute() {
            progressBar!!.visibility = View.VISIBLE;
        }

        override fun onProgressUpdate(vararg values: Int?) {}
        override fun doInBackground(vararg po: Void?): Void? {
            val f = FloodFill()
            f.floodFill(bmp, pt, targetColor, replacementColor)
            return null
        }

        override fun onPostExecute(result: Void?) {
            progressBar!!.visibility = View.INVISIBLE
            invalidate()
        }
    }
}
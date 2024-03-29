package com.wilsontryingapp2023.drawingbysound

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import java.util.*
import java.util.concurrent.locks.ReentrantLock


// 我們用到thread的地方有兩個。第一個位置在clear內部，第二個位置在fill。
// 這裡我們使用newSingleThreadExecutor，而不是讓fill, clear個別去launch a new thread的原因是因為，
// 如果fill還沒結束時，使用者就按下clear，那兩個thread就會打架
// 造成結果不好。因此，我們用newSingleThreadExecutor()，讓這些UI介面上的按鈕被按下後，
// 任務會被放入queue依序去執行
// 這裡當然也可以選用Handler，概念與邏輯都是一樣的！！
// private var executor: ExecutorService = Executors.newSingleThreadExecutor()
// 或者，我們也可以使用在fill以及clear個別去launch一個thread
// 要避免race condition的話，就使用lock即可
// 我們的shared resources是myBitmap，所以fill以及clear有用到myBitmap的部分，使用前需要先上鎖
// 下面的程式碼是使用lock來達到mutex的效果

// 我們一定需要使用View(c, attrs)這個constructor才能夠從findViewById創建PaintView物件！
class PaintView(c: Context, attrs: AttributeSet) : View(c, attrs) {
    private lateinit var progressBar : ProgressBar
    private var brushSize : Int = 0 //  先設定成0，之後會再更改
    private var currentColor = Color.RED //  先設定成0，之後會再更改
    private lateinit var myPath: Path // Path物件代表每條線
    private val paths = ArrayList<FingerPath>()

    // myCanvas是畫筆、myPaint是畫筆的設定，例如畫筆的顏色、粗度等等、myBitmap是畫紙
    private var myPaint = Paint()
    lateinit var myBitmap: Bitmap
    private lateinit var myCanvas: Canvas
    private var myBitmapPaint: Paint = Paint()

    // mode 1代表目前要畫線條，-1代表fill, 0代表eraser
    private var mode = 1

    // 這兩個是給貝茲曲線用的變數，用來儲存x, y座標
    private var mX = 0f
    private var mY = 0f

    // 設定Handler, lock以及
    private var h = Handler(Looper.getMainLooper())
    private var lock : ReentrantLock = ReentrantLock()
    private var newPath = false

    init {
        // 以下這些設定，是對於整個畫面中的所有Finger Path都適用的總設定
        // 抗鋸齒效果設定成false，讓fill algorithm可以運作完全
        myPaint.isAntiAlias = false
        // Dithering通過交替不同顏色的像素來產生新顏色的錯覺，可以產生color panel上不存在的顏色。
        // 此技術還可用於減少漸變中的條帶或平滑顏色過渡。
        myPaint.isDither = true
        // 當 Paint Object的style屬性設置為 STROKE 時，會致出的結果會是Stroke
        // 其他可以設定的有Fill以及Fill_and_Stroke
        myPaint.style = Paint.Style.STROKE
        // 指定應如何連接stroke的corners
        // 如果設定ROUND，代表設定成平滑的曲線
        myPaint.strokeJoin = Paint.Join.ROUND
        // 設定筆畫的起點與終點是圓形，而不是扁平或方形
        myPaint.strokeCap = Paint.Cap.ROUND
        // alpha設定透明度。這裡我們設定0xff代表完全不透明
        myPaint.alpha = 0xff

        // 最後幫myBitmapPaint設定isDither即可
        myBitmapPaint.isDither = true
    }


    // 我們希望設定bitmap物件是個空白的畫紙的話，不能寫在init block，因為
    // init block內部取得PaintView的this.width, this.height都是0
    // 所以必須要在onSizeChanged中取得。此方法在首次繪製View Object時時會被調用。
    override fun onSizeChanged(xNew: Int, yNew: Int, xOld: Int, yOld: Int) {
        super.onSizeChanged(xNew, yNew, xOld, yOld)
        // 取得PaintView的viewWidth, viewHeight之後，用來設定myBitmap
        myBitmap = Bitmap.createBitmap(xNew, yNew, Bitmap.Config.ARGB_8888)
        // Bitmap.Config.ARGB_8888 parameter specifies that the bitmap should use 32 bits per pixel, with alpha, red, green, and blue channels each using 8 bits.
        myBitmap.eraseColor(Color.WHITE) // Fill the bitmap with color white
        myCanvas = Canvas(myBitmap)
    }

    fun getMode() : Int {
        return mode
    }

    fun useProgressBar(bar : ProgressBar) {
        progressBar = bar
        progressBar.visibility = INVISIBLE
    }

    fun changeBrushColor(color: String?) {
        currentColor = Color.parseColor(color)
    }

    fun changeMode(input: Int) {
        mode = input
    }


    override fun onDraw(canvas: Canvas) {
        // myCanvas的工作，是將圖案畫在myBitmap上面
        // onDraw的canvas物件的工作，是將myBitmap畫在PaintView上面

        if (newPath) {
            myPaint.color = paths[paths.size - 1].color
            myPaint.strokeWidth = paths[paths.size - 1].strokeWidth.toFloat()
            myCanvas.drawPath(paths[paths.size - 1].path, myPaint)
        }

        canvas.drawBitmap(myBitmap, 0f, 0f, myBitmapPaint)
    }

    private fun touchStart(x: Float, y: Float) {
        brushSize = if (mode == 0) { 40 } else { 10 }
        myPath = Path()
        paths.add(FingerPath(currentColor, brushSize, myPath))
        myPath.moveTo(x, y)
        mX = x
        mY = y
    }

    private fun touchMove(x: Float, y: Float) {
//        這四行程式碼，可以看到touchMove的x, y會出現在哪裡，這可以方便我們理解貝茲曲線的運作方式
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
        myPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
        mX = x
        mY = y

        // 這裡我們也可以比較一下，如果單純的用lineTo()，效果會非常的差，所以用quadTo()是很有用的
        // myPath!!.lineTo(x, y)

    }

    private fun touchUp() {
        // lineTo是畫直線
        myPath.lineTo(mX, mY)
    }

    private fun floodFill(image: Bitmap, node: Point?, targetColor: Int, replacementColor: Int) {
        var myNode = node
        val width = image.width
        val height = image.height

        if (targetColor != replacementColor) {
            val queue: Queue<Point> = LinkedList()
            do {
                var x = myNode!!.x
                val y = myNode!!.y

                // 先退回到最左邊的位置
                while (x > 0 && image.getPixel(x - 1, y) == targetColor) {
                    x--
                }
                // 設定兩個變數，代表是否需要向上或向下span
                var spanUp = false
                var spanDown = false

                while (x < width && image.getPixel(x, y) == targetColor) {
                    image.setPixel(x, y, replacementColor)
                    if (!spanUp && y > 0 && image.getPixel(x, y - 1) == targetColor) {
                        queue.add(Point(x, y - 1))
                        spanUp = true
                    } else if (spanUp && y > 0 && image.getPixel(x, y - 1) != targetColor) {
                        spanUp = false
                    }
                    if (!spanDown && y < height - 1 && image.getPixel(x, y + 1) == targetColor) {
                        queue.add(Point(x, y + 1))
                        spanDown = true
                    } else if (spanDown && y < (height - 1) && image.getPixel(x, y + 1) != targetColor) {
                        spanDown = false
                    }
                    x++
                }

                 // println(queue.size)
                // 如果我們在這個while loop內部，一邊確認queue內部需要新增node，一邊invalidate()
                // 這樣可能會造成queue積累越多。這是因為，我們一邊根據設定bitmap的pixel的值，
                // 一邊讓main thread執行invalidate()，把paths內部的pixel又畫在bitmap上面，使得 image.getPixel(x, y)可能已經
                // 先被從綠色改成白色，invalidate()後，又改回綠色
                // 造成重複放入相同的Point進入queue.size，結果就是沒完沒了的局面
                // 從queue.size就可以看出來了
                invalidate()
//                h.post {
//                    invalidate()
//                }
                myNode = queue.poll()
            } while (myNode != null)
        }
    }

    private fun fillWork(
         bmp: Bitmap,
         pt: Point,
         targetColor: Int,
         replacementColor: Int) {
        Thread {
            h.post {
                progressBar.visibility = VISIBLE
            }
            lock.lock()
            floodFill(bmp, pt, targetColor, replacementColor)
            lock.unlock()
            h.post {
                progressBar.visibility = INVISIBLE
            }
        }.start()
//        executor.execute {
//            h.post {
//                progressBar!!.visibility = View.VISIBLE
//            }
//            floodFill(bmp, pt, targetColor, replacementColor)
//            h.post {
//                progressBar!!.visibility = View.INVISIBLE
//            }
//        }
    }

    fun loadBitmap(bitmapImage: Bitmap) {
        myBitmap = bitmapImage
        myCanvas = Canvas(myBitmap)
        invalidate()
    }

    fun clear() {
        Thread {
            h.post {
                progressBar.visibility = VISIBLE
            }
            paths.clear()
            lock.lock()
            val width = myBitmap.width
            val height = myBitmap.height
            for (i in 0 until width) {
                for (j in 0 until height) {
                    myBitmap.setPixel(i, j, Color.WHITE)
                }
                // 每當一個直列的pixels被設定成白色後，就讓main thread做invalidate()一次
                invalidate()
//                h.post {
//                    invalidate()
//                }
            }
            lock.unlock()
            h.post {
                progressBar.visibility = INVISIBLE
            }
        }.start()
//        executor.execute {
//            h.post {
//                progressBar!!.visibility = View.VISIBLE
//            }
//
//            paths.clear()
//            val width = myBitmap!!.width
//            val height = myBitmap!!.height
//            for (i in 0 until width) {
//                for (j in 0 until height) {
//                    myBitmap!!.setPixel(i, j, Color.WHITE)
//                }
//                // 每當一個直列的pixels被設定成白色後，就讓main thread做invalidate()一次
//                h.post {
//                    invalidate()
//                }
//            }
//            h.post {
//                progressBar!!.visibility = View.INVISIBLE
//            }
//        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            // 如果偵測到手指往下，目前的mode>=0代表要畫的是線條
            MotionEvent.ACTION_DOWN -> if (mode >= 0) {
                newPath = true
                touchStart(event.x, event.y) // 將event的x, y放入touchStart，也就代表，將touch start的起點記錄起來
                invalidate()
            } else {
                val fillPoint = Point()
                fillPoint.x = x.toInt()
                fillPoint.y = y.toInt()
                val sourceColor = myBitmap.getPixel(x.toInt(), y.toInt())
                val targetColor = currentColor
                fillWork(myBitmap, fillPoint, sourceColor, targetColor)
            }

            // 若目前是在移動手指，且目前正在畫線
            MotionEvent.ACTION_MOVE -> {
                if (mode >= 0) {
                    touchMove(x, y)
                    invalidate()
                }
            }

            // 若手指拿起來了，且目前正在畫線
            MotionEvent.ACTION_UP -> if (mode >= 0) {
                touchUp()
                invalidate()
                newPath = false
            }
        }
        return true
    }
}
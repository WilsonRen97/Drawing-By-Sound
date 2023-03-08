package com.wilsontryingapp2023.drawingbysound

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.DisplayMetrics
import android.util.TypedValue
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val AUDIO_RECORD_REQUEST_CODE =
            1 // this could be any value. 我們這裡用不到，但還是要提供一下。
        var EraserMode = false
        var isListening: Boolean = false
    }

    // 取得widgets
    private var paintView: PaintView? = null
    private var resultText: TextView? = null
    private var btn: Button? = null
    private var progressBar : ProgressBar? = null
    private var clearBtn : Button? = null
    private var fillBtn : Button? = null
    private var penBtn : Button? = null

    private var sr: SpeechRecognizer? = null
    private var all = ""
    private val listener: Listener = Listener()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //  獲得使用者的聲音錄製許可
        if (!isRecordAudioPermissionGranted()) {
            Toast.makeText(applicationContext, "需要聲音錄製許可", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(applicationContext, "聲音錄製許可已經獲准", Toast.LENGTH_LONG).show()
        }

        // SpeechRecognizer is a class provided by the Android platform that performs speech recognition.
        // It provides methods to start and stop speech recognition, set various options for the recognition task (e.g. language, maximum results)
        // It uses Android's own speech recognition engine, which is installed on the device and does not require any additional apps to be installed.
        // The engine listens to audio input from the device's microphone and performs speech recognition on the audio.
        sr = SpeechRecognizer.createSpeechRecognizer(this)

        // RecognitionListener, on the other hand, is an interface provided by the Android platform that allows developers to receive notifications
        // about the progress and results of a speech recognition task.
        // It defines a number of callback methods that are invoked during the recognition process,
        // such as onBeginningOfSpeech(), onResults(), and onError().
        sr!!.setRecognitionListener(listener)


        setContentView(R.layout.activity_main)
        paintView = findViewById(R.id.paint_view)
        resultText = findViewById(R.id.textView3)
        btn = findViewById(R.id.myBtn)
        progressBar = findViewById(R.id.progressBar)
        clearBtn = findViewById(R.id.clearButton)
        fillBtn = findViewById(R.id.fillBtn)
        penBtn = findViewById(R.id.penBtn)
        paintView!!.useProgressBar(progressBar!!)

        resultText!!.text = "聲音辨識結果在這裏"
        btn!!.setOnClickListener() { _ ->
            if (!isListening) {
                isListening = true
                // 改變button顏色與文字
                btn!!.text = "正在接收語音指令"
                btn!!.setTextColor(Color.WHITE)
                btn!!.setBackgroundColor(Color.BLACK)

                // 開啟語音辨識功能
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                // intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cmn-Hant-TW")
                // intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cmn-Hans-CN")
                intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                sr!!.startListening(intent)
                println("Start Listening....")
            }
        }

        clearBtn!!.setOnClickListener(){_ ->
            paintView!!.clear()
        }

        fillBtn!!.setOnClickListener(){_ ->
            paintView!!.changeMode(-1)
        }

        penBtn!!.setOnClickListener(){_ ->
            paintView!!.changeMode(1)
        }
    }


    /**
     * 如果使用者同意聲音錄製，則return true
     */
    private fun isRecordAudioPermissionGranted(): Boolean {
        // Build.VERSION_SDK_INT是指當前在此硬件設備上運行的軟件的 SDK 版本。
        // Build.VERSION_CODES內部有很多constants，代表不同的Android版本。M代表6.0版，O代表8.0版
        // Android 6.0版使用的SDK中的的API的level，是對應到23
        // 所以這個if statement意思是去確認，目前Android版本的SDK相對應到的API level數字，是否
        // 小於Version Code是M的Android版本的的SDK相對應到的API level數字
        // 在這裏，我們需要確認SDK的版本大於Android 6.0版是因為，
        // The requestPermissions() method was added to the Activity class in Android 6.0 (API level 23) as part of the runtime permissions feature.
        // 但如果創建Project時，就已經設定最小的SDK版本是Android7.0的話(這是Android Studio的default)
        // 其實可以不用這個if statement 來確認
        // 另外，從 Android 6.0（API 級別 23）開始，permission model模型更改為運行時權限系統，
        // 這意味著用戶必須在運行時而不是在安裝期間授予 RECORD_AUDIO 權限。
        // 也就是說，是使用App時，才給予App錄音權限的，所以我們這裡需要先確認，用戶的手機目前版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            } else {
                // requestPermissions 是Activity class的method
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    AUDIO_RECORD_REQUEST_CODE
                )
                // 第二個參數是request code
                // 通過檢查 onRequestPermissionsResult() 中的 requestCode 值，您可以確定用戶響應了哪個權限請求，並根據用戶的響應採取適當的操作。
                return false
            }
        } else {
            // 如果用戶手機版本很低，那權限在安裝時就會給予。
            return true
        }
    }


    inner class Listener : RecognitionListener {

        private fun parseColor(color: String): String {
            return if (color == "blue" || color == "blu") {
                "blue"
            } else if ((color == "green") || color == "queen" || color == "ring") {
                "green"
            } else if ((color == "red") || color == "read" || color == "lead") {
                "red"
            } else if ((color == "jello") || (color == "mellow") || (color == "yellow")) {
                "yellow"
            } else {
                color
            }
        }

        private fun restoreBtnStyling() {
            // fetch color programmatically
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true);
            val textColor = ContextCompat.getColor(applicationContext, typedValue.resourceId)
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
            val backgroundColor = ContextCompat.getColor(applicationContext, typedValue.resourceId)

            // 設定isListening是false，以及將button的顏色、文字、背景恢復到原本的設定
            btn!!.text = "點擊給予語音指令"
            btn!!.setTextColor(textColor)
            btn!!.setBackgroundColor(backgroundColor)
            isListening = false
        }

        // 如果RecognitionListener聽到使用者在時間內講出的話，就會進入onEndOfSpeech
        override fun onEndOfSpeech() {
            restoreBtnStyling()
            println("End listening...")
        }

        // 如果使用者沒有在時間內講出任何話，就會進入onError
        override fun onError(error: Int) {
            restoreBtnStyling()
            resultText!!.text = "聲音辨識的結果為：沒有聽到任何聲音"
            println("error happened...")
        }

        override fun onResults(results: Bundle) {
            val data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            // we will get 5 different possible results
            /*
            for (int i = 0; i < data.size(); i++) {
                System.out.println("result: " + data.get(i));
                str += data.get(i);
            }
            */

            // here, we just take the first result
            println(data)
            all = data!![0].toString()
            resultText!!.text = "聲音辨識結果是：$all" // Get This one back later


            // making the string command change the pen color
            val command = data[0].toString()
            val commandString = command.split(" ")
            val colors = ArrayList<String>()
            for (i in commandString.indices) {
                val currentString = commandString[i].lowercase()
                if (currentString == "clear") {
                    paintView!!.clear()
                }
                if (currentString == "eraser" || currentString == "erase" || currentString == "chaser" || currentString == "tracer" || currentString == "spacer") {
                    colors.add(currentString)
                    paintView!!.changeMode(1)
                    EraserMode = true
                } else if (currentString == "fill" || currentString == "fell" || currentString == "feel" || currentString == "fail" || currentString == "phil" || currentString == "chill") {
                    paintView!!.changeMode(-1)
                } else if (currentString == "pen" || currentString == "pain" || currentString == "pane") {
                    paintView!!.changeMode(1)
                }
                if (currentString == "blue" || currentString == "blu" || currentString == "black" || currentString == "cyan" || currentString == "gray" || currentString == "green" || currentString == "queen" || currentString == "ring" || currentString == "magenta" || currentString == "orange" || currentString == "red" || currentString == "read" || currentString == "lead" || currentString == "white" || currentString == "jello" || currentString == "mellow" || currentString == "yellow") {
                    colors.add(currentString)
                    EraserMode = false
                }
            }

            if (colors.size == 0) {
                val t =
                    Toast.makeText(applicationContext, "No color detected.", Toast.LENGTH_LONG)
                t.show()
            } else if (colors.size == 1) {
                if (colors[0] == "eraser") {
                    paintView!!.changeBrushColor("white")
                } else {
                    paintView!!.changeBrushColor(parseColor(colors[0]))
                }
            } else {
                val t = Toast.makeText(
                    applicationContext,
                    "Multiple color detected. Please provide only one color in your sentence.",
                    Toast.LENGTH_LONG
                )
                t.show()
            }

        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray) {}
        override fun onReadyForSpeech(params: Bundle) {}
        override fun onPartialResults(partialResults: Bundle) {}
        override fun onEvent(eventType: Int, params: Bundle) {}
    }
}
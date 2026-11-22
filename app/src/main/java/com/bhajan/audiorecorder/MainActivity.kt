package com.bhajan.audiorecorder

import androidx.activity.ComponentActivity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.CookieManager
import android.widget.*
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var status: TextView

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                requestCapturePermission()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission जरूरी है",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val captureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK && result.data != null) {

                val intent = Intent(this, RecordingService::class.java)
                intent.action = RecordingService.ACTION_START
                intent.putExtra(
                    RecordingService.EXTRA_RESULT_CODE,
                    result.resultCode
                )
                intent.putExtra(
                    RecordingService.EXTRA_RESULT_DATA,
                    result.data
                )

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                status.text = "● Recording Started"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)

        // YouTube WebView
        val webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.loadUrl("https://m.youtube.com")

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // Recording Control Panel
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(4, 2, 4, 2)
        panel.setBackgroundColor(Color.rgb(240, 240, 240))

        status = TextView(this)
        status.text = "● Ready to Record"
        status.textSize = 15f
        status.setTextColor(Color.BLACK)
        status.gravity = Gravity.CENTER

        panel.addView(status)

        val buttonHeight = (48 * resources.displayMetrics.density).toInt()
        // First Row
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL

        val record = Button(this)
        record.text = "🔴 RECORD"
        record.setOnClickListener {
            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                requestCapturePermission()
            } else {
                permissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }

        val pause = Button(this)
        pause.text = "⏸ PAUSE"
        pause.setOnClickListener {
            sendAction(RecordingService.ACTION_PAUSE)
            status.text = "⏸ Recording Paused"
        }

        row1.addView(record, LinearLayout.LayoutParams(0, buttonHeight, 1f))
        row1.addView(pause, LinearLayout.LayoutParams(0, buttonHeight, 1f))

        panel.addView(row1)

        // Second Row
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL

        val resume = Button(this)
        resume.text = "▶ RESUME"
        resume.setOnClickListener {
            sendAction(RecordingService.ACTION_RESUME)
            status.text = "● Recording Resumed"
        }

        val stop = Button(this)
        stop.text = "⏹ STOP"
        stop.setOnClickListener {
            sendAction(RecordingService.ACTION_STOP)
            status.text = "■ Recording Stopped"
        }

        row2.addView(resume, LinearLayout.LayoutParams(0, buttonHeight, 1f))
        row2.addView(stop, LinearLayout.LayoutParams(0, buttonHeight, 1f))
        
        panel.addView(row2)

        // My Recordings
        val recordings = Button(this)
        recordings.text = "🎵 MY RECORDINGS"

        panel.addView(
            recordings,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt()
            )
        )

        root.addView(
            panel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun requestCapturePermission() {
        captureLauncher.launch(
            projectionManager.createScreenCaptureIntent()
        )
    }

    private fun sendAction(action: String) {
        val intent = Intent(this, RecordingService::class.java)
        intent.action = action
        startService(intent)
    }
}

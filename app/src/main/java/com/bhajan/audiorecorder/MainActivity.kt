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
import android.provider.MediaStore
import android.content.ContentUris
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.ContentValues
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var status: TextView
    private lateinit var timerText: TextView
    private val recordingReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent?.action == RecordingService.ACTION_RECORDING_READY) {

                val filePath = intent.getStringExtra(
                    RecordingService.EXTRA_TEMP_FILE
                )

                if (filePath != null) {
                    showSaveDialog(filePath)
                }
            }
        }
    }
    private val timerHandler = Handler(Looper.getMainLooper())

    private var recordingStartTime = 0L
    private var elapsedBeforePause = 0L
    private var timerRunning = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsed = elapsedBeforePause +
                    SystemClock.elapsedRealtime() - recordingStartTime

                val totalSeconds = elapsed / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60

                timerText.text = String.format(
                    Locale.getDefault(),
                    "⏱ %02d:%02d:%02d",
                    hours, minutes, seconds
                )

                timerHandler.postDelayed(this, 1000)
            }
        }
    }

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
                elapsedBeforePause = 0L
                recordingStartTime = SystemClock.elapsedRealtime()
                timerRunning = true
                timerHandler.removeCallbacks(timerRunnable)
                timerHandler.post(timerRunnable)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            recordingReceiver,
            IntentFilter(RecordingService.ACTION_RECORDING_READY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
        timerText = TextView(this)
        timerText.text = "⏱ 00:00:00"
        timerText.textSize = 20f
        timerText.setTextColor(Color.BLACK)
        timerText.gravity = Gravity.CENTER
        timerText.setPadding(4, 6, 4, 6)

        panel.addView(timerText)

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
            if (timerRunning) {
                elapsedBeforePause +=
                    SystemClock.elapsedRealtime() - recordingStartTime

                timerRunning = false
                timerHandler.removeCallbacks(timerRunnable)
            }
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
            recordingStartTime = SystemClock.elapsedRealtime()
            timerRunning = true
            timerHandler.removeCallbacks(timerRunnable)
            timerHandler.post(timerRunnable)
        }

        val stop = Button(this)
        stop.text = "⏹ STOP"
        stop.setOnClickListener {
            sendAction(RecordingService.ACTION_STOP)
            status.text = "■ Recording Stopped"
            timerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
        }

        row2.addView(resume, LinearLayout.LayoutParams(0, buttonHeight, 1f))
        row2.addView(stop, LinearLayout.LayoutParams(0, buttonHeight, 1f))
        
        panel.addView(row2)

        // My Recordings
        val recordings = Button(this)
        recordings.text = "🎵 MY RECORDINGS"
        recordings.setOnClickListener {
            showRecordings()
        }

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

    private fun showSaveDialog(filePath: String) {

    val input = EditText(this)
    input.hint = "रिकॉर्डिंग का नाम लिखें"
    input.setText(
        "Bhajan_" + SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())
    )

    AlertDialog.Builder(this)
        .setTitle("रिकॉर्डिंग सेव करें")
        .setMessage("अपनी रिकॉर्डिंग का नाम लिखें")
        .setView(input)
        .setPositiveButton("SAVE") { _, _ ->

            var fileName = input.text.toString().trim()

            if (fileName.isEmpty()) {
                fileName = "Bhajan_Recording"
            }

            if (!fileName.endsWith(".wav", true)) {
                fileName += ".wav"
            }

            val sourceFile = File(filePath)

            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        "Music/Bhajan Audio Recorder"
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { destination ->
                        sourceFile.inputStream().use { source ->
                            source.copyTo(destination)
                        }
                    }

                    val completedValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }

                    contentResolver.update(
                        uri,
                        completedValues,
                        null,
                        null
                    )

                    sourceFile.delete()

                    Toast.makeText(
                        this,
                        "रिकॉर्डिंग सफलतापूर्वक सेव हो गई",
                        Toast.LENGTH_LONG
                    ).show()

                } else {
                    Toast.makeText(
                        this,
                        "रिकॉर्डिंग सेव नहीं हो सकी",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()

                Toast.makeText(
                    this,
                    "सेव करने में समस्या हुई",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        .setNegativeButton("CANCEL", null)
        .show()
    }
    
    private fun showRecordings() {

        val names = ArrayList<String>()
        val ids = ArrayList<Long>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"

        val selectionArgs = arrayOf(
            "Music/Bhajan Audio Recorder/"
        )

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                ids.add(
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            MediaStore.Audio.Media._ID
                        )
                    )
                )

                names.add(
                    it.getString(
                        it.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.DISPLAY_NAME
                        )
                    )
                )
            }
        }

        if (names.isEmpty()) {
            Toast.makeText(
                this,
                "अभी कोई रिकॉर्डिंग उपलब्ध नहीं है",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("मेरी रिकॉर्डिंग")
            .setItems(names.toTypedArray()) { _, position ->

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ids[position]
                )

                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "audio/*")
                
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(intent)
        }
        .setNegativeButton("बंद करें", null)
        .show()
    }
 override fun onDestroy() {
     timerHandler.removeCallbacks(timerRunnable)
     unregisterReceiver(recordingReceiver)
     super.onDestroy()
 }
}

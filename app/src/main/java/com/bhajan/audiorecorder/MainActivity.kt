package com.bhajan.audiorecorder

import androidx.appcompat.app.AppCompatActivity
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
import android.graphics.drawable.GradientDrawable
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

class MainActivity : AppCompatActivity() {
    private fun getThemeColor(attribute: Int): Int {
    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)
    return typedValue.data
}

    private fun createRoundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
        }
    }

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

        // Modern App Header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(16, 12, 16, 12)
        header.setBackground(
            createRoundedBackground(
                getThemeColor(android.R.attr.colorBackgroundFloating),
                16f
            )
        )

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo1)
        logo.layoutParams = LinearLayout.LayoutParams(
            (48 * resources.displayMetrics.density).toInt(),
            (48 * resources.displayMetrics.density).toInt()
        )
        header.addView(logo)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(
            getThemeColor(android.R.attr.colorBackground)
        )
        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val headerSpace = View(this)
        headerSpace.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (8 * resources.displayMetrics.density).toInt()
        )
        root.addView(headerSpace)

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

        webView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
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
        panel.setPadding(8, 4, 8, 4)
        panel.background = createRoundedBackground(
            getThemeColor(android.R.attr.colorBackgroundFloating),
            20f
        )
        panel.setBackgroundColor(
            getThemeColor(android.R.attr.colorBackground)
        )

        status = TextView(this)
        status.text = "● Ready to Record"
        status.textSize = 16f
        status.setTypeface(null, android.graphics.Typeface.BOLD)
        status.setPadding(4, 0, 4, 0)
        status.setTextColor(
            getThemeColor(android.R.attr.textColorPrimary)
        )
        status.gravity = Gravity.CENTER

        panel.addView(status)
        timerText = TextView(this)
        timerText.text = "⏱ 00:00:00"
        timerText.textSize = 26f
        timerText.setTypeface(null, android.graphics.Typeface.BOLD)
        timerText.setPadding(4, 10, 4, 10)
        timerText.setTextColor(
            getThemeColor(android.R.attr.textColorPrimary)
        )
        timerText.gravity = Gravity.CENTER
        timerText.setPadding(4, 0, 4, 0)

        panel.addView(timerText)
        timerText.visibility = View.VISIBLE

        val buttonHeight = (48 * resources.displayMetrics.density).toInt()
        // First Row
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL

        val record = Button(this)
        record.text = "🔴 RECORD"
        record.background = createRoundedBackground(
            Color.rgb(220, 53, 69),
            14f
        )
        record.setTextColor(Color.WHITE)
        record.isAllCaps = false
        record.elevation = 6f
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
        pause.background = createRoundedBackground(
            Color.rgb(230, 160, 30),
            14f
        )
        pause.setTextColor(Color.WHITE)
        pause.isAllCaps = false
        pause.elevation = 6f
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

        val buttonMargin = (5 * resources.displayMetrics.density).toInt()
        row1.addView(record, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
            setMargins(0, 4, buttonMargin, 4)
        })
        row1.addView(pause, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
            setMargins(buttonMargin, 4, 0, 4)
        })

        panel.addView(row1)

        // Second Row
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL

        val resume = Button(this)
        resume.text = "▶ RESUME"
        resume.background = createRoundedBackground(
            Color.rgb(32, 160, 110),
            14f
        )
        resume.setTextColor(Color.WHITE)
        resume.isAllCaps = false
        resume.elevation = 6f
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
        stop.background = createRoundedBackground(
            Color.rgb(70, 85, 110),
            14f
        )
        stop.setTextColor(Color.WHITE)
        stop.isAllCaps = false
        stop.elevation = 6f
        stop.setOnClickListener {
            sendAction(RecordingService.ACTION_STOP)
            status.text = "■ Recording Stopped"
            timerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
        }

        row2.addView(resume, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
            setMargins(0, 4, buttonMargin, 4)
        })
        row2.addView(stop, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
            setMargins(buttonMargin, 4, 0, 4)
        })
        
        panel.addView(row2)

        // My Recordings
        val recordings = Button(this)
        recordings.text = "🎵 MY RECORDINGS"
        recordings.background = createRoundedBackground(
            Color.rgb(40, 115, 210),
            14f
        )
        recordings.setTextColor(Color.WHITE)
        recordings.isAllCaps = false
        recordings.elevation = 6f
        recordings.setOnClickListener {
            showRecordings()
        }

        val recordingsMargin = (5 * resources.displayMetrics.density).toInt()
        panel.addView(
            recordings,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(0, recordingsMargin, 0, 0)
            }
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

    val saveDialog = AlertDialog.Builder(this)
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
        .create()

        saveDialog.setCancelable(false)
        saveDialog.setCanceledOnTouchOutside(false)
        saveDialog.show()
    }
    
private fun showRecordings() {

    val names = ArrayList<String>()
    val ids = ArrayList<Long>()

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME
    )

    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf("Music/Bhajan Audio Recorder/")

    val cursor = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    )

    cursor?.use {
        while (it.moveToNext()) {
            ids.add(it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)))
            names.add(it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)))
        }
    }

    if (names.isEmpty()) {
        Toast.makeText(this, "अभी कोई रिकॉर्डिंग उपलब्ध नहीं है", Toast.LENGTH_LONG).show()
        return
    }

    val scrollView = ScrollView(this)
    val listLayout = LinearLayout(this)
    listLayout.orientation = LinearLayout.VERTICAL
    listLayout.setPadding(12, 8, 12, 8)
    scrollView.addView(listLayout)

    val dialog = AlertDialog.Builder(this)
        .setTitle("मेरी रिकॉर्डिंग")
        .setView(scrollView)
        .setNegativeButton("बंद करें", null)
        .create()

    for (i in names.indices) {

        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            ids[i]
        )

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(8, 8, 4, 8)

        val nameText = TextView(this)
        nameText.text = names[i]
        nameText.textSize = 16f
        nameText.setTextColor(getThemeColor(android.R.attr.textColorPrimary))
        nameText.setPadding(4, 8, 4, 8)

        row.addView(
            nameText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val more = Button(this)
        more.text = "⋮"
        more.textSize = 26f
        more.isAllCaps = false
        more.minWidth = 48

        more.setOnClickListener {
            val popup = PopupMenu(this, more)
            popup.menu.add(0, 1, 0, "चलाएँ (Play)")
            popup.menu.add(0, 2, 1, "नाम बदलें (Rename)")
            popup.menu.add(0, 3, 2, "मिटाएँ (Delete)")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {

                    1 -> {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(uri, "audio/*")
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "ऑडियो चलाने वाला ऐप नहीं मिला", Toast.LENGTH_LONG).show()
                        }
                        true
                    }

                    2 -> {
                        val input = EditText(this)
                        input.setText(names[i])

                        AlertDialog.Builder(this)
                            .setTitle("नाम बदलें")
                            .setView(input)
                            .setPositiveButton("SAVE") { _, _ ->
                                var newName = input.text.toString().trim()

                                if (newName.isNotEmpty()) {
                                    if (!newName.endsWith(".wav", true)) {
                                        newName += ".wav"
                                    }

                                    try {
                                        val values = ContentValues().apply {
                                            put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
                                        }

                                        val updated = contentResolver.update(uri, values, null, null)

                                        if (updated > 0) {
                                            Toast.makeText(this, "नाम बदल गया", Toast.LENGTH_SHORT).show()
                                            dialog.dismiss()
                                            showRecordings()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(this, "नाम बदलने में समस्या हुई", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton("CANCEL", null)
                            .show()
                        true
                    }

                    3 -> {
                        AlertDialog.Builder(this)
                            .setTitle("रिकॉर्डिंग मिटाएँ?")
                            .setMessage(names[i] + " को मिटाना चाहते हैं?")
                            .setPositiveButton("DELETE") { _, _ ->
                                try {
                                    val deleted = contentResolver.delete(uri, null, null)

                                    if (deleted > 0) {
                                        Toast.makeText(this, "रिकॉर्डिंग मिटा दी गई", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                        showRecordings()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this, "मिटाने में समस्या हुई", Toast.LENGTH_LONG).show()
                                }
                            }
                            .setNegativeButton("CANCEL", null)
                            .show()
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }

        row.addView(
            more,
            LinearLayout.LayoutParams(
                (52 * resources.displayMetrics.density).toInt(),
                (52 * resources.displayMetrics.density).toInt()
            )
        )

        listLayout.addView(row)

        val divider = View(this)
        divider.setBackgroundColor(Color.LTGRAY)
        listLayout.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
        )
    }

    dialog.show()
}
override fun onDestroy() {
    timerHandler.removeCallbacks(timerRunnable)
    unregisterReceiver(recordingReceiver)
    super.onDestroy()
}
}

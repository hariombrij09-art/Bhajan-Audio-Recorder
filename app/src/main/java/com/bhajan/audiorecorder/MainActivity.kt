package com.bhajan.audiorecorder

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager

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

                Toast.makeText(
                    this,
                    "Recording Started",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(25, 25, 25, 25)
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "Bhajan Audio Recorder"
        title.textSize = 26f
        title.setTextColor(Color.BLACK)
        title.gravity = Gravity.CENTER

        layout.addView(title)

        val youtube = Button(this)
        youtube.text = "▶ Open YouTube"

        youtube.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com")
                )
            )
        }

        layout.addView(youtube)

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

        layout.addView(record)

        val pause = Button(this)
        pause.text = "⏸ PAUSE"

        pause.setOnClickListener {
            sendAction(RecordingService.ACTION_PAUSE)
        }

        layout.addView(pause)

        val resume = Button(this)
        resume.text = "▶ RESUME"

        resume.setOnClickListener {
            sendAction(RecordingService.ACTION_RESUME)
        }

        layout.addView(resume)

        val stop = Button(this)
        stop.text = "⏹ STOP"

        stop.setOnClickListener {
            sendAction(RecordingService.ACTION_STOP)
        }

        layout.addView(stop)

        val recordings = Button(this)
        recordings.text = "🎵 My Recordings"

        layout.addView(recordings)

        setContentView(layout)
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

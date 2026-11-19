package com.bhajan.audiorecorder

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(30, 30, 30, 30)
        layout.setBackgroundColor(Color.rgb(245, 245, 245))

        val title = TextView(this)
        title.text = "Bhajan Audio Recorder"
        title.textSize = 26f
        title.setTextColor(Color.BLACK)
        title.gravity = Gravity.CENTER

        layout.addView(title)

        val youtube = Button(this)
        youtube.text = "▶ Open YouTube"

        youtube.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com")
            )
            startActivity(intent)
        }

        layout.addView(youtube)

        val record = Button(this)
        record.text = "🔴 RECORD"

        record.setOnClickListener {
            Toast.makeText(
                this,
                "Recording system will be added next",
                Toast.LENGTH_SHORT
            ).show()
        }

        layout.addView(record)

        val pause = Button(this)
        pause.text = "⏸ PAUSE"
        layout.addView(pause)

        val stop = Button(this)
        stop.text = "⏹ STOP"
        layout.addView(stop)

        val recordings = Button(this)
        recordings.text = "🎵 My Recordings"
        layout.addView(recordings)

        setContentView(layout)
    }
}

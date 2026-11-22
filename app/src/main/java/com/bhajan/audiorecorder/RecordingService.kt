package com.bhajan.audiorecorder

import android.app.*
import android.content.*
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.content.pm.ServiceInfo
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "START_RECORDING"
        const val ACTION_PAUSE = "PAUSE_RECORDING"
        const val ACTION_RESUME = "RESUME_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        const val ACTION_RECORDING_READY = "RECORDING_READY"
        const val EXTRA_TEMP_FILE = "temp_file"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }

    private var recorder: AudioRecord? = null
    private var projection: MediaProjection? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var recording = false

    @Volatile
    private var paused = false

    private var outputFile: File? = null
    private var output: RandomAccessFile? = null
    private var totalBytes = 0L

    private val sampleRate = 44100
    private val channelCount = 2
    private val bytesPerSample = 2

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> startRecording(intent)

            ACTION_PAUSE -> paused = true

            ACTION_RESUME -> paused = false

            ACTION_STOP -> stopRecording()
        }

        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {

        try {
            val channelId = "bhajan_recorder"

            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    channelId,
                    "Audio Recording",
                    NotificationManager.IMPORTANCE_LOW
                )

                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }

            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Bhajan Audio Recorder")
                .setContentText("Audio recording is running")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()

            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

            val resultCode = intent.getIntExtra(
                EXTRA_RESULT_CODE, Activity.RESULT_CANCELED
            )

            val data: Intent? =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(
                        EXTRA_RESULT_DATA, Intent::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

            if (resultCode != Activity.RESULT_OK || data == null) {
                stopRecording()
                return
            }

            val manager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, data)

            val config = AudioPlaybackCaptureConfiguration.Builder(
                projection!!
            )
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)

            recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
                stopRecording()
                return
            }

            val folder = File(
                getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "Bhajan Audio Recorder"
            )

            folder.mkdirs()

            val date = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

            outputFile = File(folder, "Bhajan_$date.wav")

            output = RandomAccessFile(outputFile, "rw")
            output?.setLength(0)

            output?.write(ByteArray(44))

            totalBytes = 0
            recording = true
            paused = false

            recorder?.startRecording()

            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)

                while (recording) {

                   val count = recorder?.read(
                       buffer, 0, buffer.size
                   ) ?: -1

                   if (count > 0 && !paused) {
                       output?.write(buffer, 0, count)
                       totalBytes += count
                   }
               }
            }

            recordingThread?.start()

        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
        }
    }

    private fun stopRecording() {
    recording = false

    try {
        recordingThread?.join(1000)

        recorder?.stop()
        recorder?.release()
        recorder = null

        output?.seek(0)
        writeWavHeader(output, totalBytes)
        output?.close()
        output = null

        val tempFile = outputFile

        if (tempFile != null && tempFile.exists()) {

            val readyIntent = Intent(ACTION_RECORDING_READY)

            readyIntent.putExtra(
                EXTRA_TEMP_FILE,
                tempFile.absolutePath
            )

            sendBroadcast(readyIntent)
        }

        outputFile = null

        projection?.stop()
        projection = null

    } catch (e: Exception) {
        e.printStackTrace()
    }

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
    }
        private fun writeWavHeader(
        file: RandomAccessFile?,
        dataSize: Long
    ) {
        if (file == null) return

        val byteRate = sampleRate * channelCount * bytesPerSample

        file.writeBytes("RIFF")
        writeInt(file, (dataSize + 36).toInt())
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        writeInt(file, 16)
        writeShort(file, 1)
        writeShort(file, channelCount)
        writeInt(file, sampleRate)
        writeInt(file, byteRate)
        writeShort(file, channelCount * bytesPerSample)
        writeShort(file, 16)
        file.writeBytes("data")
        writeInt(file, dataSize.toInt())
    }

    private fun writeInt(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value shr 8) and 0xff)
        file.write((value shr 16) and 0xff)
        file.write((value shr 24) and 0xff)
    }

    private fun writeShort(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value shr 8) and 0xff)
    }

    override fun onDestroy() {
        if (recording || output != null) {
            stopRecording()
        }
        super.onDestroy()
    }
}

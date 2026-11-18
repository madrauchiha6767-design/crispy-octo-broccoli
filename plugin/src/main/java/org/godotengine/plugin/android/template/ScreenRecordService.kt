package org.godotengine.plugin.android.template

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenRecordService : Service() {

    companion object {
        @Volatile
        var isRecording: Boolean = false
            private set

        @Volatile
        var lastRecordingPath: String = ""
            private set

        const val EXTRA_RESULT_CODE = "rx_result_code"
        const val EXTRA_RESULT_DATA = "rx_result_data"
        const val STOP_ACTION = "org.godotengine.plugin.android.template.STOP_RECORDING"

        private const val CHANNEL_ID = "rx_recorder"
        private const val NOTIFICATION_ID = 1001
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputUri: android.net.Uri? = null
    private var outputDescriptor: ParcelFileDescriptor? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var recordingStarted = false
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_ACTION) {
            stopCapture()
            return START_NOT_STICKY
        }

        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResult.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultData == null || resultCode != ActivityResult.RESULT_OK) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RX Recorder")
            .setContentText("Screen recording is active")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startCapture(resultCode, resultData)
        } catch (error: Exception) {
            error.printStackTrace()
            stopCapture()
        }

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
            ?: error("Screen capture permission was not granted")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

        // MediaRecorder requires even dimensions. Use the actual display size rather than
        // forcing 1280x720, which can crop or distort other Android TV resolutions.
        val width = (metrics.widthPixels / 2) * 2
        val height = (metrics.heightPixels / 2) * 2
        require(width >= 2 && height >= 2) { "Invalid display size: ${metrics.widthPixels}x${metrics.heightPixels}" }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "RX_Recording_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RXRecorder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create output video")
        outputDescriptor = contentResolver.openFileDescriptor(outputUri!!, "w")
            ?: error("Could not open output video")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder!!.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5_000_000)
            setOutputFile(outputDescriptor!!.fileDescriptor)
            prepare()
        }

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture(stopProjection = false)
            }
        }
        projection!!.registerCallback(projectionCallback!!, Handler(Looper.getMainLooper()))

        virtualDisplay = projection!!.createVirtualDisplay(
            "RXRecorder",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder!!.surface,
            null,
            null
        ) ?: error("Could not create screen virtual display")

        recorder!!.start()
        recordingStarted = true
        isRecording = true
        lastRecordingPath = ""
    }

    private fun stopCapture(stopProjection: Boolean = true) {
        if (stopping) return
        stopping = true

        var savedSuccessfully = false
        if (recordingStarted) {
            try {
                recorder?.stop()
                savedSuccessfully = true
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }

        isRecording = false
        recordingStarted = false

        try {
            virtualDisplay?.release()
            virtualDisplay = null

            projectionCallback?.let { callback ->
                projection?.unregisterCallback(callback)
            }
            projectionCallback = null

            if (stopProjection) projection?.stop()

            recorder?.reset()
            recorder?.release()
            recorder = null

            outputDescriptor?.close()
            outputDescriptor = null

            outputUri?.let { uri ->
                if (savedSuccessfully) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    contentResolver.update(uri, values, null, null)
                    lastRecordingPath = uri.toString()
                } else {
                    contentResolver.delete(uri, null, null)
                }
            }
        } catch (error: Exception) {
            error.printStackTrace()
        } finally {
            projection = null
            outputUri = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RX Recorder",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (!stopping) stopCapture()
        super.onDestroy()
    }

    private object ActivityResult {
        const val RESULT_CANCELED = 0
        const val RESULT_OK = -1
    }
}

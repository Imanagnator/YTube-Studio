package com.example.service

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.TubeDeckApplication
import com.example.data.local.DownloadRecord
import com.example.data.local.DownloadStatus
import com.example.data.local.MediaCategory
import com.example.data.local.NotificationCategory
import com.example.data.model.RecordingState
import com.example.notifications.AppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecordService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        const val ACTION_START = "com.tamalmitra.ytubestudio.action.SCREEN_RECORD_START"
        const val ACTION_STOP = "com.tamalmitra.ytubestudio.action.SCREEN_RECORD_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_MIC_AUDIO = "extra_mic_audio"

        private val _recordingState = MutableStateFlow(RecordingState())
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isStopping = AtomicBoolean(false)
    private var timerJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var outputDisplayName: String = "YTubeStudio_Recording.mp4"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCaptureAndSave()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                val withMic = intent.getBooleanExtra(EXTRA_MIC_AUDIO, false)

                startAsForeground()
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startCapture(resultCode, resultData, width, height, withMic)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            21,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AppNotificationManager.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notif_ytmax)
            .setContentTitle("YTube Studio Screen Recorder")
            .setContentText("Recording video in progress...")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppNotificationManager.NOTIF_ID_SCREEN_RECORD,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(AppNotificationManager.NOTIF_ID_SCREEN_RECORD, notification)
        }
    }

    private fun startCapture(
        resultCode: Int,
        resultData: Intent,
        targetW: Int,
        targetH: Int,
        withMic: Boolean
    ) {
        val app = application as TubeDeckApplication
        runCatching {
            val projManager = getSystemService(MediaProjectionManager::class.java)
            val projection = projManager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection unavailable")
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCaptureAndSave()
                }
            }, null)

            val density = resources.displayMetrics.densityDpi.coerceAtLeast(160)

            // Keep dimensions even for H.264 encoder compatibility
            val width = (targetW / 2) * 2
            val height = (targetH / 2) * 2

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            outputDisplayName = "YTubeStudio_Rec_$stamp.mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, outputDisplayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/YTubeStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            ) ?: error("Failed to allocate MediaStore video file")
            outputUri = uri
            val pfd = contentResolver.openFileDescriptor(uri, "rw")
                ?: error("Failed to open output descriptor")
            outputPfd = pfd

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                if (withMic) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(pfd.fileDescriptor)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (withMic) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                }
                setVideoEncodingBitRate(6_000_000)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                prepare()
            }
            mediaRecorder = recorder

            virtualDisplay = projection.createVirtualDisplay(
                "TubeDeckRecorder",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()

            _recordingState.update {
                it.copy(isRecording = true, elapsedSeconds = 0L)
            }
            timerJob?.cancel()
            timerJob = scope.launch {
                while (true) {
                    delay(1000L)
                    _recordingState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
                }
            }
        }.onFailure { err ->
            Log.e(TAG, "Screen recording failed to start", err)
            outputUri?.let { orphan ->
                runCatching { contentResolver.delete(orphan, null, null) }
            }
            outputUri = null
            app.notificationManager.logAndNotify(
                title = "Recording failed",
                message = err.message ?: "Unable to start screen capture",
                category = NotificationCategory.RECORDING
            )
            cleanupRecorder()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopCaptureAndSave() {
        if (!isStopping.compareAndSet(false, true)) return

        timerJob?.cancel()
        timerJob = null
        val wasRecording = _recordingState.value.isRecording

        runCatching { mediaRecorder?.stop() }
        cleanupRecorder()

        val savedUri = outputUri
        outputUri = null

        _recordingState.update {
            it.copy(
                isRecording = false,
                elapsedSeconds = 0L,
                lastSavedPath = savedUri?.toString()
            )
        }

        if (savedUri != null && wasRecording) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(savedUri, values, null, null)
            }

            val app = application as TubeDeckApplication
            scope.launch {
                try {
                    val fileSize = runCatching {
                        contentResolver.openAssetFileDescriptor(savedUri, "r")?.use { it.length } ?: 0L
                    }.getOrDefault(0L)

                    app.database.downloadDao().insert(
                        DownloadRecord(
                            title = outputDisplayName.removeSuffix(".mp4"),
                            sourceUrl = "screen://recording",
                            formatLabel = "Screen Recording (MP4)",
                            extension = "mp4",
                            category = MediaCategory.RECORDING,
                            status = DownloadStatus.COMPLETED,
                            progressPercent = 100,
                            fileSizeBytes = fileSize,
                            contentUri = savedUri.toString()
                        )
                    )
                    app.notificationManager.logAndNotify(
                        title = "Recording saved",
                        message = "$outputDisplayName saved to Movies/YTubeStudio",
                        category = NotificationCategory.RECORDING,
                        actionTarget = "downloads"
                    )
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else {
            if (savedUri != null) {
                runCatching { contentResolver.delete(savedUri, null, null) }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupRecorder() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { outputPfd?.close() }
        outputPfd = null
    }

    override fun onDestroy() {
        timerJob?.cancel()
        cleanupRecorder()
        scope.cancel()
        super.onDestroy()
    }
}

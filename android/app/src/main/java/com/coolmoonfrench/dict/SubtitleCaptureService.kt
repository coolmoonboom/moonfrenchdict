package com.coolmoonfrench.dict

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.coolmoonfrench.dict.room.VideoTextDatabase
import com.coolmoonfrench.dict.room.VideoTextRecord
import kotlinx.coroutines.runBlocking
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.concurrent.thread

/**
 * 在线视频字幕：捕获**系统内部播放**的音频（MediaProjection + 音频回放捕获），
 * 用 Vosk 离线识别，把文字实时推送到字幕悬浮窗；停止时把整段转写保存为一条
 * SUIBTITLE 来源的历史记录。
 *
 * 说明：
 * - 内部音频捕获需要 Android 10（API 29）及以上；调用方需先取得 MediaProjection 授权。
 * - 本服务必须是 mediaProjection 类型的前台服务，否则系统会拒绝授权。
 */
class SubtitleCaptureService : Service() {

    companion object {
        private const val NOTIF_CHANNEL_ID = "subtitle_capture"
        private const val NOTIF_ID = 4101
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        /** 启动字幕捕获前台服务（需已获得 MediaProjection 授权结果）。 */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, SubtitleCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        /** 停止字幕捕获。 */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, SubtitleCaptureService::class.java))
            }
        }
    }

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    @Volatile
    private var running = false

    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val proj = try {
            mpm?.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            null
        }
        if (proj == null) {
            stopSelf()
            return
        }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        SubtitleWindowControl.show(this, "在线视频字幕")

        running = true
        worker = thread(name = "subtitle-capture", isDaemon = true) {
            val ar = try {
                buildPlaybackRecord(proj)
            } catch (e: Exception) {
                null
            }
            if (ar == null) {
                SubtitleWindowControl.updatePartial("无法捕获系统音频（请确认系统版本与环境）")
                running = false
                return@thread
            }
            record = ar

            val rec = try {
                val dir = VoskModelManager.currentModelDir(this@SubtitleCaptureService)
                    ?: runBlocking { VoskModelManager.ensureSmallModel(this@SubtitleCaptureService) }
                val m = Model(dir.absolutePath)
                model = m
                Recognizer(m, 16000f).also { recognizer = it }
            } catch (e: Exception) {
                SubtitleWindowControl.updatePartial("识别模型初始化失败：${e.message}")
                releaseAudio()
                running = false
                return@thread
            }

            try {
                ar.startRecording()
                val buf = ByteArray(8192)
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n < 0) break
                    if (n == 0) continue
                    val endpoint = rec.acceptWaveForm(buf, n)
                    if (endpoint) {
                        val text = voskField(rec.result, "text")
                        if (text.isNotBlank()) SubtitleWindowControl.appendLine(text)
                    } else {
                        SubtitleWindowControl.updatePartial(voskField(rec.partialResult, "partial"))
                    }
                }
            } catch (e: Throwable) {
                // 释放/停止过程中的异常无需上报
            } finally {
                releaseAudio()
            }
        }
    }

    /** 构建捕获系统回放音频的 AudioRecord（API 29+）。 */
    private fun buildPlaybackRecord(proj: MediaProjection): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val config = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = if (minBuf > 0) minBuf * 4 else 16000 * 4
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(16000)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val ar = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufBytes)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            return null
        }
        return ar
    }

    private fun releaseAudio() {
        val ar = record ?: return
        record = null
        runCatching {
            if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) ar.stop()
        }
        runCatching { ar.release() }
    }

    override fun onDestroy() {
        running = false
        runCatching { worker?.join(1500) }
        worker = null
        runCatching { recognizer?.close() }
        recognizer = null
        releaseAudio()
        runCatching { model?.close() }
        model = null
        runCatching { projection?.stop() }
        projection = null

        saveSession()
        SubtitleWindowControl.hide(this)
        super.onDestroy()
    }

    /** 把本轮字幕保存为一条历史记录（供「在线视频字幕」列表查看）。 */
    private fun saveSession() {
        val lines = FloatingWindowState.subtitleLines.toList()
        if (lines.isEmpty()) return
        val text = lines.joinToString("\n")
        val appContext = applicationContext
        thread(name = "subtitle-save", isDaemon = true) {
            runCatching {
                runBlocking {
                    VideoTextDatabase.get(appContext).videoTextDao().insert(
                        VideoTextRecord(
                            source = "SUBTITLE",
                            fileName = "在线视频字幕",
                            text = text,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        NOTIF_CHANNEL_ID,
                        "在线视频字幕",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("正在提取在线视频字幕")
            .setContentText("正在识别系统播放的声音…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}

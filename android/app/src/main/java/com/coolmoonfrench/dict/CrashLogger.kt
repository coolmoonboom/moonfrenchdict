package com.coolmoonfrench.dict

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 崩溃与运行日志记录器。
 * 开启 debug 开关后将日志写入 Documents/月球法语/logs 目录：
 *  - Android 10+ 通过 MediaStore 写入公共 Documents（免存储权限）
 *  - Android 9 及以下写入 App 专属外部 Documents 目录（免权限）
 * 并安装全局未捕获异常处理器，把闪退堆栈一并落盘，便于排查。
 */
object CrashLogger {

    @Volatile
    var enabled: Boolean = false

    private val installed = AtomicBoolean(false)
    private const val TAG = "MoonFrench"
    private const val SUB_DIR = "月球法语/logs"
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    fun init(context: Context) {
        if (installed.compareAndSet(false, true)) {
            val original = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    if (enabled) {
                        log(context, "未捕获异常", thread.name + "\n" + stackTraceOf(throwable))
                    }
                } catch (_: Exception) {
                }
                original?.uncaughtException(thread, throwable)
            }
        }
    }

    fun log(context: Context, tag: String, message: String) {
        if (!enabled) return
        try {
            val line = "${timeFmt.format(Date())} [$tag] $message\n"
            val fileName = "log_${fileFmt.format(Date())}.txt"
            if (Build.VERSION.SDK_INT >= 29) {
                writeViaMediaStore(context, fileName, line)
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs")
                dir.mkdirs()
                File(dir, fileName).appendText(line)
            }
            Log.w(TAG, "$tag: $message")
        } catch (_: Exception) {
        }
    }

    private fun writeViaMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$SUB_DIR")
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
        resolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun stackTraceOf(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
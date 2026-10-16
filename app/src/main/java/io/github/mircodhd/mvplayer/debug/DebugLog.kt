package io.github.mircodhd.mvplayer.debug

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * App-wide logger. Every line goes to logcat (tag "MvPlayer", filter with
 * `adb logcat -s MvPlayer`) and, if enabled, to a session file that can be shared without
 * adb: Documents/MV Player/logs/ when all-files access is granted, otherwise the app's
 * external files directory. File writes happen on a background thread.
 */
object DebugLog {
    enum class Level(val priority: Int, val letter: Char) {
        VERBOSE(Log.VERBOSE, 'V'),
        DEBUG(Log.DEBUG, 'D'),
        INFO(Log.INFO, 'I'),
        WARN(Log.WARN, 'W'),
        ERROR(Log.ERROR, 'E'),
    }

    private const val TAG = "MvPlayer"
    private const val KEEP_FILES = 15
    private const val FLUSH_INTERVAL_MS = 2000L

    private val writerThread = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "DebugLogWriter").apply { isDaemon = true }
    }
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    private var writer: BufferedWriter? = null
    private var appContext: Context? = null

    @Volatile var settings = DebugSettings(logToFile = false, verbose = false, statsIntervalSeconds = 0, remoteDebugging = false)
        private set

    @Volatile var currentFile: File? = null
        private set

    val verbose: Boolean get() = settings.verbose

    fun init(context: Context) {
        appContext = context.applicationContext
        settings = DebugSettings.load(context)
        if (settings.logToFile) {
            writerThread.execute { openFile(preferredDir(context)) }
            writerThread.scheduleWithFixedDelay({ flushNow() }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** Moves the session file to Documents once all-files access has been granted. */
    fun relocateIfPossible() {
        val context = appContext ?: return
        if (!settings.logToFile) return
        writerThread.execute {
            val target = preferredDir(context)
            val file = currentFile ?: return@execute
            if (file.parentFile == target) return@execute
            closeWriter()
            if (!target.mkdirs() && !target.isDirectory) return@execute openAppend(file)
            val moved = File(target, file.name)
            val ok = runCatching { file.copyTo(moved, overwrite = true); file.delete() }.isSuccess
            openAppend(if (ok) moved else file)
        }
    }

    fun v(category: String, message: String) = log(Level.VERBOSE, category, message)
    fun d(category: String, message: String) = log(Level.DEBUG, category, message)
    fun i(category: String, message: String) = log(Level.INFO, category, message)
    fun w(category: String, message: String, error: Throwable? = null) = log(Level.WARN, category, message, error)
    fun e(category: String, message: String, error: Throwable? = null) = log(Level.ERROR, category, message, error)

    fun log(level: Level, category: String, message: String, error: Throwable? = null) {
        if (level == Level.VERBOSE && !settings.verbose) return
        val text = if (error != null) "$message\n${Log.getStackTraceString(error)}" else message
        Log.println(level.priority, TAG, "[$category] $text")
        if (!settings.logToFile) return
        val line = "${timeFormat.format(Date())} ${level.letter} $category: $text\n"
        writerThread.execute {
            try {
                writer?.write(line)
            } catch (e: IOException) {
                Log.w(TAG, "Log file write failed", e)
                closeWriter()
            }
        }
    }

    /** Where new session logs go: Documents with all-files access, the app folder otherwise. */
    fun logDirectory(): File? = appContext?.let(::preferredDir)

    /** All session logs in both possible locations, oldest first. */
    fun logFiles(): List<File> {
        val context = appContext ?: return emptyList()
        return listOf(publicDir(), privateDir(context))
            .distinct()
            .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".log") }?.toList().orEmpty() }
            .sortedBy { it.name }
    }

    /** Writes pending lines; blocks briefly so it can be used before a crash or pause. */
    fun flush() {
        runCatching { writerThread.submit { flushNow() }.get(500, TimeUnit.MILLISECONDS) }
    }

    private fun flushNow() {
        runCatching { writer?.flush() }
    }

    private fun preferredDir(context: Context): File =
        if (Environment.isExternalStorageManager()) publicDir() else privateDir(context)

    private fun publicDir() =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MV Player/logs")

    private fun privateDir(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")

    private fun openFile(dir: File) {
        if (!dir.mkdirs() && !dir.isDirectory) {
            Log.w(TAG, "Cannot create log directory $dir")
            return
        }
        dir.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_FILES - 1)
            ?.forEach { it.delete() }
        openAppend(File(dir, "session_${fileNameFormat.format(Date())}.log"))
    }

    private fun openAppend(file: File) {
        try {
            writer = BufferedWriter(FileWriter(file, true))
            currentFile = file
            Log.i(TAG, "[Log] Writing session log to $file")
        } catch (e: IOException) {
            Log.w(TAG, "Cannot open log file $file", e)
        }
    }

    private fun closeWriter() {
        runCatching { writer?.close() }
        writer = null
    }
}

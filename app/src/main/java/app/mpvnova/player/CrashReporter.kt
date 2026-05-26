package app.mpvnova.player

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-only crash reporter. No network, no third-party SDK, no opt-in
 * dialog. Catches uncaught exceptions on any thread, writes a one-shot
 * text report to cacheDir/crashes/, then chains to the previous handler
 * so the process still dies normally (the OS will see a crash too).
 *
 * The user surfaces these crash files by tapping "Export support bundle"
 * in Settings → Support — the zip picks up every file under
 * cacheDir/crashes/ automatically.
 */
internal object CrashReporter {
    private const val TAG = "mpvNova-crash"
    private const val MAX_CRASH_FILES = 10
    private val timestampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            @Suppress("TooGenericExceptionCaught")
            try {
                writeCrashFile(appContext, thread, throwable)
            } catch (write: Throwable) {
                // Last-ditch logcat fallback so the crash still leaves a trace
                // somewhere even if the disk write itself failed. We
                // intentionally catch Throwable here — the alternative is
                // the secondary exception killing the JVM before the
                // chained-default handler runs, which would also lose the
                // *original* crash. Better to log + continue.
                Log.e(TAG, "Failed to write crash report", write)
            }
            // Always chain — the default handler is what kills the process and
            // surfaces the crash to the OS. Skipping it would hang the app.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.cacheDir, "crashes").apply { mkdirs() }
        pruneOldCrashes(dir)
        val stamp = timestampFormat.format(Date())
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(buildString {
            appendLine("mpvNova crash report")
            appendLine("Timestamp: $stamp")
            appendLine(
                "App version: ${BuildConfig.VERSION_NAME} " +
                    "(${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"
            )
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS?.joinToString().orEmpty()}")
            appendLine("Thread: ${thread.name} (id=${thread.id}, priority=${thread.priority})")
            appendLine()
            appendLine("--- Stack trace ---")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            appendLine(sw.toString().trimEnd())
            appendLine()
            appendLine("--- Recent mpv log (last ${MpvLogRingBuffer.snapshot().size} lines) ---")
            appendLine(MpvLogRingBuffer.snapshotText())
        })
    }

    /**
     * Keep the most recent [MAX_CRASH_FILES] crash files so the cache directory
     * doesn't grow forever for users who hit recurring crashes. Anything older
     * is dropped silently.
     */
    private fun pruneOldCrashes(dir: File) {
        val existing = dir.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") }
            ?: return
        if (existing.size < MAX_CRASH_FILES) return
        existing
            .sortedBy { it.lastModified() }
            .take(existing.size - (MAX_CRASH_FILES - 1))
            .forEach { it.delete() }
    }
}

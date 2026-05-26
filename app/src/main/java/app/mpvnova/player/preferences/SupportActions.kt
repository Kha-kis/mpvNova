package app.mpvnova.player.preferences

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.MPVView
import app.mpvnova.player.MpvLogRingBuffer
import app.mpvnova.player.NativeLibraryVersion
import app.mpvnova.player.R
import app.mpvnova.player.toShieldDecoderFallback
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SupportActions {
    private val PLAYER_UI_KEYS = arrayOf(
        "display_media_title",
        "bottom_controls",
        "player_controls_timeout",
        "keep_controls_visible_paused",
        "autopause_controls_overlay",
        "autopause_shield_hi10p",
        "remote_next_chapter_button",
        "remember_player_screen_brightness",
        "player_screen_brightness_percent",
        "player_screen_brightness_initialized",
        "remember_video_contrast",
        "video_contrast",
        "remember_video_gamma",
        "video_gamma",
        "remember_video_saturation",
        "video_saturation",
        "no_ui_pause",
        "playlist_exit_warning",
        "use_time_remaining",
    )

    fun copyDebugInfo(activity: Activity) {
        val text = buildDebugInfo(activity)
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.support_debug_info_title), text))
        Toast.makeText(activity, R.string.support_debug_info_copied, Toast.LENGTH_SHORT).show()
    }

    fun exportConfigBundle(activity: Activity) {
        val supportDir = File(activity.cacheDir, "support")
        if (!supportDir.exists())
            supportDir.mkdirs()
        supportDir.listFiles()?.forEach { it.delete() }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val bundle = File(supportDir, "mpvNova-support-$stamp.zip")
        ZipOutputStream(bundle.outputStream()).use { zip ->
            zip.textEntry("debug-info.txt", buildDebugInfo(activity))
            zip.textEntry("settings-summary.txt", buildSettingsSummary(activity))
            zip.configEntry(activity, "mpv.conf")
            zip.configEntry(activity, "input.conf")
            zip.textEntry("logs.txt", buildMpvLogDump())
            zip.crashEntries(activity)
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            bundle
        )
        val streamClip = ClipData.newUri(activity.contentResolver, bundle.name, uri)
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.clipData = streamClip
        val chooser = Intent.createChooser(shareIntent, activity.getString(R.string.support_export_chooser))
        chooser.clipData = streamClip
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.support_export_no_target, Toast.LENGTH_SHORT).show()
        }
    }

    fun resetPlayerUiSettings(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().apply {
            PLAYER_UI_KEYS.forEach(::remove)
        }.apply()
        Toast.makeText(activity, R.string.support_reset_player_ui_done, Toast.LENGTH_SHORT).show()
    }

    private fun buildDebugInfo(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val packageManager = context.packageManager
        val uiModeType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val isFireTv = packageManager.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
        val isTvMode = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val hasFakeTouch = packageManager.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH)
        val autoDecoder = prefs.getBoolean("decoder_auto_fallback", true)
        val shieldDecoder = prefs.getBoolean("shield_decoder_mode", true)
        val shieldDecoderFallback = prefs.getString(
            "shield_decoder_fallback",
            MPVView.SHIELD_DECODER_FALLBACK_COPY,
        ).toShieldDecoderFallback()
        val preferredDecoder = prefs.getString("preferred_decoder_mode", null)
            ?.takeIf { it.isNotBlank() }
            ?: "default"
        val decoder = if (autoDecoder)
            "Automatic fallback enabled; preferred=$preferredDecoder"
        else
            preferredDecoder

        return buildString {
            appendLine("mpvNova debug info")
            appendLine(
                "App version: ${BuildConfig.VERSION_NAME} " +
                    "(${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"
            )
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS?.joinToString().orEmpty()}")
            appendLine("Fire TV: ${if (isFireTv) "yes" else "no"}")
            appendLine("TV mode: ${if (isTvMode) "yes" else "no"}")
            appendLine(
                "Input features: touchscreen=${if (hasTouchscreen) "yes" else "no"}, " +
                    "faketouch=${if (hasFakeTouch) "yes" else "no"}"
            )
            appendLine("Decoder setting: $decoder")
            appendLine("Shield decoder mode: ${if (shieldDecoder) "enabled" else "disabled"}")
            appendLine("Shield Hi10P fallback: $shieldDecoderFallback")
            appendLine("mpv: ${nativeVersion(context, "libmpv.so", "mpv v")}")
            appendLine("FFmpeg: ${nativeVersion(context, "libavcodec.so", "FFmpeg version ")}")
        }
    }

    private fun buildSettingsSummary(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return buildString {
            appendLine("Selected mpvNova settings")
            prefs.all.toSortedMap().forEach { (key, value) ->
                if (key == "release_history")
                    return@forEach
                appendLine("$key=$value")
            }
        }
    }

    private fun ZipOutputStream.configEntry(context: Context, filename: String) {
        val file = File(context.filesDir, filename)
        val content = if (file.isFile)
            file.readText()
        else
            "$filename is not present.\n"
        textEntry(filename, content)
    }

    private fun ZipOutputStream.textEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /**
     * Emit every crash file the [CrashReporter] has written into the bundle
     * under a `crashes/` subdirectory. Silently no-op when there have been
     * no crashes — which is the common case.
     */
    private fun ZipOutputStream.crashEntries(context: Context) {
        val dir = File(context.cacheDir, "crashes")
        val files = dir.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") }
            ?: return
        if (files.isEmpty()) return
        for (file in files.sortedBy { it.lastModified() }) {
            textEntry("crashes/${file.name}", file.readText())
        }
    }

    private fun buildMpvLogDump(): String {
        val lines = MpvLogRingBuffer.snapshot()
        if (lines.isEmpty()) {
            return "No mpv log lines captured yet in this process.\n"
        }
        return buildString {
            appendLine("Last ${lines.size} mpv log lines captured by mpvNova in this session.")
            appendLine()
            for (line in lines) {
                appendLine(line)
            }
        }
    }

    private fun nativeVersion(context: Context, libraryName: String, marker: String): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val file = nativeDir?.let { File(it, libraryName) }
        return if (file?.isFile != true) {
            "unknown"
        } else {
            runCatching {
                NativeLibraryVersion.find(file, marker) ?: "unknown"
            }.getOrDefault("unknown")
        }
    }

    private const val AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv"
}

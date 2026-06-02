package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

/**
 * Font discovery and import for the subtitle style customizer.
 *
 * Fonts live in `filesDir/fonts`, which `fonts.conf` registers with fontconfig
 * (see Utils.writeFontsConf). Bundled faces are copied there on startup
 * (Utils.copyAssets); users add their own via [importSubtitleFont]. The Font
 * picker lists the generic families plus every font found here, keyed by the
 * real family name read from each file.
 */

private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")

internal fun MPVActivity.subtitleFontsDir(): File = File(filesDir, "fonts").apply { mkdirs() }

/** Generic families + every bundled/imported font on disk, deduped by family name. */
internal fun MPVActivity.subtitleFontChoices(): List<SubtitleFontChoice> {
    val genericFamilies = SUBTITLE_GENERIC_FONTS.mapTo(mutableSetOf()) { it.family }
    val discovered = subtitleFontsDir()
        .listFiles { file -> file.isFile && file.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS }
        ?.mapNotNull { SubtitleFontTable.familyName(it) }
        ?.filter { it !in genericFamilies }
        ?.distinct()
        ?.sortedBy { it.lowercase(Locale.ROOT) }
        .orEmpty()
        .map { SubtitleFontChoice(it, it) }
    return SUBTITLE_GENERIC_FONTS + discovered
}

internal fun MPVActivity.subtitleFontLabel(family: String): String {
    return subtitleFontChoices().firstOrNull { it.family == family }?.label
        ?: family.ifEmpty { SUBTITLE_GENERIC_FONTS.first().label }
}

/**
 * Copy a picker-chosen font into the fonts dir and return its family name (or
 * null if it wasn't a usable font). libass picks it up on the next sub reload.
 */
internal fun MPVActivity.importSubtitleFont(result: Int, data: Intent?): String? {
    val path = data?.getStringExtra("path")?.takeIf { result == RESULT_OK } ?: return null
    return copyFontInto(subtitleFontsDir(), path)?.let { dest ->
        mpvCommand(arrayOf("sub-reload"))
        SubtitleFontTable.familyName(dest)
    }
}

/** Font files in the fonts dir that the user imported (i.e. not bundled assets). */
private fun MPVActivity.userFontFiles(): List<File> {
    val bundled = assets.list("fonts")?.toSet().orEmpty()
    return subtitleFontsDir()
        .listFiles { file ->
            file.isFile &&
                file.name !in bundled &&
                file.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS
        }
        ?.toList()
        .orEmpty()
}

/** Family names of imported (removable) fonts, deduped and sorted. */
internal fun MPVActivity.removableFontFamilies(): List<String> =
    userFontFiles()
        .mapNotNull { SubtitleFontTable.familyName(it) }
        .distinct()
        .sortedBy { it.lowercase(Locale.ROOT) }

/** Delete every imported file for [family]. Bundled fonts are never touched. */
internal fun MPVActivity.removeSubtitleFontFamily(family: String) {
    userFontFiles()
        .filter { SubtitleFontTable.familyName(it) == family }
        .forEach { it.delete() }
    if (subStyleFontFamily == family) {
        subStyleFontFamily = SUBTITLE_FONT_DEFAULT_FAMILY
        if (customSubStyleEnabled)
            applyCustomSubtitleStyle()
        writeSubtitleStyleSettings()
    }
    mpvCommand(arrayOf("sub-reload"))
}

private fun MPVActivity.copyFontInto(dir: File, path: String): File? = runCatching {
    if (path.startsWith("content://")) {
        val uri = Uri.parse(path)
        val name = fontDisplayName(uri) ?: return@runCatching null
        if (!isFontFileName(name)) return@runCatching null
        val dest = File(dir, sanitizeFontFileName(name))
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return@runCatching null
        dest
    } else {
        val src = File(path)
        if (!isFontFileName(src.name) || !src.canRead()) return@runCatching null
        val dest = File(dir, sanitizeFontFileName(src.name))
        src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        dest
    }
}.getOrNull()

private fun MPVActivity.fontDisplayName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && cursor.columnCount > 0) {
            cursor.getString(0)?.let { return it }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

private fun isFontFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.ROOT) in FONT_EXTENSIONS

private fun sanitizeFontFileName(name: String): String =
    name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
